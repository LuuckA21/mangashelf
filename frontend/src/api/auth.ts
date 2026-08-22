import { api } from './http'
import type { User } from './types'

export const auth = {
  me: () => api.get<User>('/api/auth/me'),
  login: (login: string, password: string) =>
    api.post<User>('/api/auth/login', { login, password }),
  register: (username: string, email: string, password: string, language: 'it' | 'en') =>
    api.post<User>('/api/auth/register', { username, email, password, language }),
  logout: () => api.post<void>('/api/auth/logout'),
  updateLanguage: (language: 'it' | 'en') =>
    api.put<User>('/api/auth/me/language', { language }),
  updateProfile: (username: string, email: string, currentPassword: string) =>
    api.put<User>('/api/auth/me/profile', { username, email, currentPassword }),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.put<void>('/api/auth/me/password', { currentPassword, newPassword }),
  deleteAccount: (currentPassword: string) =>
    api.delete<void>('/api/auth/me', { currentPassword }),
}
