import { AlertTriangle, CheckCircle2 } from 'lucide-react';

function packageLabel(packageName) {
  if (packageName === '(default package)') return packageName;
  const segments = packageName.split('.');
  return segments.at(-1) || packageName;
}

export function PackageDependencyMatrix({ analysis, selectedPackage, onSelectPackage }) {
  const {
    packages,
    counts,
    relationTypes,
    cycles,
    cyclePackages,
    dependencyCount,
  } = analysis;

  if (packages.length === 0) {
    return (
      <div className="empty-result java-dependency-empty">
        <strong>분석할 패키지 의존성이 없습니다.</strong>
        <span>JavaType 간 관계가 적재되면 패키지별 의존 방향과 순환 관계를 표시합니다.</span>
      </div>
    );
  }

  return (
    <section className="java-dependency-analysis" aria-label="패키지 의존성 분석">
      <div className="java-dependency-summary">
        <div>
          <span>패키지</span>
          <strong>{packages.length}</strong>
        </div>
        <div>
          <span>교차 패키지 의존</span>
          <strong>{dependencyCount}</strong>
        </div>
        <div className={cycles.length > 0 ? 'has-cycle' : 'is-clean'}>
          <span>순환 그룹</span>
          <strong>{cycles.length}</strong>
        </div>
      </div>

      <div className="java-dependency-matrix-wrap">
        <table className="java-dependency-matrix">
          <caption>행 패키지가 열 패키지에 의존하는 클래스 관계 수</caption>
          <thead>
            <tr>
              <th scope="col">From ╲ To</th>
              {packages.map((targetPackage) => (
                <th
                  key={targetPackage}
                  className={cyclePackages.has(targetPackage) ? 'is-cyclic' : ''}
                  scope="col"
                  title={targetPackage}
                >
                  <button type="button" onClick={() => onSelectPackage(targetPackage)}>
                    {packageLabel(targetPackage)}
                  </button>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {packages.map((sourcePackage) => (
              <tr key={sourcePackage} className={sourcePackage === selectedPackage ? 'is-selected' : ''}>
                <th
                  className={cyclePackages.has(sourcePackage) ? 'is-cyclic' : ''}
                  scope="row"
                  title={sourcePackage}
                >
                  <button type="button" onClick={() => onSelectPackage(sourcePackage)}>
                    {sourcePackage}
                  </button>
                </th>
                {packages.map((targetPackage) => {
                  const count = counts[sourcePackage]?.[targetPackage] ?? 0;
                  const relationshipKey = `${sourcePackage}|${targetPackage}`;
                  const types = relationTypes[relationshipKey] ?? [];
                  const isDiagonal = sourcePackage === targetPackage;
                  return (
                    <td
                      key={targetPackage}
                      className={isDiagonal ? 'is-diagonal' : count > 0 ? 'has-dependency' : ''}
                      title={count > 0
                        ? `${sourcePackage} → ${targetPackage}: ${count}건 (${types.join(', ')})`
                        : isDiagonal ? '동일 패키지' : '의존 없음'}
                    >
                      {isDiagonal ? <span aria-label="동일 패키지">—</span> : count || ''}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <section className="java-cycle-panel" aria-labelledby="java-cycle-title">
        <div className="java-cycle-heading">
          {cycles.length > 0
            ? <AlertTriangle size={17} aria-hidden="true" />
            : <CheckCircle2 size={17} aria-hidden="true" />}
          <div>
            <strong id="java-cycle-title">순환 의존성</strong>
            <span>{cycles.length > 0
              ? '서로 다시 도달할 수 있는 패키지 그룹입니다.'
              : '탐지된 패키지 순환 의존성이 없습니다.'}</span>
          </div>
        </div>
        {cycles.length > 0 && (
          <ol className="java-cycle-list">
            {cycles.map((cycle) => (
              <li key={cycle.id}>
                <strong>{cycle.packages.length}개 패키지</strong>
                <code>{cycle.path.join(' → ')}</code>
              </li>
            ))}
          </ol>
        )}
      </section>
    </section>
  );
}
