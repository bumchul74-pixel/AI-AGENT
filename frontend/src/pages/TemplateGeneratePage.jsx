import { useState } from 'react';
import { FileCode2, History, Play, Sparkles } from 'lucide-react';
import { Button } from '../components/common/Button.jsx';
import { Input } from '../components/common/Input.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { Modal } from '../components/common/Modal.jsx';
import { Header } from '../components/layout/Header.jsx';
import { Sidebar } from '../components/layout/Sidebar.jsx';
import { ChatInput } from '../components/chat/ChatInput.jsx';
import { ChatResult } from '../components/chat/ChatResult.jsx';
import { GENERATION_TARGETS } from '../constants/apiConstants.js';
import { useGenerate } from '../hooks/useGenerate.js';
import { useGenerationStore } from '../store/appStore.js';
import { formatDateTime } from '../utils/dateUtils.js';
import { ChatPage } from './ChatPage.jsx';
import { DocumentManagePage } from './DocumentManagePage.jsx';
import { HistoryPage } from './HistoryPage.jsx';

export function TemplateGeneratePage() {
  const generation = useGenerate();
  const generationStore = useGenerationStore();
  const [activePage, setActivePage] = useState('generate');
  const [targetType, setTargetType] = useState(GENERATION_TARGETS[0]);
  const [prompt, setPrompt] = useState('User 업무의 Controller, Service, DTO 구조를 생성해줘.');
  const [isHistoryOpen, setIsHistoryOpen] = useState(false);

  async function handleGenerate() {
    const response = await generation.submit({ targetType, prompt });

    if (response) {
      generationStore.addHistory({
        targetType,
        prompt,
        generatedCode: response.generatedCode,
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

    if (activePage === 'history') {
      return <HistoryPage history={generationStore.history} />;
    }

    return (
      <section className="work-grid">
        <div className="request-panel">
          <div className="panel-title">
            <Sparkles size={18} />
            <h1>Java Source Generator</h1>
          </div>

          <div className="target-grid">
            {GENERATION_TARGETS.map((target) => (
              <button
                className={targetType === target ? 'target-chip selected' : 'target-chip'}
                key={target}
                type="button"
                onClick={() => setTargetType(target)}
              >
                {target}
              </button>
            ))}
          </div>

          <Input
            label="생성 대상"
            value={targetType}
            onChange={(event) => setTargetType(event.target.value)}
          />

          <ChatInput value={prompt} onChange={setPrompt} />

          {generation.error && <p className="error-text">{generation.error}</p>}

          <div className="action-row">
            <Button icon={Play} onClick={handleGenerate} disabled={generation.isLoading}>
              생성
            </Button>
            <Button icon={History} variant="secondary" onClick={() => setIsHistoryOpen(true)}>
              이력
            </Button>
          </div>
        </div>

        <div className="result-panel">
          <div className="panel-title">
            <FileCode2 size={18} />
            <h2>Generated Source</h2>
          </div>

          {generation.isLoading ? <Loading /> : <ChatResult result={generation.result} />}
        </div>
      </section>
    );
  }

  return (
    <div className="app-shell">
      <Sidebar activePage={activePage} onNavigate={setActivePage} />
      <main className="workspace">
        <Header />
        {renderPage()}
      </main>

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
    </div>
  );
}
