export function ProjectSelect({
  projects = [],
  value,
  onChange,
  disabled = false,
  className = '',
  label = '프로젝트',
}) {
  const fieldClassName = ['field', 'project-select-field', className].filter(Boolean).join(' ');

  return (
    <label className={fieldClassName}>
      <span>{label}</span>
      <select
        value={value}
        onChange={(event) => onChange(event.target.value)}
        disabled={disabled || projects.length === 0}
      >
        {projects.length === 0 ? (
          <option value="">등록된 프로젝트가 없습니다.</option>
        ) : projects.map((project) => (
          <option key={project.projectKey} value={project.projectKey}>
            {project.name} ({project.projectKey})
          </option>
        ))}
      </select>
    </label>
  );
}
