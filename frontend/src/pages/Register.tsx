import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, auth } from '../api/client'
import { useSession } from '../api/session'

const MESSAGES: Record<string, string> = {
  username_taken: 'Questo username è già in uso.',
  email_taken: 'Questa email è già registrata.',
  registration_closed: 'Le registrazioni sono chiuse su questa istanza.',
  validation_failed: 'Controlla i campi: la password richiede almeno 10 caratteri.',
}

export default function Register() {
  const { setUser } = useSession()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await auth.register(username, email, password)
      // Sign upon does not open a session, so sign in straight after.
      setUser(await auth.login(username, password))
    } catch (e) {
      setError(
        e instanceof ApiError
          ? (MESSAGES[e.code] ?? 'Registrazione non riuscita.')
          : 'Server non raggiungibile.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={handleSubmit}>
        <h1>Crea un account</h1>
        <p className="subtitle">Il primo account registrato amministra l’istanza.</p>

        {error && <div className="error">{error}</div>}

        <div className="field">
          <label htmlFor="username">Username</label>
          <input
            id="username"
            autoComplete="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="email">Email</label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
        </div>

        <div className="field">
          <label htmlFor="password">Password (min. 10 caratteri)</label>
          <input
            id="password"
            type="password"
            autoComplete="new-password"
            minLength={10}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>

        <button type="submit" disabled={busy}>
          {busy ? 'Creazione…' : 'Crea account'}
        </button>

        <p className="switch">
          Hai già un account? <Link to="/login">Accedi</Link>
        </p>
      </form>
    </div>
  )
}
