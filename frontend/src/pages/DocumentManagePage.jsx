import { FileStack } from 'lucide-react';

export function DocumentManagePage() {
  return (
    <section className="card page-panel">
      <div className="panel-title">
        <FileStack size={18} />
        <div>
          <h1>Documents</h1>
          <p>RAG 서버에 표준 문서와 소스코드를 업로드하고 관리하는 화면입니다.</p>
        </div>
      </div>

      <div className="empty-result">
        <strong>문서 관리 API 준비 중</strong>
        <span>백엔드와 Python RAG 서버 연동 후 업로드 기능을 연결합니다.</span>
      </div>
    </section>
  );
}
