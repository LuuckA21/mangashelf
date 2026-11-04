/** Missing volume numbers as ranges, e.g. `3, 7–9`; empty when none is missing. */
export function formatMissing(owned: number[], volumeCount: number): string {
  const have = new Set(owned)
  const parts: string[] = []
  for (let n = 1; n <= volumeCount; n++) {
    if (have.has(n)) continue
    let end = n
    while (end < volumeCount && !have.has(end + 1)) end++
    parts.push(end === n ? `${n}` : `${n}–${end}`)
    n = end
  }
  return parts.join(', ')
}
