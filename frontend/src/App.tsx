import { Navigate, Route, Routes } from 'react-router-dom'
import { useSession } from './api/session'
import Library from './pages/Library'
import Login from './pages/Login'
import MangaDetail from './pages/MangaDetail'
import MyCollection from './pages/MyCollection'
import PurchaseDetail from './pages/PurchaseDetail'
import Purchases from './pages/Purchases'
import Register from './pages/Register'
import SeriesDetail from './pages/SeriesDetail'

export default function App() {
  const { user, loading } = useSession()

  // Rendering routes before the session check resolves would flash the login
  // screen at users who are in fact already signed in.
  if (loading) return null

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <Routes>
      <Route path="/" element={<Library />} />
      <Route path="/manga/:id" element={<MangaDetail />} />
      <Route path="/edition/:id" element={<SeriesDetail />} />
      <Route path="/collection" element={<MyCollection />} />
      <Route path="/purchases" element={<Purchases />} />
      <Route path="/purchases/:id" element={<PurchaseDetail />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
