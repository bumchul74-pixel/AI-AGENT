import {
  CircleCheck,
  CircleDashed,
  CircleX,
  GitBranch,
  RefreshCw,
} from 'lucide-react';

const STATUS_LABELS = {
  SUCCEEDED: '완료',
  SUCCEEDED_WITH_FALLBACK: '대체 실행 완료',
  PARTIAL: '부분 완료',
  FALLBACK_SUCCEEDED: '대체 완료',
  FAILED: '실패',
  SKIPPED: '건너뜀',
};

function StatusIcon({ status }) {
  if (status === 'SUCCEEDED' || status === 'FALLBACK_SUCCEEDED') {
    return <CircleCheck size={14} aria-hidden="true" />;
  }
  if (status === 'FAILED') {
    return <CircleX size={14} aria-hidden="true" />;
  }
  return <CircleDashed size={14} aria-hidden="true" />;
}

export function AgentExecutionSteps({ execution }) {
  if (!execution?.steps?.length) return null;

  return (
    <details className="agent-execution">
      <summary>
        <span>
          <GitBranch size={14} aria-hidden="true" />
          Agent 실행 단계
        </span>
        <strong data-status={execution.status}>
          {STATUS_LABELS[execution.status] || execution.status}
        </strong>
      </summary>
      <ol className="agent-execution-steps">
        {execution.steps.map((step) => (
          <li key={step.stepId} data-status={step.status}>
            <StatusIcon status={step.status} />
            <div>
              <strong>{step.capabilityId || step.target}</strong>
              <span>
                {STATUS_LABELS[step.status] || step.status}
                {' · '}
                {step.durationMs}ms
                {step.attempts > 1 && (
                  <>
                    {' · '}
                    <RefreshCw size={10} aria-hidden="true" />
                    {step.attempts}회
                  </>
                )}
              </span>
              {step.dependencies?.length > 0 && (
                <small>선행 단계: {step.dependencies.join(', ')}</small>
              )}
              {step.fallbackCapabilityId && (
                <small>대체 Agent: {step.fallbackCapabilityId}</small>
              )}
            </div>
          </li>
        ))}
      </ol>
      <footer>실행 ID {execution.executionId}</footer>
    </details>
  );
}