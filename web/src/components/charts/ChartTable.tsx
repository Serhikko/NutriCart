import type { ReactNode } from 'react';

interface ChartTableProps {
  /** The chart's title, read as the table's caption. */
  caption: ReactNode;
  /** Column headers; the first column names the row (the day). */
  head: string[];
  /** One row per point; the first cell is the row header. */
  rows: (string | number)[][];
}

/**
 * The numbers behind a chart as a real table, visually hidden. The drawn
 * chart is decoration for sighted readers (aria-hidden); a screen reader
 * walks this table instead, row by row, with the column headers announced.
 * The hiding is on a wrapper: a table ignores the 1 px box and overflow that
 * visually hidden text relies on, so on its own it would still take up its
 * full height in the page.
 */
export function ChartTable({ caption, head, rows }: ChartTableProps) {
  return (
    <div className="sr">
      <table className="chart-table">
        <caption>{caption}</caption>
        <thead>
          <tr>
            {head.map((h, i) => (
              <th key={i} scope="col">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, r) => (
            <tr key={r}>
              {row.map((cell, c) =>
                c === 0 ? (
                  <th key={c} scope="row">
                    {cell}
                  </th>
                ) : (
                  <td key={c}>{cell}</td>
                ),
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
