import { useEffect, useState } from 'react';
import { FileCode2, History, Play, Sparkles } from 'lucide-react';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { Modal } from '../components/common/Modal.jsx';
import { MainLayout } from '../components/layout/MainLayout.jsx';
import { ChatInput } from '../components/chat/ChatInput.jsx';
import { ChatResult } from '../components/chat/ChatResult.jsx';
import { fetchProjectStructures } from '../api/generateApi.js';
import { GENERATION_TARGETS } from '../constants/apiConstants.js';
import { useGenerate } from '../hooks/useGenerate.js';
import { useGenerationStore } from '../store/appStore.js';
import { formatDateTime } from '../utils/dateUtils.js';
import { ChatPage } from './ChatPage.jsx';
import { DocumentManagePage } from './DocumentManagePage.jsx';
import { HistoryPage } from './HistoryPage.jsx';
import { RagSearchPage } from './RagSearchPage.jsx';

export function TemplateGeneratePage() {
  const generation = useGenerate();
  const generationStore = useGenerationStore();
  const [activePage, setActivePage] = useState('generate');
  const [targetTypes, setTargetTypes] = useState([GENERATION_TARGETS[0]]);
  const [prompt, setPrompt] = useState('User CRUD API를 생성해줘.');
  const [projectStructure, setProjectStructure] = useState('');
  const [projectStructureOptions, setProjectStructureOptions] = useState([]);
  const [projectStructureError, setProjectStructureError] = useState('');
  const [isProjectStructureLoading, setIsProjectStructureLoading] = useState(false);
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);

  useEffect(() => {
    let ignore = false;

    async function loadProjectStructures() {
      setIsProjectStructureLoading(true);
      setProjectStructureError('');

      try {
        const options = await fetchProjectStructures();
        if (ignore) {
          return;
        }

        setProjectStructureOptions(options);
        setProjectStructure((current) => current || options[0]?.value || '');
      } catch (exception) {
        if (!ignore) {
          setProjectStructureError(exception.message);
        }
      } finally {
        if (!ignore) {
          setIsProjectStructureLoading(false);
        }
      }
    }

    loadProjectStructures();

    return () => {
      ignore = true;
    };
  }, []);
  useEffect(() => {
    if (activePage === 'generate') {
      generation.clearError();
    }
  }, [activePage]);


  function toggleTargetType(target) {
    setTargetTypes((current) => {
      if (current.includes(target)) {
        return current.filter((item) => item !== target);
      }

      return [...current, target];
    });
  }
  async function handleGenerate() {
    const response = await generation.submit({ targetTypes, prompt, projectStructure });

    if (response) {
      generationStore.addHistory({
        targetType: response.targetType ?? targetTypes.join(', '),
        targetTypes,
        prompt,
        generatedCode: response.generatedCode,
        projectStructure,
        createdAt: new Date().toISOString(),
      });
    }
  }

  function renderPage() {
    if (activePage === 'chat') {
      return <ChatPage />;
    }

    if (activePage === 'documents') {
      return <DocumentManagePage />;
    }

    if (activePage === 'rag') {
      return <RagSearchPage />;
    }

    if (activePage === 'history') {
      return <HistoryPage history={generationStore.history} />;
    }

    return (
      <section className="work-grid">
        <div className="card request-panel">
          <div className="panel-title">
            <Sparkles size={18} />
            <div>
              <h1>Java Source Generator</h1>
              <p>RAG 검색 결과를 기반으로 표준 패턴의 Java 소스를 생성합니다.</p>
            </div>
          </div>

          <div className="target-grid" aria-label="생성 대상 선택">
            {GENERATION_TARGETS.map((target) => (
              <button
                className={targetTypes.includes(target) ? 'target-chip selected' : 'target-chip'}
                key={target}
                type="button"
                aria-pressed={targetTypes.includes(target)}
                onClick={() => toggleTargetType(target)}
              >
                {target}
              </button>
            ))}
          </div>


          <ChatInput value={prompt} onChange={setPrompt} />

          <label className="field project-structure-field">
            <span>Project Structure</span>
            <select
              value={projectStructure}
              onChange={(event) => setProjectStructure(event.target.value)}
              disabled={isProjectStructureLoading || projectStructureOptions.length === 0}
            >
              {projectStructureOptions.length === 0 ? (
                <option value="">No project structures configured</option>
              ) : projectStructureOptions.map((option, index) => (
                <option key={`${option.name}-${index}`} value={option.value}>{option.name}</option>
              ))}
            </select>
          </label>

          {projectStructureError && <p className="error-text">{projectStructureError}</p>}

          {generation.error && <p className="error-text">{generation.error}</p>}

          <div className="action-row">
            <Button icon={Play} onClick={handleGenerate} disabled={generation.isLoading || isProjectStructureLoading || !projectStructure || targetTypes.length === 0}>
              생성
            </Button>
            <Button icon={History} variant="secondary" onClick={() => setIsHistoryOpen(true)}>
              이력
            </Button>
          </div>
        </div>

        <div className="card result-panel">
          <div className="panel-title">
            <FileCode2 size={18} />
            <div>
              <h2>Generated Source</h2>
              <p>생성된 Java 코드가 이 영역에 표시됩니다.</p>
            </div>
          </div>

          {generation.isLoading ? <Loading /> : <ChatResult result={generation.result} />}
        </div>
      </section>
    );
  }

  return (
    <MainLayout activePage={activePage} onNavigate={setActivePage}>
      {renderPage()}

      <Modal title="생성 이력" open={isHistoryOpen} onClose={() => setIsHistoryOpen(false)}>
        {generationStore.history.length === 0 ? (
          <p className="muted">아직 생성 이력이 없습니다.</p>
        ) : (
          <div className="history-list">
            {generationStore.history.map((item) => (
              <article className="history-item" key={`${item.createdAt}-${item.targetType}`}>
                <strong>{item.targetType}</strong>
                <span>{formatDateTime(item.createdAt)}</span>
                <p>{item.prompt}</p>
              </article>
            ))}
          </div>
        )}
      </Modal>
    </MainLayout>
  );
}
