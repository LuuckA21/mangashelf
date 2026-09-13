import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, expect, it, vi } from 'vitest'
import { twoFactor } from '../api/users'
import TwoFactorSettings from './TwoFactorSettings'

const session = vi.hoisted(() => ({ setUser: vi.fn() }))
const qr = vi.hoisted(() => ({ toDataURL: vi.fn() }))
vi.mock('../api/session', () => ({ useSession: () => session }))
vi.mock('qrcode', () => ({ default: qr }))
vi.mock('../api/users', () => ({
  twoFactor: {
    status: vi.fn(),
    setup: vi.fn(),
    enable: vi.fn(),
    cancelSetup: vi.fn(),
    disable: vi.fn(),
    regenerate: vi.fn(),
  },
}))

beforeEach(() => {
  vi.resetAllMocks()
  qr.toDataURL.mockResolvedValue('data:image/png;base64,test')
  vi.mocked(twoFactor.status).mockResolvedValue({
    available: true,
    enabled: false,
    recoveryCodesRemaining: 0,
  })
})

it('requires proof before activation and acknowledgement before hiding recovery codes', async () => {
  const codes = ['01234567-89abcdef-01234567-89abcdef']
  vi.mocked(twoFactor.setup).mockResolvedValue({
    secret: 'SECRET123',
    uri: 'otpauth://totp/test?secret=SECRET123',
  })
  vi.mocked(twoFactor.enable).mockResolvedValue({
    recoveryCodes: codes,
    user: {
      id: 1,
      username: 'luca',
      email: 'luca@example.test',
      language: 'it',
      role: 'USER',
      twoFactorEnabled: true,
    },
  })
  render(<TwoFactorSettings />)
  const user = userEvent.setup()
  await user.click(await screen.findByRole('button', { name: 'Attiva 2FA' }))
  await user.type(
    screen.getByLabelText('Password attuale per la 2FA'),
    'current-password',
  )
  await user.click(screen.getByRole('button', { name: 'Continua' }))
  expect(await screen.findByText('SECRET123')).toBeInTheDocument()
  expect(twoFactor.enable).not.toHaveBeenCalled()
  expect(
    screen.queryByLabelText('Password attuale per la 2FA'),
  ).not.toBeInTheDocument()
  await user.type(
    screen.getByLabelText('Codice app o codice di recupero'),
    '123456',
  )
  await user.click(screen.getByRole('button', { name: 'Conferma attivazione' }))
  expect(await screen.findByText(codes[0])).toBeInTheDocument()
  expect(screen.queryByText('SECRET123')).not.toBeInTheDocument()
  expect(screen.getByRole('button', { name: 'Ho finito' })).toBeDisabled()
  await user.click(screen.getByLabelText('Ho salvato i codici di recupero'))
  await user.click(screen.getByRole('button', { name: 'Ho finito' }))
  expect(screen.queryByText(codes[0])).not.toBeInTheDocument()
  expect(session.setUser).toHaveBeenCalledWith(
    expect.objectContaining({ twoFactorEnabled: true }),
  )
})

it('does not offer activation when the server key is missing', async () => {
  vi.mocked(twoFactor.status).mockResolvedValue({
    available: false,
    enabled: false,
    recoveryCodesRemaining: 0,
  })
  render(<TwoFactorSettings />)
  expect(
    await screen.findByText(/L’attivazione della 2FA non è disponibile/),
  ).toBeInTheDocument()
  expect(
    screen.queryByRole('button', { name: 'Attiva 2FA' }),
  ).not.toBeInTheDocument()
})
