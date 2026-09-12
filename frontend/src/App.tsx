import { Navigate, Route, Routes } from 'react-router-dom'
import { useSession } from './api/session'
import Library from './pages/Library'
import Login from './pages/Login'
import AccountEmail from './pages/AccountEmail'
import MangaDetail from './pages/MangaDetail'
import MyCollection from './pages/MyCollection'
import PurchaseDetail from './pages/PurchaseDetail'
import Purchases from './pages/Purchases'
import Register from './pages/Register'
import SeriesDetail from './pages/SeriesDetail'
import Settings from './pages/Settings'
import AdminUsers from './pages/AdminUsers'
import AdminAudit from './pages/AdminAudit'
import { useI18n } from './i18n'

export default function App() {
  const { user, loading, unavailable, retry } = useSession()
  const { t } = useI18n()

  // Rendering routes before the session check resolves would flash the login
  // screen at users who are in fact already signed in.
  if (loading) {
    return (
      <div className="app-state" role="status">
        {t('session.loading')}
      </div>
    )
  }

  if (unavailable) {
    return (
      <div className="app-state">
        <h1>{t('session.unavailableTitle')}</h1>
        <p>{t('session.unavailableBody')}</p>
        <button type="button" onClick={retry}>
          {t('common.retry')}
        </button>
      </div>
    )
  }

  if (!user) {
    return (
      <Routes>
        <Route
          path="/forgot-password"
          element={<AccountEmail mode="forgot" />}
        />
        <Route
          path="/resend-verification"
          element={<AccountEmail mode="resend" />}
        />
        <Route path="/verify-email" element={<AccountEmail mode="verify" />} />
        <Route path="/reset-password" element={<AccountEmail mode="reset" />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <Routes>
      <Route path="/verify-email" element={<AccountEmail mode="verify" />} />
      <Route path="/reset-password" element={<AccountEmail mode="reset" />} />
      <Route path="/" element={<Library />} />
      <Route path="/manga/:id" element={<MangaDetail />} />
      <Route path="/edition/:id" element={<SeriesDetail />} />
      <Route path="/collection" element={<MyCollection />} />
      <Route path="/purchases" element={<Purchases />} />
      <Route path="/purchases/:id" element={<PurchaseDetail />} />
      <Route path="/settings" element={<Settings />} />
      <Route
        path="/admin/users"
        element={
          user.role === 'ADMIN' ? <AdminUsers /> : <Navigate to="/" replace />
        }
      />
      <Route
        path="/admin/audit"
        element={
          user.role === 'ADMIN' ? <AdminAudit /> : <Navigate to="/" replace />
        }
      />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
