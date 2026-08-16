import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, auth } from '../api/client'
import { useSession } from '../api/session'

const MESSAGES: Record<string, string> = {
  invalid_credentials: 'Credenziali non valide.',
  account_disabled: 'Questo account è disattivato.',
  too_many_attempts: 'Troppi tentativi falliti. Riprova fra un quarto d’ora.',
}

export default function Login() {
  const { setUser } = useSession()
  const [login, setLogin] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setUser(await auth.login(login, password))
    } catch (e) {
      setError(
        e instanceof ApiError
          ? (MESSAGES[e.code] ?? 'Accesso non riuscito. Riprova.')
          : 'Server non raggiungibile.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>MangaShelf</h1>
        <p className="subtitle">Accedi alla tua collezione.</p>

        {error && <div className="error">{error}</div>}

        <div className="field">
          <label htmlFor="login">Username o email</label>
          <input
            id="login"
            autoComplete="username"
            value={login}
            onChange={(e) => setLogin(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        <button type="submit" disabled={busy}>
          {busy ? 'Accesso…' : 'Accedi'}
        </button>

        <p className="switch">
          Non hai un account? <Link to="/register">Registrati</Link>
        </p>
      </form>
    </div>
  )
}
