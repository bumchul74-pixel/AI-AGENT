import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Check,
  Folder,
  FolderPlus,
  MessageSquare,
  MoreHorizontal,
  Pencil,
  Plus,
  Trash2,
  X,
} from 'lucide-react';

export function ChatConversationList({
  conversations,
  projects,
  activeConversationId,
  disabled,
  onNew,
  onSelect,
  onDelete,
  onCreateProject,
  onRenameProject,
  onMoveConversation,
  projectError,
}) {
  const [isCreating, setIsCreating] = useState(false);
  const [newProjectName, setNewProjectName] = useState('');
  const [editingProjectId, setEditingProjectId] = useState(null);
  const [editingProjectName, setEditingProjectName] = useState('');
  const [draggedConversationId, setDraggedConversationId] = useState(null);
  const [dropTargetProjectId, setDropTargetProjectId] = useState(undefined);

  const projectGroups = projects.map((project) => ({
    ...project,
    conversations: conversations.filter(
      (conversation) => conversation.projectId === project.id,
    ),
  }));
  const ungrouped = conversations.filter(
    (conversation) => conversation.projectId == null,
  );

  async function handleDelete(event, conversation) {
    event.stopPropagation();
    if (!window.confirm(`'${conversation.title}' 대화를 삭제할까요?`)) return;
    await onDelete(conversation.id);
  }

  async function handleCreateProject(event) {
    event.preventDefault();
    const name = newProjectName.trim();
    if (!name) return;
    try {
      await onCreateProject(name);
      setNewProjectName('');
      setIsCreating(false);
    } catch {
      // The hook exposes the server message in the panel.
    }
  }

  async function handleRenameProject(event, projectId) {
    event.preventDefault();
    const name = editingProjectName.trim();
    if (!name) return;
    try {
      await onRenameProject(projectId, name);
      setEditingProjectId(null);
      setEditingProjectName('');
    } catch {
      // The hook exposes the server message in the panel.
    }
  }

  function beginRename(project) {
    setEditingProjectId(project.id);
    setEditingProjectName(project.name);
  }

  function handleDragStart(event, conversation) {
    if (disabled || event.target.closest('.conversation-menu-trigger')) {
      event.preventDefault();
      return;
    }
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('application/x-chat-conversation-id', String(conversation.id));
    event.dataTransfer.setData('text/plain', String(conversation.id));
    setDraggedConversationId(conversation.id);
  }

  function handleDragEnd() {
    setDraggedConversationId(null);
    setDropTargetProjectId(undefined);
  }

  function handleDragOver(event, projectId) {
    if (disabled || draggedConversationId == null) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = 'move';
    setDropTargetProjectId(projectId);
  }

  function handleDragLeave(event) {
    if (event.currentTarget.contains(event.relatedTarget)) return;
    setDropTargetProjectId(undefined);
  }

  async function handleDrop(event, projectId) {
    event.preventDefault();
    const transferredId = event.dataTransfer.getData('application/x-chat-conversation-id');
    const conversation = conversations.find(
      (item) => String(item.id) === String(transferredId || draggedConversationId),
    );
    setDraggedConversationId(null);
    setDropTargetProjectId(undefined);
    if (!conversation || conversation.projectId === projectId) return;
    try {
      await onMoveConversation(conversation.id, projectId);
    } catch {
      // The hook exposes the server message in the panel.
    }
  }

  return (
    <aside className='card chat-context-panel'>
      <button
        className='new-conversation-button'
        type='button'
        disabled={disabled}
        onClick={onNew}
      >
        <Plus size={17} />
        <span>새 대화</span>
      </button>

      <div className='project-toolbar'>
        <strong>프로젝트</strong>
        <button
          type='button'
          title='프로젝트 만들기'
          disabled={disabled}
          onClick={() => setIsCreating((current) => !current)}
        >
          <FolderPlus size={16} />
        </button>
      </div>

      {isCreating && (
        <form className='project-name-form' onSubmit={handleCreateProject}>
          <input
            autoFocus
            maxLength={80}
            value={newProjectName}
            placeholder='프로젝트 이름'
            disabled={disabled}
            onChange={(event) => setNewProjectName(event.target.value)}
          />
          <button type='submit' title='만들기' disabled={disabled || !newProjectName.trim()}>
            <Check size={15} />
          </button>
          <button type='button' title='취소' onClick={() => setIsCreating(false)}>
            <X size={15} />
          </button>
        </form>
      )}

      {projectError && <p className='project-error'>{projectError}</p>}

      <div className='conversation-groups'>
        {projectGroups.map((project) => (
          <section
            className={dropTargetProjectId === project.id
              ? 'conversation-group project-group drop-target'
              : 'conversation-group project-group'}
            key={project.id}
            onDragOver={(event) => handleDragOver(event, project.id)}
            onDragLeave={handleDragLeave}
            onDrop={(event) => handleDrop(event, project.id)}
          >
            <div className='project-group-heading'>
              {editingProjectId === project.id ? (
                <form onSubmit={(event) => handleRenameProject(event, project.id)}>
                  <input
                    autoFocus
                    maxLength={80}
                    value={editingProjectName}
                    disabled={disabled}
                    onChange={(event) => setEditingProjectName(event.target.value)}
                  />
                  <button type='submit' title='저장' disabled={disabled || !editingProjectName.trim()}>
                    <Check size={14} />
                  </button>
                  <button type='button' title='취소' onClick={() => setEditingProjectId(null)}>
                    <X size={14} />
                  </button>
                </form>
              ) : (
                <>
                  <span>
                    <Folder size={14} />
                    <strong>{project.name}</strong>
                    <small>{project.conversations.length}</small>
                  </span>
                  <button
                    type='button'
                    title='프로젝트 이름 변경'
                    disabled={disabled}
                    onClick={() => beginRename(project)}
                  >
                    <Pencil size={13} />
                  </button>
                </>
              )}
            </div>
            <ConversationItems
              conversations={project.conversations}
              activeConversationId={activeConversationId}
              disabled={disabled}
              draggedConversationId={draggedConversationId}
              onSelect={onSelect}
              onDelete={handleDelete}
              onDragStart={handleDragStart}
              onDragEnd={handleDragEnd}
              emptyMessage='대화를 이 프로젝트로 이동해 보세요.'
            />
          </section>
        ))}

        <section
          className={dropTargetProjectId === null
            ? 'conversation-group drop-target'
            : 'conversation-group'}
          onDragOver={(event) => handleDragOver(event, null)}
          onDragLeave={handleDragLeave}
          onDrop={(event) => handleDrop(event, null)}
        >
          <h2>프로젝트 없음</h2>
          <ConversationItems
            conversations={ungrouped}
            activeConversationId={activeConversationId}
            disabled={disabled}
            draggedConversationId={draggedConversationId}
            onSelect={onSelect}
            onDelete={handleDelete}
            onDragStart={handleDragStart}
            onDragEnd={handleDragEnd}
            emptyMessage={projects.length === 0 ? '저장된 대화가 없습니다.' : '분류되지 않은 대화가 없습니다.'}
          />
        </section>
      </div>
    </aside>
  );
}

