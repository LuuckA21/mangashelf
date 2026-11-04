import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { t } from '../i18n/t'
import { ApiError, api } from './client'

const fetchMock = vi.fn()
const assign = vi.fn()

function respond(status: number, body?: unknown) {
  fetchMock.mockResolvedValue(
    new Response(body === undefined ? null : JSON.stringify(body), { status }),
  )
}

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('location', { assign })
})

afterEach(() => {
  vi.unstubAllGlobals()
  fetchMock.mockReset()
  assign.mockReset()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
})

function sentHeaders() {
  return fetchMock.mock.calls.at(-1)![1].headers as Headers
}

describe('api', () => {
  it('sendsXsrfHeaderOnPost', async () => {
    document.cookie = 'XSRF-TOKEN=abc'
    respond(200, {})
    await api('/things', { method: 'POST', body: '{}' })
    expect(sentHeaders().get('X-XSRF-TOKEN')).toBe('abc')
    expect(sentHeaders().get('Content-Type')).toBe('application/json')

    fetchMock.mockClear()
    respond(200, {})
    await api('/things')
    expect(sentHeaders().has('X-XSRF-TOKEN')).toBe(false)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/v1/things')
  })

  it('fetchesXsrfTokenFirstWhenMissing', async () => {
    fetchMock.mockImplementation(async (url: string) => {
      if (url === '/api/v1/auth/me') document.cookie = 'XSRF-TOKEN=fresh'
      return new Response('{}', { status: 200 })
    })
    await api('/auth/login', { method: 'POST', body: '{}' })
    expect(fetchMock.mock.calls.map((c) => c[0])).toEqual([
      '/api/v1/auth/me',
      '/api/v1/auth/login',
    ])
    expect(sentHeaders().get('X-XSRF-TOKEN')).toBe('fresh')
  })

  it('sendsNoContentTypeForFormData', async () => {
    respond(200, {})
    await api('/things', { method: 'POST', body: new FormData() })
    expect(sentHeaders().has('Content-Type')).toBe(false)
  })

  it('throwsApiErrorFromProblemDetails', async () => {
    respond(400, {
      status: 400,
      detail: 'Dati non validi',
      errors: [{ field: 'title', message: 'obbligatorio' }],
    })
    const error = (await api('/things').catch((e) => e)) as ApiError
    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.detail).toBe('Dati non validi')
    expect(error.errors[0].field).toBe('title')
  })

  it('redirectsToLoginOn401', async () => {
    respond(401, { status: 401, detail: 'x' })
    await expect(api('/collection')).rejects.toBeInstanceOf(ApiError)
    expect(assign).toHaveBeenCalledWith('/login')
  })

  it.each(['/auth/me', '/auth/login'])(
    'does not redirect on 401 from %s',
    async (path) => {
      respond(401, { status: 401, detail: 'Credenziali non valide' })
      const error = (await api(path).catch((e) => e)) as ApiError
      expect(error.status).toBe(401)
      expect(error.detail).toBe('Credenziali non valide')
      expect(assign).not.toHaveBeenCalled()
    },
  )

  it('doesNotRedirectOn401WhileOnLoginPage', async () => {
    vi.stubGlobal('location', { assign, pathname: '/login' })
    respond(401, { status: 401, detail: 'x' })
    await expect(api('/collection')).rejects.toBeInstanceOf(ApiError)
    expect(assign).not.toHaveBeenCalled()
  })

  it('fallsBackToGenericMessageWhenDetailAndStatusTextAreEmpty', async () => {
    respond(500) // no body, empty statusText: what HTTP/2 gives
    const error = (await api('/things').catch((e) => e)) as ApiError
    expect(error.detail).toBe(t('error.generic'))
  })

  it('returnsUndefinedOn204', async () => {
    respond(204)
    expect(await api('/things', { method: 'DELETE' })).toBeUndefined()
  })
})
