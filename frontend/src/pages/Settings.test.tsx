import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { expect, it, vi } from 'vitest'
import { auth } from '../api/client'
import Settings from './Settings'

const updateLanguage = vi.fn().mockResolvedValue(undefined)
const setUser = vi.fn()

vi.mock('../api/client', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../api/client')>()
  return { ...actual, auth: { ...actual.auth, updatePassword: vi.fn() } }
})

vi.mock('../api/session', () => ({
  useSession: () => ({
    user: {
      id: 1,
      username: 'luca',
      email: 'luca@example.test',
      role: 'USER',
      language: 'it',
    },
    setUser,
    updateLanguage,
  }),
}))

it('saves the language selected by the current user', async () => {
  render(
    <MemoryRouter>
      <Settings />
    </MemoryRouter>,
  )

  await userEvent.selectOptions(
    screen.getByRole('combobox', { name: 'Lingua' }),
    'en',
  )

  expect(updateLanguage).toHaveBeenCalledWith('en')
  expect(await screen.findByText('Lingua aggiornata.')).toBeInTheDocument()
})

it('checks password confirmation before calling the server', async () => {
  const user = userEvent.setup()
  render(
    <MemoryRouter>
      <Settings />
    </MemoryRouter>,
  )

  await user.type(screen.getByLabelText('Password attuale'), 'current password')
  await user.type(screen.getByLabelText('Nuova password'), 'new password 123')
  await user.type(
    screen.getByLabelText('Conferma nuova password'),
    'different password',
  )
  await user.click(screen.getByRole('button', { name: 'Aggiorna password' }))

  expect(
    await screen.findByText('Le nuove password non coincidono.'),
  ).toBeInTheDocument()
  expect(auth.updatePassword).not.toHaveBeenCalled()
})

it('changes the password and clears the local session', async () => {
  const user = userEvent.setup()
  vi.mocked(auth.updatePassword).mockResolvedValue(undefined)
  setUser.mockClear()
  render(
    <MemoryRouter>
      <Settings />
    </MemoryRouter>,
  )

  await user.type(screen.getByLabelText('Password attuale'), 'current password')
  await user.type(screen.getByLabelText('Nuova password'), 'new password 123')
  await user.type(
    screen.getByLabelText('Conferma nuova password'),
    'new password 123',
  )
  await user.click(screen.getByRole('button', { name: 'Aggiorna password' }))

  expect(auth.updatePassword).toHaveBeenCalledWith(
    'current password',
    'new password 123',
  )
  expect(setUser).toHaveBeenCalledWith(null)
})
