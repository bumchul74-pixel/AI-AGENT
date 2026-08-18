import { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, ChevronUp } from 'lucide-react';

function escapeRegExp(value) {
  return value.replace(/[.*+?^$(){}|[\]\\]/g, '\\$&');
}

function parseSearchTerms(query) {
  const terms = new Map();
  (query.match(/"([^"]+)"|\S+/g) ?? []).forEach((token) => {
    const term = token.startsWith('"') && token.endsWith('"')
      ? token.slice(1, -1).trim()
      : token.trim();
    if (term) terms.set(term.toLocaleLowerCase(), term);
  });
  return [...terms.values()].sort((left, right) => right.length - left.length);
}

function highlightSource(source, query) {
  const terms = parseSearchTerms(query);
  let matchIndex = 0;
  if (terms.length === 0) {
    return { lines: source.split(/\r?\n/).map((text) => ({ text, segments: [] })), matchCount: 0 };
  }
  const matcher = new RegExp(terms.map(escapeRegExp).join('|'), 'giu');
  const lines = source.split(/\r?\n/).map((text) => {
    const segments = [];
    let cursor = 0;
    for (const match of text.matchAll(matcher)) {
      if (match.index > cursor) segments.push({ text: text.slice(cursor, match.index), matchIndex: null });
      segments.push({ text: match[0], matchIndex });
      matchIndex += 1;
      cursor = match.index + match[0].length;
    }
    if (cursor < text.length) segments.push({ text: text.slice(cursor), matchIndex: null });
    return { text, segments };
  });
  return { lines, matchCount: matchIndex };
}

export function HighlightedSourceViewer({ source, query }) {
  const [activeMatchIndex, setActiveMatchIndex] = useState(0);
  const matchRefs = useRef([]);
  const { lines, matchCount } = useMemo(() => highlightSource(source, query), [source, query]);

  useEffect(() => setActiveMatchIndex(0), [source, query]);
  useEffect(() => {
    if (matchCount > 0) {
      matchRefs.current[activeMatchIndex]?.scrollIntoView({
        behavior: 'smooth',
        block: 'center',
        inline: 'nearest',
      });
    }
  }, [activeMatchIndex, matchCount, query, source]);

  function moveMatch(offset) {
    setActiveMatchIndex((current) => (current + offset + matchCount) % matchCount);
  }

  return (
    <div className="rag-source-viewer">
      <div className="rag-source-toolbar" aria-live="polite">
        <span className={matchCount > 0 ? 'rag-match-count has-matches' : 'rag-match-count'}>
          {matchCount > 0 ? '일치 ' + matchCount + '건' : '일치 없음'}
        </span>
        <span className="rag-match-position">
          {matchCount > 0 ? activeMatchIndex + 1 + ' / ' + matchCount : '0 / 0'}
        </span>
        <div className="rag-match-navigation">
          <button type="button" aria-label="이전 일치 항목" disabled={matchCount === 0} onClick={() => moveMatch(-1)}>
            <ChevronUp size={16} />
          </button>
          <button type="button" aria-label="다음 일치 항목" disabled={matchCount === 0} onClick={() => moveMatch(1)}>
            <ChevronDown size={16} />
          </button>
        </div>
      </div>
      <pre className="rag-detail-content" aria-label="검색 결과 원문">
        {lines.map((line, lineIndex) => {
          const hasMatch = line.segments.some((segment) => segment.matchIndex !== null);
          const hasActiveMatch = line.segments.some((segment) => segment.matchIndex === activeMatchIndex);
          const className = 'rag-source-line' + (hasMatch ? ' has-match' : '') + (hasActiveMatch ? ' active-match' : '');
          return (
            <span className={className} key={lineIndex}>
              <span className="rag-source-line-number" aria-hidden="true">{lineIndex + 1}</span>
              <span className="rag-source-line-text">
                {line.segments.length === 0 ? line.text || '\u200b' : line.segments.map((segment, segmentIndex) => (
                  segment.matchIndex === null ? (
                    <span key={segmentIndex}>{segment.text}</span>
                  ) : (
                    <mark
                      className={segment.matchIndex === activeMatchIndex ? 'active' : undefined}
                      key={segmentIndex}
                      ref={(node) => { matchRefs.current[segment.matchIndex] = node; }}
                    >
                      {segment.text}
                    </mark>
                  )
                ))}
              </span>
            </span>
          );
        })}
      </pre>
    </div>
  );
}
