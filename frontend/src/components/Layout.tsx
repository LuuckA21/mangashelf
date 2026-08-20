import { NavLink, Link } from 'react-router-dom'
import { auth } from '../api/client'
import { useSession } from '../api/session'
import type { ReactNode } from 'react'

export default function Layout({ children }: { children: ReactNode }) {
  const { user, setUser } = useSession()

  async function handleLogout() {
    await auth.logout()
    setUser(null)
  }

  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <Link to="/" className="brand">MangaShelf</Link>
          <nav aria-label="Navigazione principale">
            <NavLink to="/" end>Catalogo</NavLink>
            <NavLink to="/collection">La mia collezione</NavLink>
            <NavLink to="/purchases">Acquisti</NavLink>
          </nav>
          <span className="spacer" />
          <span className="muted" style={{ fontSize: 14 }}>{user?.username}</span>
          <button className="quiet" onClick={handleLogout}>Esci</button>
        </div>
      </header>
      <main className="page">{children}</main>
    </>
  )
}
