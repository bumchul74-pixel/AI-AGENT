import { useEffect, useMemo, useRef, useState } from 'react';
import { AtSign, LoaderCircle, Wrench } from 'lucide-react';
import { fetchMcpTools } from '../../api/chatApi.js';

export function ChatInput({
  value,
  textareaRef,
  placeholder,
  disabled = false,
  onChange,
  onSubmit,
}) {
  const [mention, setMention] = useState(null);
  const [tools, setTools] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const optionRefs = useRef([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const requestRef = useRef(null);

  const filteredTools = useMemo(() => {
    if (!Array.isArray(tools) || !mention) return [];
    const query = mention.query.toLowerCase();
    return tools.filter((tool) => (
      tool.name.toLowerCase().includes(query)
      || tool.description.toLowerCase().includes(query)
    )).slice(0, 7);
  }, [mention, tools]);

  const isOpen = Boolean(mention) && !loadFailed;

  useEffect(() => () => requestRef.current?.abort(), []);

  useEffect(() => {
    setActiveIndex(0);
  }, [mention?.query]);

  useEffect(() => {
    if (!isOpen) return;
    optionRefs.current[activeIndex]?.scrollIntoView({ block: 'nearest' });
  }, [activeIndex, isOpen]);


  function updateMention(text, cursor) {
    const nextMention = findToolMention(text, cursor);
    setMention(nextMention);
    if (!nextMention || mention || isLoading) return;
    if (loadFailed) setLoadFailed(false);
    loadTools();
  }

  async function loadTools() {
    const controller = new AbortController();
    requestRef.current = controller;
    setIsLoading(true);
    try {
      const items = await fetchMcpTools(controller.signal);
      setTools(items.map(normalizeTool).filter((tool) => tool.name));
    } catch (error) {
      if (error?.name !== 'ApiRequestCancelledError') {
        setLoadFailed(true);
        setMention(null);
      }
    } finally {
      if (requestRef.current === controller) {
        requestRef.current = null;
        setIsLoading(false);
      }
    }
  }

  function handleChange(event) {
    const nextValue = event.target.value;
    onChange(nextValue, event.target);
    updateMention(nextValue, event.target.selectionStart);
  }

  function handleCursorChange(event) {
    updateMention(event.currentTarget.value, event.currentTarget.selectionStart);
  }

  function handleKeyDown(event) {
    if (isOpen) {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        if (filteredTools.length > 0) {
          const direction = event.key === 'ArrowDown' ? 1 : -1;
          setActiveIndex((current) => (
            (current + direction + filteredTools.length) % filteredTools.length
          ));
        }
        return;
      }
      if ((event.key === 'Enter' || event.key === 'Tab') && filteredTools.length > 0) {
        event.preventDefault();
        selectTool(filteredTools[activeIndex] ?? filteredTools[0]);
        return;
      }
      if (event.key === 'Escape') {
        event.preventDefault();
        setMention(null);
        return;
      }
    }

    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      onSubmit(event);
    }
  }

  function selectTool(tool) {
    if (!mention || !tool) return;
    const inserted = `@${tool.name} `;
    const nextValue = `${value.slice(0, mention.start)}${inserted}${value.slice(mention.end)}`;
    const nextCursor = mention.start + inserted.length;
    onChange(nextValue, textareaRef.current);
    setMention(null);
    requestAnimationFrame(() => {
      const textarea = textareaRef.current;
      if (!textarea) return;
      textarea.focus();
      textarea.setSelectionRange(nextCursor, nextCursor);
    });
  }

  return (
    <div className="chat-input-wrap">
      {isOpen && (
        <div
          className="mcp-tool-picker"
          id="mcp-tool-list"
          role="listbox"
          aria-label="MCP tool 목록"
        >
          <div className="mcp-tool-picker-heading">
            <span><AtSign size={14} /> DB Agent tools</span>
            {!isLoading && <small>{filteredTools.length}개</small>}
          </div>
          {isLoading && (
            <div className="mcp-tool-picker-state" role="status">
              <LoaderCircle className="spin" size={15} /> tool 목록을 불러오는 중...
            </div>
          )}
          {!isLoading && tools?.length === 0 && (
            <div className="mcp-tool-picker-state">사용 가능한 MCP tool이 없습니다.</div>
          )}
          {!isLoading && tools?.length > 0 && filteredTools.length === 0 && (
            <div className="mcp-tool-picker-state">일치하는 MCP tool이 없습니다.</div>
          )}
          {!isLoading && filteredTools.map((tool, index) => (
            <button
              key={tool.name}
              ref={(node) => {
                optionRefs.current[index] = node;
              }}
              id={`mcp-tool-option-${index}`}
              className={index === activeIndex ? 'mcp-tool-option active' : 'mcp-tool-option'}
              type="button"
              role="option"
              aria-selected={index === activeIndex}
              onMouseEnter={() => setActiveIndex(index)}
              onMouseDown={(event) => event.preventDefault()}
              onClick={() => selectTool(tool)}
            >
              <span className="mcp-tool-icon"><Wrench size={14} /></span>
              <span className="mcp-tool-copy">
                <strong>@{tool.name}</strong>
                <small>{tool.description || '설명이 제공되지 않은 MCP tool입니다.'}</small>
                {tool.required.length > 0 && (
                  <em>필수 인자: {tool.required.join(', ')}</em>
                )}
              </span>
            </button>
          ))}
        </div>
      )}
      <textarea
        ref={textareaRef}
        rows={1}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        aria-label="AI 대화 메시지"
        role="combobox"
        aria-haspopup="listbox"
        aria-autocomplete="list"
        aria-expanded={isOpen}
        aria-controls={isOpen ? 'mcp-tool-list' : undefined}
        aria-activedescendant={isOpen && filteredTools.length > 0
          ? `mcp-tool-option-${activeIndex}`
          : undefined}
        onChange={handleChange}
        onClick={handleCursorChange}
        onKeyUp={(event) => {
          if (!['ArrowDown', 'ArrowUp', 'Enter', 'Tab', 'Escape'].includes(event.key)) {
            handleCursorChange(event);
          }
        }}
        onKeyDown={handleKeyDown}
      />
    </div>
  );
}

export function findToolMention(value, cursor) {
  if (typeof value !== 'string' || !Number.isInteger(cursor)) return null;
  const beforeCursor = value.slice(0, cursor);
  const match = beforeCursor.match(/(^|\s)@([\w-]*)$/u);
  if (!match) return null;
  const start = cursor - match[2].length - 1;
  return { start, end: cursor, query: match[2] };
}

function normalizeTool(tool) {
  const inputSchema = tool?.inputSchema ?? tool?.input_schema ?? {};
  return {
    name: typeof tool?.name === 'string' ? tool.name : '',
    description: typeof tool?.description === 'string' ? tool.description : '',
    required: Array.isArray(inputSchema.required) ? inputSchema.required : [],
  };
}
