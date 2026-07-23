import { useEffect, useState } from 'react';
import { FileCode2, Play, Sparkles } from 'lucide-react';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';
import { ProjectSelect } from '../components/common/ProjectSelect.jsx';
import { MainLayout } from '../components/layout/MainLayout.jsx';
import { ChatInput } from '../components/chat/ChatInput.jsx';
import { ChatResult } from '../components/chat/ChatResult.jsx';
import { fetchKnowledgeProjects } from '../api/projectApi.js';
import { GENERATION_TARGETS } from '../constants/apiConstants.js';
import { useGenerate } from '../hooks/useGenerate.js';
import { ChatPage } from './ChatPage.jsx';
import { DocumentManagePage } from './DocumentManagePage.jsx';
import { DataCleanupPage } from './DataCleanupPage.jsx';
import { DashboardPage } from './DashboardPage.jsx';
import { HistoryPage } from './HistoryPage.jsx';
import { JavaGraphPage } from './JavaGraphPage.jsx';
import { RagSearchPage } from './RagSearchPage.jsx';
import { ProjectManagePage } from './ProjectManagePage.jsx';
import { SecureCodingPage } from './SecureCodingPage.jsx';

const TEXT = {
  defaultPrompt: 'User CRUD API\uB97C \uC0DD\uC131\uD574\uC918.',
  generatorDescription: 'RAG \uAC80\uC0C9 \uACB0\uACFC\uB97C \uAE30\uBC18\uC73C\uB85C \uD45C\uC900 \uD328\uD134\uC758 Java \uC18C\uC2A4\uB97C \uC0DD\uC131\uD569\uB2C8\uB2E4.',
  targetSelect: '\uC0DD\uC131 \uB300\uC0C1 \uC120\uD0DD',
  generate: '\uC0DD\uC131',
  resultDescription: '\uC0DD\uC131\uB41C Java \uCF54\uB4DC\uAC00 \uC774 \uC601\uC5ED\uC5D0 \uD45C\uC2DC\uB429\uB2C8\uB2E4.',
};

export function TemplateGeneratePage() {
  const generation = useGenerate();
  const [activePage, setActivePage] = useState('dashboard');
  const [targetTypes, setTargetTypes] = useState([GENERATION_TARGETS[0]]);
  const [prompt, setPrompt] = useState(TEXT.defaultPrompt);
  const [projectKey, setProjectKey] = useState('');
  const [projects, setProjects] = useState([]);
  const [isProjectLoading, setIsProjectLoading] = useState(false);

  useEffect(() => {
    if (activePage !== 'generate') {
      return undefined;
    }

    let ignore = false;

    async function loadProjects() {
      setIsProjectLoading(true);

      try {
        const options = await fetchKnowledgeProjects();
        if (ignore) {
          return;
        }

        setProjects(options);
        setProjectKey((current) => current || options[0]?.projectKey || '');
      } catch {
        if (!ignore) {
          setProjects([]);
          setProjectKey('');
        }
      } finally {
        if (!ignore) {
          setIsProjectLoading(false);
        }
      }
    }

    loadProjects();

    return () => {
      ignore = true;
    };
  }, [activePage]);

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
    await generation.submit({ targetTypes, prompt, projectKey });
  }

  function renderPage() {
    if (activePage === 'dashboard') {
      return <DashboardPage onNavigate={setActivePage} />;
    }

    if (activePage === 'chat') {
      return <ChatPage />;
    }

    if (activePage === 'documents') {
      return <DocumentManagePage />;
    }

    if (activePage === 'projects') {
      return <ProjectManagePage />;
    }

    if (activePage === 'rag') {
      return <RagSearchPage />;
    }

    if (activePage === 'javaGraph') {
      return <JavaGraphPage />;
    }

    if (activePage === 'history') {
      return <HistoryPage />;
    }

    if (activePage === 'dataCleanup') {
      return <DataCleanupPage />;
    }

    if (activePage === 'secureCoding') {
      return <SecureCodingPage />;
    }

    return (
      <section className="work-grid">
        <div className="card request-panel">
          <div className="panel-title">
            <Sparkles size={18} />
            <div>
              <h1>Java Source Generator</h1>
              <p>{TEXT.generatorDescription}</p>
            </div>
          </div>

          <div className="target-grid" aria-label={TEXT.targetSelect}>
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

          <ProjectSelect
            projects={projects}
            value={projectKey}
            onChange={setProjectKey}
            disabled={isProjectLoading}
            className="project-structure-field"
          />

          {projectKey && (() => {
            const project = projects.find((item) => item.projectKey === projectKey);
            return project ? (
              <p className="field-help">
                {project.description || '설명 없음'} · 색인 문서 {project.documentCount ?? 0}개
              </p>
            ) : null;
          })()}

          {generation.error && <p className="error-text">{generation.error}</p>}

          <div className="action-row">
            <Button icon={Play} onClick={handleGenerate} disabled={generation.isLoading || isProjectLoading || !projectKey || targetTypes.length === 0}>
              {TEXT.generate}
            </Button>
          </div>
        </div>

        <div className="card result-panel">
          <div className="panel-title">
            <FileCode2 size={18} />
            <div>
              <h2>Generated Source</h2>
              <p>{TEXT.resultDescription}</p>
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
    </MainLayout>
  );
}
