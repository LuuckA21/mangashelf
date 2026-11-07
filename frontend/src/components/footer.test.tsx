import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppRoutes } from '../App'
import { axe } from '../test/axe'
import SiteFooter from './SiteFooter'

beforeEach(() => {
  vi.stubEnv('VITE_APP_VERSION', '0.1.0')
  vi.stubEnv('VITE_APP_COMMIT', '')
  vi.stubGlobal(
    'fetch',
    vi.fn(async () => new Response('{"detail":"no"}', { status: 401 })),
  )
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllEnvs()
  vi.unstubAllGlobals()
})

function renderApp(path: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

const footerText = () =>
  render(
    <MemoryRouter>
      <SiteFooter />
    </MemoryRouter>,
  ).getByRole('contentinfo').textContent

describe('site footer', () => {
  it('isOnTheInnerPagesAndOnLogin', async () => {
    renderApp('/privacy')
    await screen.findByRole('heading', { level: 1, name: 'Privacy' })
    expect(
      within(screen.getByRole('contentinfo')).getByRole('link', {
        name: 'Privacy',
      }),
    ).toHaveAttribute('href', '/privacy')
  })

  it('isOnTheLoginPageWithoutAccessibilityViolations', async () => {
    const { container } = renderApp('/login')
    await screen.findByRole('heading', { level: 1, name: 'Accedi' })
    expect(screen.getByRole('contentinfo')).toHaveTextContent('MangaShelf')
    expect((await axe(container)).violations).toEqual([])
  })

  it('showsOnlyTheFirstYearIn2026', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2026-12-31T12:00:00'))
    expect(footerText()).toContain('© 2026 MangaShelf')
  })

  it('showsTheRangeFrom2027', () => {
    vi.useFakeTimers({ toFake: ['Date'] })
    vi.setSystemTime(new Date('2027-01-01T12:00:00'))
    expect(footerText()).toContain('© 2026–2027 MangaShelf')
  })

  it('showsTheVersionWithoutCommit', () => {
    const text = footerText()
    expect(text).toContain('v0.1.0')
    expect(text).not.toContain('(')
  })

  it('showsTheShortCommitWhenTheBuildHasOne', () => {
    vi.stubEnv('VITE_APP_COMMIT', '8a37a8c0123456789')
    expect(footerText()).toContain('v0.1.0 (8a37a8c)')
  })
})
