import { api } from './http'
import type { AdminAuditEvent, AdminUser, User } from './types'

export const auth = {
  requestAccountDeletion: (currentPassword: string) =>
    api.post<void>('/api/auth/me/deletion-request', { currentPassword }),
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
    api.post<User>('/api/auth/login', { login, password }),
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
  updatePassword: (currentPassword: string, newPassword: string) =>
    api.put<void>('/api/auth/me/password', { currentPassword, newPassword }),
}

export const adminAccounts = {
  list: () => api.get<AdminUser[]>('/api/admin/users'),
  update: (id: number, role: User['role'], enabled: boolean) =>
    api.put<AdminUser>(`/api/admin/users/${id}`, { role, enabled }),
}

export const adminAudit = {
  list: () => api.get<AdminAuditEvent[]>('/api/admin/audit'),
}
