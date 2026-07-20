import { MessageSquare, Plus, Trash2 } from 'lucide-react';

export function ChatConversationList({
  conversations,
  activeConversationId,
  disabled,
  onNew,
  onSelect,
  onDelete,
}) {
  const groups = groupConversations(conversations);

  async function handleDelete(event, conversation) {
    event.stopPropagation();
    if (!window.confirm(`'${conversation.title}' 대화를 삭제할까요?`)) return;
    await onDelete(conversation.id);
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

      <div className='conversation-groups'>
        {groups.map((group) => (
          <section className='conversation-group' key={group.label}>
            <h2>{group.label}</h2>
            <div className='conversation-list'>
              {group.items.map((conversation) => (
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
                  <button
                    className='conversation-delete'
                    type='button'
                    title='대화 삭제'
                    disabled={disabled}
                    onClick={(event) => handleDelete(event, conversation)}
                  >
                    <Trash2 size={14} />
                  </button>
                </div>
              ))}
            </div>
          </section>
        ))}

        {conversations.length === 0 && (
          <p className='conversation-empty'>저장된 대화가 없습니다.</p>
        )}
      </div>
    </aside>
  );
}

function groupConversations(conversations) {
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const buckets = [
    { label: '오늘', items: [] },
    { label: '지난 7일', items: [] },
    { label: '이전', items: [] },
  ];

  conversations.forEach((conversation) => {
    const updatedAt = new Date(conversation.updatedAt);
    const days = Math.floor((today - updatedAt) / 86400000);
    const bucket = days <= 0 ? buckets[0] : days < 7 ? buckets[1] : buckets[2];
    bucket.items.push(conversation);
  });

  return buckets.filter((bucket) => bucket.items.length > 0);
}
