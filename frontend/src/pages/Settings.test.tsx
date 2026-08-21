import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { expect, it, vi } from 'vitest'
import Settings from './Settings'

const updateLanguage = vi.fn().mockResolvedValue(undefined)

vi.mock('../api/session', () => ({
  useSession: () => ({
    user: {
      id: 1, username: 'luca', email: 'luca@example.test', role: 'USER', language: 'it',
    },
    setUser: vi.fn(),
    updateLanguage,
  }),
}))

it('saves the language selected by the current user', async () => {
  render(<MemoryRouter><Settings /></MemoryRouter>)

  await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Lingua' }), 'en')

  expect(updateLanguage).toHaveBeenCalledWith('en')
  expect(await screen.findByText('Lingua aggiornata.')).toBeInTheDocument()
})
