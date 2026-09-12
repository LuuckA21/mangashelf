import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { ApiError, auth } from '../api/client'
import AccountEmail from './AccountEmail'
import Register from './Register'

const session = vi.hoisted(() => ({ setUser: vi.fn() }))
vi.mock('../api/session', () => ({ useSession: () => session }))
vi.mock('../api/client', async (original) => {
  const actual = await original<typeof import('../api/client')>()
  return {
    ...actual,
    auth: {
      ...actual.auth,
      emailOptions: vi.fn(),
      register: vi.fn(),
      login: vi.fn(),
      verifyEmail: vi.fn(),
      resetPassword: vi.fn(),
      requestPasswordReset: vi.fn(),
    },
  }
})
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(auth.emailOptions).mockResolvedValue({ enabled: true })
})
const token = 'a'.repeat(43)

it('requires an explicit click before consuming an email confirmation link', async () => {
  render(
    <MemoryRouter initialEntries={[`/verify-email#token=${token}`]}>
      <AccountEmail mode="verify" />
    </MemoryRouter>,
  )
  expect(auth.verifyEmail).not.toHaveBeenCalled()
  await userEvent.click(
    screen.getByRole('button', { name: 'Conferma indirizzo email' }),
  )
  expect(auth.verifyEmail).toHaveBeenCalledWith(token)
  expect(await screen.findByRole('status')).toHaveTextContent(
    'Indirizzo confermato',
  )
})

it('shows expired-link feedback with a route to request a replacement', async () => {
  vi.mocked(auth.verifyEmail).mockRejectedValue(
    new ApiError(400, 'email_token_invalid'),
  )
  render(
    <MemoryRouter initialEntries={[`/verify-email#token=${token}`]}>
      <AccountEmail mode="verify" />
    </MemoryRouter>,
  )
  await userEvent.click(
    screen.getByRole('button', { name: 'Conferma indirizzo email' }),
  )
  expect(await screen.findByRole('alert')).toHaveTextContent('Link non valido')
  expect(
    screen.getByRole('link', { name: 'Richiedi un nuovo link' }),
  ).toHaveAttribute('href', '/resend-verification')
})

it('rejects password confirmation mismatch, then resets and returns to login', async () => {
  render(
    <MemoryRouter initialEntries={[`/reset-password#token=${token}`]}>
      <Routes>
        <Route path="/reset-password" element={<AccountEmail mode="reset" />} />
        <Route path="/login" element={<p>Login page</p>} />
      </Routes>
    </MemoryRouter>,
  )
  const user = userEvent.setup()
  await user.type(
    screen.getByLabelText('Password (min. 10 caratteri)'),
    'new-password-123',
  )
  await user.type(
    screen.getByLabelText('Conferma nuova password'),
    'wrong-password-123',
  )
  await user.click(screen.getByRole('button', { name: 'Reimposta password' }))
  expect(screen.getByRole('alert')).toHaveTextContent(
    'Le password non coincidono',
  )
  expect(auth.resetPassword).not.toHaveBeenCalled()
  await user.clear(screen.getByLabelText('Conferma nuova password'))
  await user.type(
    screen.getByLabelText('Conferma nuova password'),
    'new-password-123',
  )
  await user.click(screen.getByRole('button', { name: 'Reimposta password' }))
  expect(auth.resetPassword).toHaveBeenCalledWith(token, 'new-password-123')
  expect(session.setUser).toHaveBeenCalledWith(null)
  expect(await screen.findByText('Login page')).toBeInTheDocument()
})

it('gives generic feedback for a password recovery request', async () => {
  render(
    <MemoryRouter>
      <AccountEmail mode="forgot" />
    </MemoryRouter>,
  )
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email'), 'missing@example.test')
  await user.click(screen.getByRole('button', { name: 'Invia email' }))
  expect(auth.requestPasswordReset).toHaveBeenCalledWith('missing@example.test')
  expect(await screen.findByRole('status')).toHaveTextContent(
    'Se l’indirizzo corrisponde',
  )
})

it('does not allow a request when email is disabled', async () => {
  vi.mocked(auth.emailOptions).mockResolvedValue({ enabled: false })
  render(
    <MemoryRouter>
      <AccountEmail mode="forgot" />
    </MemoryRouter>,
  )
  expect(await screen.findByRole('status')).toHaveTextContent(
    'Il servizio email non è disponibile',
  )
  expect(
    screen.queryByRole('button', { name: 'Invia email' }),
  ).not.toBeInTheDocument()
})

it('does not automatically sign in an account awaiting verification', async () => {
  vi.mocked(auth.register).mockResolvedValue({
    id: 4,
    username: 'reader',
    email: 'reader@example.test',
    language: 'it',
    role: 'USER',
    emailVerificationRequired: true,
  })
  render(
    <MemoryRouter>
      <Register />
    </MemoryRouter>,
  )
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Username'), 'reader')
  await user.type(screen.getByLabelText('Email'), 'reader@example.test')
  await user.type(
    screen.getByLabelText('Password (min. 10 caratteri)'),
    'new-password-123',
  )
  await user.click(screen.getByRole('button', { name: 'Crea account' }))
  expect(await screen.findByRole('status')).toHaveTextContent('Account creato')
  expect(auth.login).not.toHaveBeenCalled()
  expect(session.setUser).not.toHaveBeenCalled()
})