function ConversationItems({
  conversations,
  activeConversationId,
  disabled,
  draggedConversationId,
  onSelect,
  onDelete,
  onDragStart,
  onDragEnd,
  emptyMessage,
}) {
  const [openMenu, setOpenMenu] = useState(null);

  useEffect(() => {
    if (!openMenu) return undefined;

    function closeOnOutsidePointer(event) {
      if (
        event.target.closest('.conversation-context-menu')
        || event.target.closest('.conversation-menu-trigger')
      ) return;
      setOpenMenu(null);
    }

    function closeOnEscape(event) {
      if (event.key === 'Escape') setOpenMenu(null);
    }

    function closeOnViewportChange() {
      setOpenMenu(null);
    }

    document.addEventListener('pointerdown', closeOnOutsidePointer);
    document.addEventListener('keydown', closeOnEscape);
    window.addEventListener('resize', closeOnViewportChange);
    window.addEventListener('scroll', closeOnViewportChange, true);
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsidePointer);
      document.removeEventListener('keydown', closeOnEscape);
      window.removeEventListener('resize', closeOnViewportChange);
      window.removeEventListener('scroll', closeOnViewportChange, true);
    };
  }, [openMenu]);

  if (conversations.length === 0) {
    return <p className='conversation-empty'>{emptyMessage}</p>;
  }

  function toggleMenu(event, conversation) {
    event.stopPropagation();
    if (openMenu?.conversationId === conversation.id) {
      setOpenMenu(null);
      return;
    }

    const trigger = event.currentTarget.getBoundingClientRect();
    const menuWidth = 220;
    const estimatedHeight = 52;
    const spaceBelow = window.innerHeight - trigger.bottom;
    setOpenMenu({
      conversationId: conversation.id,
      left: Math.max(8, trigger.right - menuWidth),
      top: spaceBelow >= estimatedHeight
        ? trigger.bottom + 4
        : Math.max(8, trigger.top - estimatedHeight - 4),
    });
  }

  function handleContextDelete(event, conversation) {
    setOpenMenu(null);
    onDelete(event, conversation);
  }

  return (
    <div className='conversation-list'>
      {conversations.map((conversation) => (
        <div
          className={[
            'conversation-item',
            conversation.id === activeConversationId ? 'selected' : '',
            conversation.id === draggedConversationId ? 'dragging' : '',
          ].filter(Boolean).join(' ')}
          draggable={!disabled}
          key={conversation.id}
          onDragStart={(event) => onDragStart(event, conversation)}
          onDragEnd={onDragEnd}
        >
          <button
            className='conversation-select'
            type='button'
            disabled={disabled}
            onClick={() => onSelect(conversation.id)}
          >
            <MessageSquare size={15} />
            <span>
              <strong>{conversation.title}</strong>
              <small>{conversation.messageCount}개 메시지</small>
            </span>
          </button>
          <div className='conversation-actions'>
            <button
              className='conversation-menu-trigger'
              type='button'
              title='대화 메뉴'
              aria-label={`${conversation.title} 대화 메뉴`}
              aria-haspopup='menu'
              aria-expanded={openMenu?.conversationId === conversation.id}
              disabled={disabled}
              onClick={(event) => toggleMenu(event, conversation)}
            >
              <MoreHorizontal size={17} />
            </button>
          </div>

          {openMenu?.conversationId === conversation.id && createPortal(
            <div
              className='conversation-context-menu'
              role='menu'
              aria-label={`${conversation.title} 대화 작업`}
              style={{ left: openMenu.left, top: openMenu.top }}
            >
              <button
                className='conversation-context-delete'
                type='button'
                role='menuitem'
                onClick={(event) => handleContextDelete(event, conversation)}
              >
                <Trash2 size={14} />
                <span>대화 삭제</span>
              </button>
            </div>,
            document.body,
          )}
        </div>
      ))}
    </div>
  );
}
