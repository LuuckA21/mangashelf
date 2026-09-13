import { api } from './http'
import type { AdminAuditEvent, AdminUser, User } from './types'

export const auth = {
  requestAccountDeletion: (currentPassword: string, code?: string) =>
    api.post<void>('/api/auth/me/deletion-request', { currentPassword, code }),
  accountDeletionDetails: (token: string) =>
    api.post<{ username: string; email: string }>(
      '/api/auth/deletion-details',
      { token },
    ),
  deleteAccount: (token: string) =>
    api.post<void>('/api/auth/delete-account', { token, confirmed: true }),
  emailOptions: () => api.get<{ enabled: boolean }>('/api/auth/email-options'),
  requestPasswordReset: (email: string) =>
    api.post<void>('/api/auth/forgot-password', { email }),
  resendVerification: (email: string) =>
    api.post<void>('/api/auth/resend-verification', { email }),
  verifyEmail: (token: string) =>
    api.post<void>('/api/auth/verify-email', { token }),
  resetPassword: (token: string, newPassword: string) =>
    api.post<void>('/api/auth/reset-password', { token, newPassword }),
  me: () => api.get<User>('/api/auth/me'),
  login: (login: string, password: string) =>
    api.post<User | { twoFactorRequired: true }>('/api/auth/login', {
      login,
      password,
    }),
  twoFactorLogin: (code: string) =>
    api.post<User>('/api/auth/2fa/login', { code }),
  cancelTwoFactorLogin: () => api.post<void>('/api/auth/2fa/cancel'),
  register: (
    username: string,
    email: string,
    password: string,
    language: 'it' | 'en',
  ) =>
    api.post<User & { emailVerificationRequired: boolean }>(
      '/api/auth/register',
      {
        username,
        email,
        password,
        language,
      },
    ),
  logout: () => api.post<void>('/api/auth/logout'),
  updateLanguage: (language: 'it' | 'en') =>
    api.put<User>('/api/auth/me/language', { language }),
  updatePassword: (
    currentPassword: string,
    newPassword: string,
    code?: string,
  ) =>
    api.put<void>('/api/auth/me/password', {
      currentPassword,
      newPassword,
      code,
    }),
}

export interface TwoFactorStatus {
  available: boolean
  enabled: boolean
  recoveryCodesRemaining: number
}

export const twoFactor = {
  status: () => api.get<TwoFactorStatus>('/api/auth/2fa'),
  setup: (currentPassword: string) =>
    api.post<{ secret: string; uri: string }>('/api/auth/2fa/setup', {
      currentPassword,
    }),
  cancelSetup: () => api.delete<void>('/api/auth/2fa/setup'),
  enable: (code: string) =>
    api.post<{ recoveryCodes: string[]; user: User }>('/api/auth/2fa/enable', {
      code,
    }),
  disable: (currentPassword: string, code: string) =>
    api.post<User>('/api/auth/2fa/disable', { currentPassword, code }),
  regenerate: (currentPassword: string, code: string) =>
    api.post<{ recoveryCodes: string[]; user: User }>(
      '/api/auth/2fa/recovery-codes',
      { currentPassword, code },
    ),
}

export const adminAccounts = {
  list: () => api.get<AdminUser[]>('/api/admin/users'),
  update: (id: number, role: User['role'], enabled: boolean) =>
    api.put<AdminUser>(`/api/admin/users/${id}`, { role, enabled }),
}

export const adminAudit = {
  list: () => api.get<AdminAuditEvent[]>('/api/admin/audit'),
}
