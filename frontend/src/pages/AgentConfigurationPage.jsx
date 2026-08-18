import { useEffect, useState } from 'react';
import {
  Bot,
  Database,
  Plus,
  RefreshCw,
  Save,
  Trash2,
} from 'lucide-react';
import {
  fetchActiveAgentConfiguration,
  refreshAgentConfiguration,
  saveAndActivateAgentConfiguration,
} from '../api/agentConfigurationApi.js';
import { notifyApp } from '../api/apiClient.js';
import { Button } from '../components/common/Button.jsx';
import { Loading } from '../components/common/Loading.jsx';

function emptyCapability(index) {
  return {
    id: 'capability.' + index,
    tool: '',
    enabled: true,
    intents: [],
    argumentResolver: 'none',
    priority: 10,
    timeoutMs: 30000,
    requiresApproval: false,
    dependencies: [],
    maxAttempts: 1,
    retryBackoffMs: 100,
    fallbackCapabilityIds: [],
  };
}

function emptyAgent(index) {
  return {
    id: 'agent-' + index,
    name: 'New Agent',
    enabled: true,
    executor: 'mcp',
    server: 'ai-mcp',
    capabilities: [emptyCapability(1)],
  };
}

function cloneConfiguration(configuration) {
  return {
    maxParallelism: Number(configuration?.maxParallelism ?? 4),
    agents: (configuration?.agents ?? []).map((agent) => ({
      ...agent,
      capabilities: (agent.capabilities ?? []).map((capability) => ({
        ...capability,
        intents: [...(capability.intents ?? [])],
        dependencies: [...(capability.dependencies ?? [])],
        fallbackCapabilityIds: [...(capability.fallbackCapabilityIds ?? [])],
      })),
    })),
  };
}

function parseList(value) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function FieldAlias({ label, alias, description }) {
  const tooltip = `별명: ${alias}${description ? `\n${description}` : ''}`;
  return (
    <span
      className="agent-config-field-alias"
      title={tooltip}
      tabIndex={0}
      aria-label={`${label}. ${tooltip}`}
    >
      {label}
    </span>
  );
}

function CapabilityEditor({ agentIndex, capability, capabilityIndex, onChange, onRemove }) {
  function field(name, value) {
    onChange(agentIndex, capabilityIndex, name, value);
  }

  return (
    <article className="agent-config-capability">
      <header>
        <div>
          <Bot size={16} />
          <strong>{capability.id || '새 Capability'}</strong>
          <span>{capability.tool || 'Tool 미지정'}</span>
        </div>
        <button className="agent-config-delete-button" type="button" onClick={onRemove}>
          <Trash2 size={15} />
          <span>삭제</span>
        </button>
      </header>

      <div className="agent-config-form-grid">
        <label className="field"><FieldAlias label="Capability ID" alias="capability.id" description="Capability를 참조하는 고유 식별자" /><input value={capability.id} onChange={(event) => field('id', event.target.value)} /></label>
        <label className="field"><FieldAlias label="MCP Tool" alias="capability.tool" description="실행할 MCP tools/call 이름" /><input value={capability.tool} onChange={(event) => field('tool', event.target.value)} /></label>
        <label className="field"><FieldAlias label="Argument Resolver" alias="capability.argumentResolver" description="사용자 요청에서 Tool 인자를 만드는 전략" /><input value={capability.argumentResolver} onChange={(event) => field('argumentResolver', event.target.value)} /></label>
        <label className="field"><FieldAlias label="우선순위" alias="capability.priority" description="여러 Capability 후보 중 선택 우선순위" /><input min="0" type="number" value={capability.priority} onChange={(event) => field('priority', Number(event.target.value))} /></label>
        <label className="field"><FieldAlias label="Timeout (ms)" alias="capability.timeoutMs" description="한 번의 Tool 실행 제한 시간" /><input min="1" type="number" value={capability.timeoutMs} onChange={(event) => field('timeoutMs', Number(event.target.value))} /></label>
        <label className="field"><FieldAlias label="최대 시도 횟수" alias="capability.maxAttempts" description="최초 실행을 포함한 최대 시도 횟수" /><input min="1" type="number" value={capability.maxAttempts} onChange={(event) => field('maxAttempts', Number(event.target.value))} /></label>
        <label className="field"><FieldAlias label="재시도 대기 (ms)" alias="capability.retryBackoffMs" description="실패 후 다음 시도까지의 대기 시간" /><input min="0" type="number" value={capability.retryBackoffMs} onChange={(event) => field('retryBackoffMs', Number(event.target.value))} /></label>
        <label className="field agent-config-wide"><FieldAlias label="Intent 목록" alias="capability.intents" description="이 Capability로 routing할 업무 의도 목록" /><input value={(capability.intents ?? []).join(', ')} placeholder="database-search, source-search" onChange={(event) => field('intents', parseList(event.target.value))} /></label>
        <label className="field agent-config-wide"><FieldAlias label="선행 Capability ID" alias="capability.dependencies" description="먼저 성공해야 하는 Capability ID 목록" /><input value={(capability.dependencies ?? []).join(', ')} placeholder="database.columns" onChange={(event) => field('dependencies', parseList(event.target.value))} /></label>
        <label className="field agent-config-wide"><FieldAlias label="Fallback Capability ID" alias="capability.fallbackCapabilityIds" description="실패 시 순서대로 대체 실행할 Capability 목록" /><input value={(capability.fallbackCapabilityIds ?? []).join(', ')} placeholder="source.search" onChange={(event) => field('fallbackCapabilityIds', parseList(event.target.value))} /></label>
      </div>

      <div className="agent-config-switches">
        <label><input type="checkbox" checked={capability.enabled} onChange={(event) => field('enabled', event.target.checked)} /> <FieldAlias label="활성화" alias="capability.enabled" description="이 Capability를 routing과 실행 대상에 포함" /></label>
        <label><input type="checkbox" checked={capability.requiresApproval} onChange={(event) => field('requiresApproval', event.target.checked)} /> <FieldAlias label="실행 승인 필요" alias="capability.requiresApproval" description="Tool 실행 전에 승인이 필요한지 표시" /></label>
      </div>
    </article>
  );
}

