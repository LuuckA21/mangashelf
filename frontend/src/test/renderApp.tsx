import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { AppRoutes } from '../App'

/** The whole app at `path`, with a fresh query cache and no retries; `extra` renders beside it. */
export function renderApp(path: string, extra?: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const view = render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[path]}>
        <AppRoutes />
        {extra}
      </MemoryRouter>
    </QueryClientProvider>,
  )
  return { ...view, client }
}
