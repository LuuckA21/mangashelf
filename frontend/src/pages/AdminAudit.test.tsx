import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { adminAudit, type AdminAuditEvent } from '../api/client'
import AdminAudit from './AdminAudit'

const events: AdminAuditEvent[] = [
  {
    id: 1,
    actorUserId: 1,
    actorUsername: 'owner',
    targetUserId: 2,
    targetUsername: 'reader',
    action: 'STATUS_CHANGED',
    oldValue: 'ENABLED',
    newValue: 'DISABLED',
    createdAt: '2026-09-11T18:00:00Z',
  },
]

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    adminAudit: { list: vi.fn() },
  }
})

vi.mock('../api/session', () => ({
  useSession: () => ({
    user: { id: 1, username: 'owner', role: 'ADMIN' },
  }),
}))

beforeEach(() => {
  vi.mocked(adminAudit.list).mockReset().mockResolvedValue(events)
})

it('renders translated account audit details', async () => {
  render(
    <MemoryRouter>
      <AdminAudit />
    </MemoryRouter>,
  )

  expect(await screen.findByText('reader')).toBeInTheDocument()
  expect(screen.getAllByText('owner')).toHaveLength(2)
  expect(screen.getByText('Stato modificato')).toBeInTheDocument()
  expect(screen.getByText('Attivo')).toBeInTheDocument()
  expect(screen.getByText('Disattivato')).toBeInTheDocument()
})
