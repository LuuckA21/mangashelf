import { describe, expect, it } from 'vitest'
import { formatMissing } from './formatMissing'

describe('formatMissing', () => {
  it.each([
    [[], 3, '1–3'],
    [[1, 2, 3], 3, ''],
    [[1, 2, 4, 5, 6, 10], 10, '3, 7–9'],
    [[2], 3, '1, 3'],
  ])('%j of %i is %j', (owned, count, expected) => {
    expect(formatMissing(owned, count)).toBe(expected)
  })
})
