import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import {
  Check,
  Folder,
  FolderInput,
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
          <section className='conversation-group project-group' key={project.id}>
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
              projects={projects}
              activeConversationId={activeConversationId}
              disabled={disabled}
              onSelect={onSelect}
              onDelete={handleDelete}
              onMove={onMoveConversation}
              emptyMessage='대화를 이 프로젝트로 이동해 보세요.'
            />
          </section>
        ))}

        <section className='conversation-group'>
          <h2>프로젝트 없음</h2>
          <ConversationItems
            conversations={ungrouped}
            projects={projects}
            activeConversationId={activeConversationId}
            disabled={disabled}
            onSelect={onSelect}
            onDelete={handleDelete}
            onMove={onMoveConversation}
            emptyMessage={projects.length === 0 ? '저장된 대화가 없습니다.' : '분류되지 않은 대화가 없습니다.'}
          />
        </section>
      </div>
    </aside>
  );
}

function ConversationItems({
  conversations,
  projects,
  activeConversationId,
  disabled,
  onSelect,
  onDelete,
  onMove,
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

  async function handleMove(conversationId, projectId) {
    setOpenMenu(null);
    try {
      await onMove(conversationId, projectId);
    } catch {
      // The hook exposes the server message in the panel.
    }
  }

  function toggleMenu(event, conversation) {
    event.stopPropagation();
    if (openMenu?.conversationId === conversation.id) {
      setOpenMenu(null);
      return;
    }

    const trigger = event.currentTarget.getBoundingClientRect();
    const menuWidth = 220;
    const estimatedHeight = Math.min(340, 112 + projects.length * 36);
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
          className={conversation.id === activeConversationId
            ? 'conversation-item selected'
            : 'conversation-item'}
          key={conversation.id}
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
              <div className='conversation-context-heading'>
                <FolderInput size={14} />
                <span>프로젝트 이동</span>
              </div>
              <div className='conversation-context-projects'>
                <button
                  type='button'
                  role='menuitemradio'
                  aria-checked={conversation.projectId == null}
                  className={conversation.projectId == null ? 'current' : ''}
                  onClick={() => handleMove(conversation.id, null)}
                >
                  <span>프로젝트 없음</span>
                  {conversation.projectId == null && <Check size={14} />}
                </button>
                {projects.map((project) => (
                  <button
                    type='button'
                    role='menuitemradio'
                    aria-checked={conversation.projectId === project.id}
                    className={conversation.projectId === project.id ? 'current' : ''}
                    key={project.id}
                    onClick={() => handleMove(conversation.id, project.id)}
                  >
                    <span>{project.name}</span>
                    {conversation.projectId === project.id && <Check size={14} />}
                  </button>
                ))}
              </div>
              <div className='conversation-context-divider' />
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
