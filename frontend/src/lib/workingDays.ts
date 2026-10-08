export function workingDays(start: string, end: string): number {
  if (!start || !end || end < start) return 0;
  let total = 0;
  const cursor = new Date(`${start}T12:00:00+05:30`);
  const last = new Date(`${end}T12:00:00+05:30`);
  while (cursor <= last) {
    const day = cursor.getUTCDay();
    if (day !== 0 && day !== 6) total += 1;
    cursor.setUTCDate(cursor.getUTCDate() + 1);
  }
  return total;
}
