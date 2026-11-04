import { t } from '../i18n/t'

export class ApiError extends Error {
  status: number
  detail: string
  errors: { field: string; message: string }[]

  constructor(
    status: number,
    detail: string,
    errors: { field: string; message: string }[] = [],
  ) {
    super(detail)
    this.status = status
    this.detail = detail
    this.errors = errors
  }
}

function csrfToken(): string | undefined {
  return document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1]
}

// A 401 here is an answer to show, not a lost session.
const NO_REDIRECT_ON_401 = ['/auth/me', '/auth/login']

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  // FormData sets its own multipart boundary header.
  if (
    init.body &&
    !(init.body instanceof FormData) &&
    !headers.has('Content-Type')
  )
    headers.set('Content-Type', 'application/json')
  // The page may not come from Spring (Vite in dev): any GET hands out the token.
  if (method !== 'GET' && !csrfToken())
    await fetch('/api/v1/auth/me', { credentials: 'same-origin' })
  const token = csrfToken()
  if (method !== 'GET' && token)
    headers.set('X-XSRF-TOKEN', decodeURIComponent(token))

  const res = await fetch(`/api/v1${path}`, {
    ...init,
    method,
    headers,
    credentials: 'same-origin',
  })

  // On /login already, redirecting again would loop.
  if (
    res.status === 401 &&
    !NO_REDIRECT_ON_401.includes(path) &&
    window.location.pathname !== '/login'
  ) {
    window.location.assign('/login')
    throw new ApiError(401, 'Not logged in')
  }
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}))
    throw new ApiError(
      res.status,
      problem.detail || res.statusText || t('error.generic'),
      problem.errors,
    )
  }
  return res.status === 204 ? (undefined as T) : res.json()
}

/** The message the server attached to one field of a 400, if any. */
export function fieldMessage(error: unknown, field: string) {
  return error instanceof ApiError
    ? error.errors.find((e) => e.field === field)?.message
    : undefined
}
