export function Input({ label, value, onChange, ...inputProps }) {
  return (
    <label className="field">
      <span>{label}</span>
      <input value={value} onChange={onChange} {...inputProps} />
    </label>
  );
}
