import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { lazy, Suspense } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router'
import Layout from './components/Layout'
import NotFoundPage from './components/NotFoundPage'
import RequireAuth from './features/auth/RequireAuth'
import { t } from './i18n/t'

const LoginPage = lazy(() => import('./features/auth/LoginPage'))
const ChangePasswordPage = lazy(
  () => import('./features/auth/ChangePasswordPage'),
)
const CollectionPage = lazy(
  () => import('./features/collection/CollectionPage'),
)
const EditionPage = lazy(() => import('./features/collection/EditionPage'))
const CatalogPage = lazy(() => import('./features/catalog/CatalogPage'))
const SeriesPage = lazy(() => import('./features/catalog/SeriesPage'))
const UsersPage = lazy(() => import('./features/users/UsersPage'))
const AccountPage = lazy(() => import('./features/account/AccountPage'))
const PrivacyPage = lazy(() => import('./features/account/PrivacyPage'))

export function AppRoutes() {
  return (
    <Suspense fallback={<p className="app-state">{t('common.loading')}</p>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/change-password" element={<ChangePasswordPage />} />
        <Route element={<Layout />}>
          <Route path="/privacy" element={<PrivacyPage />} />
          <Route element={<RequireAuth />}>
            <Route index element={<CollectionPage />} />
            <Route path="/editions/:id" element={<EditionPage />} />
            <Route path="/catalog" element={<CatalogPage />} />
            <Route path="/series/:id" element={<SeriesPage />} />
            <Route path="/account" element={<AccountPage />} />
          </Route>
          <Route element={<RequireAuth admin />}>
            <Route path="/admin/users" element={<UsersPage />} />
          </Route>
          <Route path="*" element={<NotFoundPage />} />
        </Route>
      </Routes>
    </Suspense>
  )
}

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: false } },
})

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AppRoutes />
      </BrowserRouter>
    </QueryClientProvider>
  )
}
