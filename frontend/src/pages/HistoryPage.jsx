import { useEffect, useState } from 'react';
import { FileCode2, RotateCcw, Search, ScrollText } from 'lucide-react';
import { fetchGenerationHistory, fetchGenerationHistoryDetail } from '../api/generateApi.js';
import { isApiRequestError } from '../api/apiClient.js';
import { Loading } from '../components/common/Loading.jsx';
import { formatDateTime } from '../utils/dateUtils.js';

const TEXT = {
  historyDescription: 'Postgres DB\uC5D0 \uC800\uC7A5\uB41C \uC0DD\uC131 \uC774\uB825\uC744 \uC870\uD68C\uD569\uB2C8\uB2E4.',
  searchTitle: '\uAC80\uC0C9',
  resultTitle: '\uAC80\uC0C9 \uBAA9\uB85D',
  detailTitle: '\uC0C1\uC138 \uD654\uBA74',
  search: '\uAC80\uC0C9',
  reset: '\uCD08\uAE30\uD654',
  emptyTitle: '\uAC80\uC0C9 \uACB0\uACFC\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.',
  emptyDescription: '\uAC80\uC0C9 \uC870\uAC74\uC744 \uBCC0\uACBD\uD558\uAC70\uB098 Generate \uBA54\uB274\uC5D0\uC11C \uCF54\uB4DC\uB97C \uC0DD\uC131\uD574 \uC8FC\uC138\uC694.',
  noSelectionTitle: '\uC120\uD0DD\uB41C \uC774\uB825\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.',
  noSelectionDescription: '\uAC80\uC0C9 \uBAA9\uB85D\uC5D0\uC11C \uD56D\uBAA9\uC744 \uC120\uD0DD\uD558\uBA74 \uC0C1\uC138 \uB0B4\uC6A9\uC774 \uD45C\uC2DC\uB429\uB2C8\uB2E4.',
  id: 'ID',
  targetType: '\uB300\uC0C1 \uC720\uD615',
  targetTypes: '\uB300\uC0C1 \uC720\uD615 \uBAA9\uB85D',
  prompt: '\uD504\uB86C\uD504\uD2B8',
  projectStructure: '\uD504\uB85C\uC81D\uD2B8 \uAD6C\uC870',
  ragDocuments: 'RAG \uBB38\uC11C',
  generatedCode: '\uC0DD\uC131 \uCF54\uB4DC',
  llmProvider: 'LLM Provider',
  llmModel: 'LLM Model',
  createdFrom: '\uC0DD\uC131\uC77C\uC790 \uC2DC\uC791',
  createdTo: '\uC0DD\uC131\uC77C\uC790 \uC885\uB8CC',
  createdAt: '\uC0DD\uC131\uC77C\uC790',
  provider: 'Provider',
  target: 'Target',
  created: 'Created',
};

