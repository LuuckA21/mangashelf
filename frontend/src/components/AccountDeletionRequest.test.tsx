import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, auth } from '../api/client'
import AccountDeletionRequest from './AccountDeletionRequest'

vi.mock('../api/client', async (original) => {
  const actual = await original<typeof import('../api/client')>()
  return {
    ...actual,
    auth: {
      ...actual.auth,
      emailOptions: vi.fn(),
      requestAccountDeletion: vi.fn(),
      deleteAccount: vi.fn(),
    },
  }
})
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(auth.emailOptions).mockResolvedValue({ enabled: true })
})

it('requests confirmation without deleting the account and clears the password', async () => {
  render(<AccountDeletionRequest />)
  const password = await screen.findByLabelText(
    'Password attuale per eliminare l’account',
  )
  const user = userEvent.setup()
  await user.type(password, 'my-current-password')
  await user.click(
    screen.getByRole('button', { name: 'Invia conferma via email' }),
  )
  expect(auth.requestAccountDeletion).toHaveBeenCalledWith(
    'my-current-password',
  )
  expect(await screen.findByRole('status')).toHaveTextContent(
    'Nessun dato è stato eliminato',
  )
  expect(password).toHaveValue('')
  expect(auth.deleteAccount).not.toHaveBeenCalled()
})

it('explains why the last administrator cannot delete their account', async () => {
  vi.mocked(auth.requestAccountDeletion).mockRejectedValue(
    new ApiError(409, 'last_admin_required'),
  )
  render(<AccountDeletionRequest />)
  await userEvent.type(
    await screen.findByLabelText('Password attuale per eliminare l’account'),
    'my-current-password',
  )
  await userEvent.click(
    screen.getByRole('button', { name: 'Invia conferma via email' }),
  )
  expect(await screen.findByRole('alert')).toHaveTextContent(
    'ultimo amministratore attivo',
  )
  expect(auth.deleteAccount).not.toHaveBeenCalled()
})

it('does not offer deletion without email delivery', async () => {
  vi.mocked(auth.emailOptions).mockResolvedValue({ enabled: false })
  render(<AccountDeletionRequest />)
  expect(
    await screen.findByText(
      /Per eliminare l’account è necessario il servizio email/,
    ),
  ).toBeInTheDocument()
  expect(screen.queryByRole('button')).not.toBeInTheDocument()
})
