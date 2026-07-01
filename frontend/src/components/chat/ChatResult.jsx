export function ChatResult({ result }) {
  if (!result) {
    return (
      <div className="empty-result">
        <strong>생성 결과 대기 중</strong>
        <span>RAG 검색 결과와 선택한 LLM Provider를 기반으로 Java 소스가 표시됩니다.</span>
      </div>
    );
  }

  return (
    <div className="generation-result">
      {result.mcpContextApplied && (
        <div className="mcp-result-notice" role="status">
          {result.mcpContextMessage || 'MCP 결과가 생성 결과에 반영되었습니다.'}
        </div>
      )}
      <pre className="code-output">
        <code>{result.generatedCode}</code>
      </pre>
    </div>
  );
}