export function AgentConfigurationPage() {
  const [view, setView] = useState(null);
  const [configuration, setConfiguration] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSaving, setIsSaving] = useState(false);

  async function loadActive() {
    const active = await fetchActiveAgentConfiguration();
    setView(active);
    setConfiguration(cloneConfiguration(active.configuration));
    return active;
  }

  useEffect(() => {
    loadActive()
      .catch(() => {})
      .finally(() => setIsLoading(false));
  }, []);

  function updateAgent(agentIndex, name, value) {
    setConfiguration((current) => ({
      ...current,
      agents: current.agents.map((agent, index) => (
        index === agentIndex ? { ...agent, [name]: value } : agent
      )),
    }));
  }

  function updateCapability(agentIndex, capabilityIndex, name, value) {
    setConfiguration((current) => ({
      ...current,
      agents: current.agents.map((agent, index) => (
        index === agentIndex
          ? {
              ...agent,
              capabilities: agent.capabilities.map((capability, childIndex) => (
                childIndex === capabilityIndex ? { ...capability, [name]: value } : capability
              )),
            }
          : agent
      )),
    }));
  }

  function addAgent() {
    setConfiguration((current) => ({
      ...current,
      agents: [...current.agents, emptyAgent(current.agents.length + 1)],
    }));
  }

  function removeAgent(agentIndex) {
    setConfiguration((current) => ({
      ...current,
      agents: current.agents.filter((_, index) => index !== agentIndex),
    }));
  }

  function addCapability(agentIndex) {
    setConfiguration((current) => ({
      ...current,
      agents: current.agents.map((agent, index) => (
        index === agentIndex
          ? {
              ...agent,
              capabilities: [
                ...agent.capabilities,
                emptyCapability(agent.capabilities.length + 1),
              ],
            }
          : agent
      )),
    }));
  }

  function removeCapability(agentIndex, capabilityIndex) {
    setConfiguration((current) => ({
      ...current,
      agents: current.agents.map((agent, index) => (
        index === agentIndex
          ? {
              ...agent,
              capabilities: agent.capabilities.filter(
                (_, childIndex) => childIndex !== capabilityIndex,
              ),
            }
          : agent
      )),
    }));
  }

  async function handleSave() {
    if (!configuration) return;
    setIsSaving(true);
    try {
      const saved = await saveAndActivateAgentConfiguration(configuration);
      setView(saved);
      setConfiguration(cloneConfiguration(saved.configuration));
      notifyApp('Agent 설정을 새 DB 버전으로 저장하고 즉시 활성화했습니다.');
    } catch {
      // API 오류는 공통 Toast에서 표시한다.
    } finally {
      setIsSaving(false);
    }
  }

  async function handleRefresh() {
    setIsLoading(true);
    try {
      const refreshed = await refreshAgentConfiguration();
      setView(refreshed);
      setConfiguration(cloneConfiguration(refreshed.configuration));
      notifyApp('DB의 활성 Agent 설정을 Memory에 반영했습니다.');
    } catch {
      // API 오류는 공통 Toast에서 표시한다.
    } finally {
      setIsLoading(false);
    }
  }

  async function handleReload() {
    setIsLoading(true);
    try {
      await loadActive();
    } catch {
      // API 오류는 공통 Toast에서 표시한다.
    } finally {
      setIsLoading(false);
    }
  }

  if (isLoading && !configuration) return <Loading />;

  return (
    <section className="agent-config-page">
      <section className="card agent-config-hero">
        <div className="panel-title">
          <Database size={19} />
          <div>
            <h1>Agent 설정 관리</h1>
            <p>저장하면 새 DB 버전이 활성화되고 다음 업무 요청부터 Memory Snapshot으로 사용됩니다.</p>
          </div>
        </div>
        <div className="agent-config-actions">
          <span className="agent-config-version">
            <small>{view?.source ?? '-'}</small>
            <strong>{view?.version ?? '-'}</strong>
          </span>
          <Button variant="secondary" icon={RefreshCw} onClick={handleRefresh} disabled={isLoading || isSaving}>DB 다시 반영</Button>
          <Button icon={Save} onClick={handleSave} disabled={!configuration || isLoading || isSaving}>{isSaving ? '저장 중...' : '저장 및 활성화'}</Button>
        </div>
      </section>

      <section className="agent-config-guide" aria-label="저장 및 삭제 안내">
        <strong>저장·삭제 방법</strong>
        <span>Agent 또는 Capability의 삭제 버튼으로 편집 목록에서 제거한 후 <b>저장 및 활성화</b>를 누르면 DB에 새 버전으로 반영됩니다. 이전 버전은 실행 이력 추적을 위해 보존됩니다.</span>
      </section>

      {!configuration && (
        <section className="card agent-config-load-empty">
          <Database size={24} />
          <h2>편집할 활성 설정이 없습니다.</h2>
          <p>관리 API와 DB 상태를 확인한 뒤 활성 설정을 다시 불러와 주세요.</p>
          <Button variant="secondary" icon={RefreshCw} onClick={handleReload} disabled={isLoading}>
            다시 불러오기
          </Button>
        </section>
      )}

      {configuration && (
        <>
          <section className="card agent-config-policy">
            <div>
              <h2>Workflow 정책</h2>
              <p>한 요청에서 동시에 실행할 준비 단계의 최대 개수입니다.</p>
            </div>
            <label className="field">
              <FieldAlias label="최대 병렬 실행 수" alias="configuration.maxParallelism" description="동시에 실행할 수 있는 준비 단계의 최대 개수" />
              <input min="1" type="number" value={configuration.maxParallelism} onChange={(event) => setConfiguration((current) => ({ ...current, maxParallelism: Number(event.target.value) }))} />
            </label>
          </section>

          <section className="agent-config-list">
            {configuration.agents.map((agent, agentIndex) => (
              <article className="card agent-config-agent" key={agentIndex}>
                <header className="agent-config-agent-header">
                  <div>
                    <Bot size={19} />
                    <div>
                      <h2>{agent.name || agent.id || '새 Agent'}</h2>
                      <p>{agent.capabilities.length}개 Capability</p>
                    </div>
                  </div>
                  <div className="agent-config-agent-actions">
                    <label><input type="checkbox" checked={agent.enabled} onChange={(event) => updateAgent(agentIndex, 'enabled', event.target.checked)} /> <FieldAlias label="Agent 활성화" alias="agent.enabled" description="이 Agent와 하위 Capability를 활성 Snapshot에 포함" /></label>
                    <Button variant="secondary" icon={Plus} onClick={() => addCapability(agentIndex)}>Capability 추가</Button>
                    <button className="agent-config-delete-button" type="button" onClick={() => removeAgent(agentIndex)}>
                      <Trash2 size={16} />
                      <span>Agent 삭제</span>
                    </button>
                  </div>
                </header>

                <div className="agent-config-agent-fields">
                  <label className="field"><FieldAlias label="Agent ID" alias="agent.id" description="Agent를 식별하는 고유 ID" /><input value={agent.id} onChange={(event) => updateAgent(agentIndex, 'id', event.target.value)} /></label>
                  <label className="field"><FieldAlias label="이름" alias="agent.name" description="화면과 운영에서 사용하는 Agent 표시명" /><input value={agent.name} onChange={(event) => updateAgent(agentIndex, 'name', event.target.value)} /></label>
                  <label className="field"><FieldAlias label="Executor" alias="agent.executor" description="Capability 실행 방식" /><input value={agent.executor} onChange={(event) => updateAgent(agentIndex, 'executor', event.target.value)} /></label>
                  <label className="field"><FieldAlias label="MCP Server" alias="agent.server" description="Tool을 실행할 MCP 서버 식별자" /><input value={agent.server} onChange={(event) => updateAgent(agentIndex, 'server', event.target.value)} /></label>
                </div>

                <div className="agent-config-capability-list">
                  {agent.capabilities.map((capability, capabilityIndex) => (
                    <CapabilityEditor
                      key={capabilityIndex}
                      agentIndex={agentIndex}
                      capability={capability}
                      capabilityIndex={capabilityIndex}
                      onChange={updateCapability}
                      onRemove={() => removeCapability(agentIndex, capabilityIndex)}
                    />
                  ))}
                  {agent.capabilities.length === 0 && (
                    <div className="agent-config-empty">활성화할 Capability를 추가해 주세요.</div>
                  )}
                </div>
              </article>
            ))}

            <button className="agent-config-add-agent" type="button" onClick={addAgent}>
              <Plus size={18} />
              Agent 추가
            </button>
          </section>
        </>
      )}
    </section>
  );
}
