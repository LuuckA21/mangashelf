import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import Settings from './Settings'

const updateLanguage = vi.fn().mockResolvedValue(undefined)
const updateProfile = vi.fn().mockResolvedValue(undefined)
const changePassword = vi.fn().mockResolvedValue(undefined)
const deleteAccount = vi.fn().mockResolvedValue(undefined)

vi.mock('../api/session', () => ({
  useSession: () => ({
    user: {
      id: 1, username: 'luca', email: 'luca@example.test', role: 'USER', language: 'it',
    },
    setUser: vi.fn(),
    updateLanguage,
    updateProfile,
    changePassword,
    deleteAccount,
  }),
}))

function renderSettings() {
  render(<MemoryRouter><Settings /></MemoryRouter>)
}

describe('Settings', () => {
  beforeEach(() => {
    updateLanguage.mockClear()
    updateProfile.mockClear()
    changePassword.mockClear()
    deleteAccount.mockClear()
  })

  it('saves the language selected by the current user', async () => {
    renderSettings()

    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Lingua' }), 'en')

    expect(updateLanguage).toHaveBeenCalledWith('en')
    expect(await screen.findByText('Lingua aggiornata.')).toBeInTheDocument()
  })

  it('updates username and email only with the current password', async () => {
    const user = userEvent.setup()
    renderSettings()

    const username = screen.getByLabelText('Username')
    await user.clear(username)
    await user.type(username, 'nuovo.nome')
    const email = screen.getByLabelText('Email')
    await user.clear(email)
    await user.type(email, 'nuovo@example.test')
    await user.type(screen.getAllByLabelText('Password attuale')[0], 'current secret')
    await user.click(screen.getByRole('button', { name: 'Salva profilo' }))

    expect(updateProfile).toHaveBeenCalledWith(
      'nuovo.nome', 'nuovo@example.test', 'current secret',
    )
    expect(await screen.findByText('Profilo aggiornato.')).toBeInTheDocument()
  })

  it('does not submit two different new passwords', async () => {
    const user = userEvent.setup()
    renderSettings()

    await user.type(screen.getAllByLabelText('Password attuale')[1], 'current secret')
    await user.type(screen.getByLabelText('Nuova password (min. 10 caratteri)'), 'first password')
    await user.type(screen.getByLabelText('Conferma nuova password'), 'second password')
    await user.click(screen.getByRole('button', { name: 'Cambia password' }))

    expect(changePassword).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent('non coincidono')
  })

  it('requires username confirmation before deleting the account', async () => {
    const user = userEvent.setup()
    renderSettings()

    const deleteButton = screen.getByRole('button', {
      name: 'Elimina definitivamente il mio account',
    })
    expect(deleteButton).toBeDisabled()

    await user.type(screen.getByLabelText('Digita il tuo username per confermare'), 'luca')
    await user.type(screen.getAllByLabelText('Password attuale')[2], 'current secret')
    expect(deleteButton).toBeEnabled()
    await user.click(deleteButton)

    expect(deleteAccount).toHaveBeenCalledWith('current secret')
  })
})
