import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, auth } from '../api/client'
import DeleteAccount from './DeleteAccount'

const session = vi.hoisted(() => ({ setUser: vi.fn() }))
vi.mock('../api/session', () => ({ useSession: () => session }))
vi.mock('../api/client', async (original) => {
  const actual = await original<typeof import('../api/client')>()
  return {
    ...actual,
    auth: {
      ...actual.auth,
      accountDeletionDetails: vi.fn(),
      deleteAccount: vi.fn(),
    },
  }
})
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(auth.accountDeletionDetails).mockResolvedValue({
    username: 'reader',
    email: 'reader@example.test',
  })
})
const token = 'a'.repeat(43)
function open(path = `/delete-account#token=${token}`) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/delete-account" element={<DeleteAccount />} />
        <Route path="/login" element={<p>Login page</p>} />
      </Routes>
    </MemoryRouter>,
  )
}

it('shows the target account and only deletes after explicit confirmation', async () => {
  open()
  expect(await screen.findByText('reader@example.test')).toBeInTheDocument()
  expect(auth.accountDeletionDetails).toHaveBeenCalledWith(token)
  expect(auth.deleteAccount).not.toHaveBeenCalled()
  const button = screen.getByRole('button', {
    name: 'Elimina definitivamente il mio account',
  })
  expect(button).toBeDisabled()
  await userEvent.click(screen.getByRole('checkbox'))
  expect(button).toBeEnabled()
  expect(auth.deleteAccount).not.toHaveBeenCalled()
  await userEvent.click(button)
  expect(auth.deleteAccount).toHaveBeenCalledWith(token)
  expect(session.setUser).toHaveBeenCalledWith(null)
  expect(await screen.findByText('Login page')).toBeInTheDocument()
})

it('does not offer confirmation for an expired link', async () => {
  vi.mocked(auth.accountDeletionDetails).mockRejectedValue(
    new ApiError(400, 'deletion_token_invalid'),
  )
  open()
  expect(await screen.findByRole('alert')).toHaveTextContent('scaduto')
  expect(screen.queryByRole('checkbox')).not.toBeInTheDocument()
  expect(auth.deleteAccount).not.toHaveBeenCalled()
})

it('rejects a link belonging to another logged-in account', async () => {
  vi.mocked(auth.accountDeletionDetails).mockRejectedValue(
    new ApiError(403, 'deletion_wrong_account'),
  )
  open()
  expect(await screen.findByRole('alert')).toHaveTextContent('un altro account')
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})

it('does not request account details when the link is missing', () => {
  open('/delete-account')
  expect(screen.getByRole('alert')).toHaveTextContent('non è valido')
  expect(auth.accountDeletionDetails).not.toHaveBeenCalled()
})
