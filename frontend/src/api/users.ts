import { api } from './http'
import type { AdminUser, User } from './types'

export const auth = {
  me: () => api.get<User>('/api/auth/me'),
  login: (login: string, password: string) =>
    api.post<User>('/api/auth/login', { login, password }),
  register: (
    username: string,
    email: string,
    password: string,
    language: 'it' | 'en',
  ) =>
    api.post<User>('/api/auth/register', {
      username,
      email,
      password,
      language,
    }),
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
