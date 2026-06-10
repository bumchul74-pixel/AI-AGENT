export function Input({ label, value, onChange }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input value={value} onChange={onChange} />
    </label>
  );
}
