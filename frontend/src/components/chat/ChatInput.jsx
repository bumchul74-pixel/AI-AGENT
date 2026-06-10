export function ChatInput({ value, onChange }) {
  return (
    <label className="field prompt-field">
      <span>요청 내용</span>
      <textarea value={value} onChange={(event) => onChange(event.target.value)} />
    </label>
  );
}
