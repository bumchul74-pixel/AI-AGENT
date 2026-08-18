export function DataTable({
  columns,
  rows,
  rowKey,
  renderCells,
  rowClassName,
  trailingRows = null,
  className = '',
}) {
  const tableClassName = ['data-table', className].filter(Boolean).join(' ');

  return (
    <table className={tableClassName}>
      <thead>
        <tr>
          {columns.map((column) => (
            <th className={column.className} key={column.key} scope="col">
              {column.header}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => (
          <tr
            className={rowClassName?.(row, index) || undefined}
            key={rowKey(row, index)}
          >
            {renderCells(row, index)}
          </tr>
        ))}
        {trailingRows}
      </tbody>
    </table>
  );
}