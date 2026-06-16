import { useEffect, useMemo, useState } from 'react';
import { DatabaseZap, FileSearch, Search } from 'lucide-react';
import { fetchRagStats } from '../api/ragApi.js';
import { Button } from '../components/common/Button.jsx';
import { Input } from '../components/common/Input.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { useRagSearch } from '../hooks/useRagSearch.js';

function summarizeDocument(document) {
  const compact = document.replace(/\s+/g, ' ').trim();
  return compact.length > 160 ? `${compact.slice(0, 160)}...` : compact;
}

export function RagSearchPage() {
  const ragSearch = useRagSearch();
  const [query, setQuery] = useState('');
  const [topK, setTopK] = useState('5');
  const [selectedIndex, setSelectedIndex] = useState(null);
  const [javaFileCount, setJavaFileCount] = useState(0);
  const [statsError, setStatsError] = useState('');

  const selectedDocument = useMemo(() => {
    if (selectedIndex === null) {
      return '';
    }

    return ragSearch.documents[selectedIndex] ?? '';
  }, [ragSearch.documents, selectedIndex]);

  async function loadStats() {
    try {
      const stats = await fetchRagStats();
      setJavaFileCount(stats.javaFileCount ?? stats.java_file_count ?? 0);
      setStatsError('');
    } catch (exception) {
      setJavaFileCount(0);
      setStatsError(exception.message);
    }
  }

  useEffect(() => {
    loadStats();
  }, []);

  async function handleSearch() {
    const result = await ragSearch.search({
      query,
      topK: Number(topK) || 5,
    });

    setSelectedIndex(result && result.length > 0 ? 0 : null);
    loadStats();
  }

  function handleSubmit(event) {
    event.preventDefault();
    handleSearch();
  }

  return (
    <section className="rag-page">
      <form className="card rag-search-panel" onSubmit={handleSubmit}>
        <div className="rag-search-header">
          <div className="panel-title">
            <DatabaseZap size={18} />
            <div>
              <h1>Rag조회</h1>
              <p>VectorDB에 저장된 표준 문서와 Java 소스 chunk를 검색합니다.</p>
            </div>
          </div>

          <div className="stat-card">
            <span>로드된 Java 파일</span>
            <strong>{javaFileCount.toLocaleString()}</strong>
          </div>
        </div>

        <div className="rag-search-fields">
          <Input
            label="검색어"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="VectorDB에서 조회할 문서 또는 소스코드 내용을 입력하세요."
          />
          <Input
            label="조회 건수"
            value={topK}
            onChange={(event) => setTopK(event.target.value)}
            type="number"
            min="1"
            max="20"
          />
        </div>

        {statsError && <p className="error-text">{statsError}</p>}
        {ragSearch.error && <p className="error-text">{ragSearch.error}</p>}

        <Button icon={Search} disabled={ragSearch.isLoading} onClick={handleSearch}>
          검색
        </Button>
      </form>

      <div className="rag-content-grid">
        <section className="card rag-list-panel">
          <div className="panel-title">
            <FileSearch size={18} />
            <div>
              <h2>검색 목록</h2>
              <p>항목을 선택하면 상세 chunk 내용을 확인할 수 있습니다.</p>
            </div>
          </div>

          {ragSearch.isLoading ? (
            <Loading />
          ) : ragSearch.documents.length === 0 ? (
            <div className="empty-result">검색 결과가 없습니다.</div>
          ) : (
            <div className="rag-result-list">
              {ragSearch.documents.map((document, index) => (
                <button
                  className={selectedIndex === index ? 'rag-result-item selected' : 'rag-result-item'}
                  key={`${index}-${document.slice(0, 24)}`}
                  type="button"
                  onClick={() => setSelectedIndex(index)}
                >
                  <strong>Result {index + 1}</strong>
                  <span>{summarizeDocument(document)}</span>
                </button>
              ))}
            </div>
          )}
        </section>

        <section className="card rag-detail-panel">
          <div className="panel-title">
            <DatabaseZap size={18} />
            <div>
              <h2>상세 내용</h2>
              <p>VectorDB에서 반환된 원문 chunk입니다.</p>
            </div>
          </div>

          {selectedDocument ? (
            <pre className="rag-detail-content">{selectedDocument}</pre>
          ) : (
            <div className="empty-result">목록에서 항목을 선택하면 상세 내용이 표시됩니다.</div>
          )}
        </section>
      </div>
    </section>
  );
}
