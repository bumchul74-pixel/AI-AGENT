import { formatDateTime } from '../utils/dateUtils.js';

export function HistoryPage({ history = [] }) {
  return (
    <section className="page-panel">
      <h1>History</h1>
      <p>현재 브라우저 세션에서 생성한 코드 이력을 확인합니다.</p>

      {history.length === 0 ? (
        <div className="empty-result">
          <strong>생성 이력이 없습니다.</strong>
          <span>Generate 메뉴에서 코드를 생성하면 여기에 표시됩니다.</span>
        </div>
      ) : (
        <div className="history-list">
          {history.map((item) => (
            <article className="history-item" key={`${item.createdAt}-${item.targetType}`}>
              <strong>{item.targetType}</strong>
              <span>{formatDateTime(item.createdAt)}</span>
              <p>{item.prompt}</p>
            </article>
          ))}
        </div>
      )}
    </section>
  );
}
