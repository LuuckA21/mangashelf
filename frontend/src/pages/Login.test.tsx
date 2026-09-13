import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, auth, type User } from '../api/client'
import Login from './Login'

const session = vi.hoisted(() => ({ setUser: vi.fn() }))

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return {
    ...actual,
    auth: {
      ...actual.auth,
      login: vi.fn(),
      emailOptions: vi.fn(),
      twoFactorLogin: vi.fn(),
      cancelTwoFactorLogin: vi.fn(),
    },
  }
})

vi.mock('../api/session', () => ({
  useSession: () => ({ user: null, loading: false, setUser: session.setUser }),
}))

const login = vi.mocked(auth.login)

function renderLogin() {
  render(
    <MemoryRouter>
      <Login />
    </MemoryRouter>,
  )
}

async function fillAndSubmit(password: string) {
  const user = userEvent.setup()
  await user.type(
    screen.getByRole('textbox', { name: 'Username o email' }),
    'luca',
  )
  await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Accedi' }))
}

describe('Login', () => {
  beforeEach(() => {
    login.mockReset()
    vi.mocked(auth.emailOptions).mockResolvedValue({ enabled: true })
    session.setUser.mockReset()
  })

  it('shows the stable message returned for invalid credentials', async () => {
    login.mockRejectedValue(new ApiError(401, 'invalid_credentials'))
    renderLogin()

    await fillAndSubmit('wrong password')

    expect(
      await screen.findByText('Credenziali non valide.'),
    ).toBeInTheDocument()
    expect(session.setUser).not.toHaveBeenCalled()
  })

  it('stores the authenticated user in the shared session', async () => {
    const authenticated: User = {
      id: 7,
      username: 'luca',
      email: 'luca@example.test',
      role: 'USER',
      language: 'it',
    }
    login.mockResolvedValue(authenticated)
    renderLogin()

    await fillAndSubmit('correct password')

    expect(login).toHaveBeenCalledWith('luca', 'correct password')
    expect(session.setUser).toHaveBeenCalledWith(authenticated)
  })

  it('does not authenticate or retain the password until the second factor succeeds', async () => {
    login.mockResolvedValue({ twoFactorRequired: true })
    vi.mocked(auth.twoFactorLogin).mockResolvedValue({
      id: 7,
      username: 'luca',
      email: 'luca@example.test',
      role: 'USER',
      language: 'it',
      twoFactorEnabled: true,
    })
    renderLogin()
    await fillAndSubmit('correct password')
    expect(session.setUser).not.toHaveBeenCalled()
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument()
    const code = await screen.findByLabelText('Codice app o codice di recupero')
    await userEvent.type(code, '123456')
    await userEvent.click(screen.getByRole('button', { name: 'Accedi' }))
    expect(auth.twoFactorLogin).toHaveBeenCalledWith('123456')
    expect(session.setUser).toHaveBeenCalledWith(
      expect.objectContaining({ twoFactorEnabled: true }),
    )
  })

  it('returns to an empty password form when the challenge expires', async () => {
    login.mockResolvedValue({ twoFactorRequired: true })
    vi.mocked(auth.twoFactorLogin).mockRejectedValue(
      new ApiError(401, 'two_factor_challenge_expired'),
    )
    renderLogin()
    await fillAndSubmit('correct password')
    await userEvent.type(
      await screen.findByLabelText('Codice app o codice di recupero'),
      '123456',
    )
    await userEvent.click(screen.getByRole('button', { name: 'Accedi' }))
    expect(await screen.findByLabelText('Password')).toHaveValue('')
    expect(session.setUser).not.toHaveBeenCalled()
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Verifica scaduta',
    )
  })
})