function formatDateInput(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function createInitialFilters() {
  const today = new Date();
  const nextWeek = new Date(today);
  nextWeek.setDate(today.getDate() + 7);

  return {
    targetType: '',
    ragDocuments: '',
    createdFrom: formatDateInput(today),
    createdTo: formatDateInput(nextWeek),
  };
}

const searchFields = [
  { name: 'targetType', label: TEXT.targetType },
  { name: 'ragDocuments', label: TEXT.ragDocuments },
  { name: 'createdFrom', label: TEXT.createdFrom, type: 'date' },
  { name: 'createdTo', label: TEXT.createdTo, type: 'date' },
];

function summarize(value, limit = 96) {
  const compact = (value ?? '').replace(/\s+/g, ' ').trim();
  return compact.length > limit ? `${compact.slice(0, limit)}...` : compact;
}

function formatCreatedAt(value) {
  return value ? formatDateTime(value) : '-';
}

export function HistoryPage() {
  const [filters, setFilters] = useState(() => createInitialFilters());
  const [history, setHistory] = useState([]);
  const [selectedId, setSelectedId] = useState(null);
  const [selectedHistory, setSelectedHistory] = useState(null);
  const [error, setError] = useState('');
  const [detailError, setDetailError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [isDetailLoading, setIsDetailLoading] = useState(false);

  function updateFilter(name, value) {
    setFilters((current) => ({ ...current, [name]: value }));
  }

  async function loadDetail(id) {
    if (!id) {
      setSelectedHistory(null);
      return;
    }

    setIsDetailLoading(true);
    setDetailError('');

    try {
      const detail = await fetchGenerationHistoryDetail(id);
      setSelectedHistory(detail);
    } catch (exception) {
      setSelectedHistory(null);
      setDetailError(isApiRequestError(exception) ? '' : exception.message);
    } finally {
      setIsDetailLoading(false);
    }
  }

  async function selectHistory(id) {
    setSelectedId(id);
    await loadDetail(id);
  }

  async function loadHistory(nextFilters = filters) {
    setIsLoading(true);
    setError('');
    setDetailError('');
    setSelectedHistory(null);

    try {
      const nextHistory = await fetchGenerationHistory(nextFilters);
      const nextSelectedId = nextHistory[0]?.id ?? null;
      setHistory(nextHistory);
      setSelectedId(nextSelectedId);
      if (nextSelectedId) {
        await loadDetail(nextSelectedId);
      }
    } catch (exception) {
      setHistory([]);
      setSelectedHistory(null);
      setSelectedId(null);
      setError(isApiRequestError(exception) ? '' : exception.message);
    } finally {
      setIsLoading(false);
    }
  }

  function handleSubmit(event) {
    event.preventDefault();
    loadHistory(filters);
  }

  function handleReset() {
    const nextFilters = createInitialFilters();
    setFilters(nextFilters);
    loadHistory(nextFilters);
  }

  useEffect(() => {
    loadHistory(createInitialFilters());
  }, []);

  return (
    <section className="history-page">
      <section className="card history-search-panel">
        <div className="panel-title">
          <Search size={18} />
          <div>
            <h1>{TEXT.searchTitle}</h1>
            <p>{TEXT.historyDescription}</p>
          </div>
        </div>

        <form className="history-search-form" onSubmit={handleSubmit}>
          <div className="history-search-grid">
            {searchFields.map((field) => (
              <label className="field" key={field.name}>
                <span>{field.label}</span>
                <input
                  type={field.type ?? 'text'}
                  value={filters[field.name]}
                  onChange={(event) => updateFilter(field.name, event.target.value)}
                />
              </label>
            ))}
          </div>

          <div className="history-search-actions">
            <button className="button primary" type="submit" disabled={isLoading}>
              <Search size={17} aria-hidden="true" />
              <span>{TEXT.search}</span>
            </button>
            <button className="button secondary" type="button" onClick={handleReset} disabled={isLoading}>
              <RotateCcw size={17} aria-hidden="true" />
              <span>{TEXT.reset}</span>
            </button>
          </div>
        </form>
      </section>

      <section className="history-content-grid">
        <section className="card history-list-panel">
          <div className="panel-title">
            <ScrollText size={18} />
            <div>
              <h2>{TEXT.resultTitle}</h2>
              <p>{history.length} rows</p>
            </div>
          </div>

          {error && <p className="error-text">{error}</p>}

          {isLoading ? (
            <Loading />
          ) : history.length === 0 ? (
            <div className="empty-result">
              <strong>{TEXT.emptyTitle}</strong>
              <span>{TEXT.emptyDescription}</span>
            </div>
          ) : (
            <div className="history-table-wrap">
              <div className="history-table" role="table" aria-label={TEXT.resultTitle}>
                <div className="history-table-header" role="row">
                  <span role="columnheader">{TEXT.id}</span>
                  <span role="columnheader">{TEXT.targetType}</span>
                  <span role="columnheader">{TEXT.prompt}</span>
                  <span role="columnheader">{TEXT.provider}</span>
                  <span role="columnheader">{TEXT.createdAt}</span>
                </div>
                {history.map((item) => (
                  <button
                    className={selectedId === item.id ? 'history-table-row selected' : 'history-table-row'}
                    key={item.id}
                    type="button"
                    role="row"
                    onClick={() => selectHistory(item.id)}
                  >
                    <span role="cell">#{item.id}</span>
                    <span role="cell">{item.targetType}</span>
                    <span role="cell">{summarize(item.prompt)}</span>
                    <span role="cell">{item.llmProvider ?? '-'}</span>
                    <span role="cell">{formatCreatedAt(item.createdAt)}</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </section>

        <section className="card history-detail-panel">
          <div className="panel-title">
            <FileCode2 size={18} />
            <div>
              <h2>{TEXT.detailTitle}</h2>
              <p>#{selectedId ?? '-'}</p>
            </div>
          </div>

          {detailError && <p className="error-text">{detailError}</p>}

          {isDetailLoading ? (
            <Loading />
          ) : selectedHistory ? (
            <div className="history-detail-content">
              <div className="history-detail-meta">
                <div>
                  <span>{TEXT.target}</span>
                  <strong>{selectedHistory.targetType}</strong>
                </div>
                <div>
                  <span>{TEXT.created}</span>
                  <strong>{formatCreatedAt(selectedHistory.createdAt)}</strong>
                </div>
                <div>
                  <span>{TEXT.provider}</span>
                  <strong>{selectedHistory.llmProvider ?? '-'}</strong>
                </div>
              </div>

              {selectedHistory.targetTypes?.length > 0 && (
                <div className="history-targets">
                  {selectedHistory.targetTypes.map((target) => (
                    <span className="history-pill" key={target}>{target}</span>
                  ))}
                </div>
              )}

              <section className="history-section">
                <h3>{TEXT.prompt}</h3>
                <p>{selectedHistory.prompt}</p>
              </section>

              {selectedHistory.projectStructure && (
                <section className="history-section">
                  <h3>{TEXT.projectStructure}</h3>
                  <pre className="history-text-block">{selectedHistory.projectStructure}</pre>
                </section>
              )}

              <section className="history-section">
                <h3>{TEXT.generatedCode}</h3>
                <pre className="code-output history-code-output">
                  <code>{selectedHistory.generatedCode ?? ''}</code>
                </pre>
              </section>

              {selectedHistory.ragDocuments?.length > 0 && (
                <section className="history-section">
                  <h3>{TEXT.ragDocuments}</h3>
                  <div className="rag-document-list">
                    {selectedHistory.ragDocuments.map((document, index) => (
                      <pre className="rag-document-item" key={`${selectedHistory.id}-${index}`}>{document}</pre>
                    ))}
                  </div>
                </section>
              )}
            </div>
          ) : (
            <div className="empty-result">
              <strong>{TEXT.noSelectionTitle}</strong>
              <span>{TEXT.noSelectionDescription}</span>
            </div>
          )}
        </section>
      </section>
    </section>
  );
}
