/** Format ISO timestamp to readable format: 2026-05-30 22:46:29 */
export function fmtDate(iso?: string): string {
  if (!iso) return '';
  return iso.replace('T', ' ');
}
