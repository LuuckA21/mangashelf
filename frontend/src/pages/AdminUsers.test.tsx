import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { adminAccounts, type AdminUser } from '../api/client'
import AdminUsers from './AdminUsers'

const accounts: AdminUser[] = [
  {
    id: 1,
    username: 'owner',
    email: 'owner@example.test',
    role: 'ADMIN',
    enabled: true,
    language: 'it',
    createdAt: '2026-08-20T10:00:00Z',
  },
  {
    id: 2,
    username: 'reader',
    email: 'reader@example.test',
    role: 'USER',
    enabled: true,
    language: 'en',
    createdAt: '2026-08-21T10:00:00Z',
  },
]

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    adminAccounts: { list: vi.fn(), update: vi.fn() },
  }
})

vi.mock('../api/session', () => ({
  useSession: () => ({ user: accounts[0] }),
}))

beforeEach(() => {
  vi.mocked(adminAccounts.list).mockReset().mockResolvedValue(accounts)
  vi.mocked(adminAccounts.update).mockReset()
})

it('keeps the current account locked and saves another user', async () => {
  const interaction = userEvent.setup()
  const updated = { ...accounts[1], role: 'ADMIN' as const, enabled: false }
  vi.mocked(adminAccounts.update).mockResolvedValue(updated)
  render(
    <MemoryRouter>
      <AdminUsers />
    </MemoryRouter>,
  )

  expect(await screen.findByText('reader')).toBeInTheDocument()
  expect(screen.getByRole('combobox', { name: 'Ruolo: owner' })).toBeDisabled()

  await interaction.selectOptions(
    screen.getByRole('combobox', { name: 'Ruolo: reader' }),
    'ADMIN',
  )
  await interaction.selectOptions(
    screen.getByRole('combobox', { name: 'Stato: reader' }),
    'disabled',
  )
  await interaction.click(
    screen.getAllByRole('button', { name: 'Salva utente' })[1],
  )

  expect(adminAccounts.update).toHaveBeenCalledWith(2, 'ADMIN', false)
  expect(await screen.findByText('Utente aggiornato.')).toBeInTheDocument()
})
