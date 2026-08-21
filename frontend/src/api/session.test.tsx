import { describe, expect, it } from 'vitest'
import { ApiError } from './client'
import { isExpiredSession } from './session'

describe('session restore errors', () => {
  it('treats only an actual 401 as an expired session', () => {
    expect(isExpiredSession(new ApiError(401, 'unauthorized'))).toBe(true)
    expect(isExpiredSession(new ApiError(503, 'unavailable'))).toBe(false)
    expect(isExpiredSession(new Error('network failure'))).toBe(false)
  })
})
