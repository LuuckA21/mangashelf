import { useEffect, useState, type FormEvent } from 'react'
import QRCode from 'qrcode'
import { twoFactor, type TwoFactorStatus } from '../api/users'
import { ApiError } from '../api/http'
import { useSession } from '../api/session'
import { useI18n } from '../i18n'
import TwoFactorCode from './TwoFactorCode'

type Mode = 'idle' | 'setup' | 'confirm' | 'disable' | 'regenerate' | 'recovery'

export default function TwoFactorSettings() {
  const { setUser } = useSession()
  const { t } = useI18n()
  const [status, setStatus] = useState<TwoFactorStatus | null>(null)
  const [retry, setRetry] = useState(0)
  const [mode, setMode] = useState<Mode>('idle')
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [secret, setSecret] = useState('')
  const [qr, setQr] = useState('')
  const [codes, setCodes] = useState<string[]>([])
  const [saved, setSaved] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    twoFactor
      .status()
      .then((result) => {
        if (active) setStatus(result)
      })
      .catch(() => {
        if (active) setError('two_factor_failed')
      })
    return () => {
      active = false
    }
  }, [retry])

  function clear() {
    setPassword('')
    setCode('')
    setSecret('')
    setQr('')
    setCodes([])
    setSaved(false)
  }

  async function submit(event: FormEvent) {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (mode === 'setup') {
        const result = await twoFactor.setup(password)
        setPassword('')
        setSecret(result.secret)
        setMode('confirm')
        // Generated entirely in the browser; the secret never reaches a QR service.
        setQr(
          await QRCode.toDataURL(result.uri, { width: 240, margin: 2 }).catch(
            () => '',
          ),
        )
      } else if (mode === 'confirm' || mode === 'regenerate') {
        const result =
          mode === 'confirm'
            ? await twoFactor.enable(code.trim())
            : await twoFactor.regenerate(password, code.trim())
        clear()
        setUser(result.user)
        setCodes(result.recoveryCodes)
        setMode('recovery')
        setStatus({
          available: true,
          enabled: true,
          recoveryCodesRemaining: result.recoveryCodes.length,
        })
      } else if (mode === 'disable') {
        setUser(await twoFactor.disable(password, code.trim()))
        clear()
        setMode('idle')
        setStatus({
          available: true,
          enabled: false,
          recoveryCodesRemaining: 0,
        })
      }
    } catch (e) {
      setCode('')
      setError(e instanceof ApiError ? e.code : 'two_factor_failed')
    } finally {
      setBusy(false)
    }
  }

  async function cancel() {
    setBusy(true)
    setError(null)
    try {
      if (mode === 'confirm') await twoFactor.cancelSetup()
      clear()
      setMode('idle')
    } catch {
      setError('two_factor_failed')
    } finally {
      setBusy(false)
    }
  }

  function download() {
    const url = URL.createObjectURL(
      new Blob(
        [
          'MangaShelf — ' +
            t('twoFactor.recoveryTitle') +
            '\n\n' +
            codes.join('\n') +
            '\n',
        ],
        { type: 'text/plain' },
      ),
    )
    const link = document.createElement('a')
    link.href = url
    link.download = 'mangashelf-recovery-codes.txt'
    link.click()
    setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  const message = error
    ? ({
        current_password_invalid: t('settings.currentPasswordInvalid'),
        two_factor_invalid: t('twoFactor.invalid'),
        two_factor_setup_expired: t('twoFactor.setupExpired'),
        two_factor_unavailable: t('twoFactor.unavailable'),
        too_many_attempts: t('login.blocked'),
        session_invalid: t('deletion.sessionInvalid'),
      }[error] ?? t('twoFactor.failed'))
    : null

  return (
    <section
      className="panel settings-panel settings-section"
      aria-labelledby="two-factor-title"
    >
      <h2 id="two-factor-title">{t('twoFactor.title')}</h2>
      <p className="muted field-help">{t('twoFactor.help')}</p>
      {message && (
        <p className="error" role="alert">
          {message}
        </p>
      )}
      {!status ? (
        <button
          type="button"
          onClick={() => {
            setError(null)
            setRetry(retry + 1)
          }}
        >
          {t('twoFactor.retry')}
        </button>
      ) : mode === 'recovery' ? (
        <>
          <h3>{t('twoFactor.recoveryTitle')}</h3>
          <p>{t('twoFactor.recoveryHelp')}</p>
          <ul className="recovery-codes">
            {codes.map((item) => (
              <li key={item}>
                <code>{item}</code>
              </li>
            ))}
          </ul>
          <button type="button" className="secondary" onClick={download}>
            {t('twoFactor.download')}
          </button>
          <label className="deletion-check">
            <input
              type="checkbox"
              checked={saved}
              onChange={(event) => setSaved(event.target.checked)}
            />
            {t('twoFactor.saved')}
          </label>
          <button
            type="button"
            disabled={!saved}
            onClick={() => {
              clear()
              setMode('idle')
            }}
          >
            {t('twoFactor.done')}
          </button>
        </>
      ) : mode === 'idle' ? (
        <>
          <p role="status">
            <strong>
              {t(status.enabled ? 'twoFactor.enabled' : 'twoFactor.disabled')}
            </strong>
          </p>
          {status.enabled && (
            <p>
              {t('twoFactor.remaining')} {status.recoveryCodesRemaining}
            </p>
          )}
          {!status.available ? (
            <p>{t('twoFactor.unavailable')}</p>
          ) : (
            <div className="two-factor-actions">
              {status.enabled ? (
                <>
                  <button
                    type="button"
                    className="secondary"
                    onClick={() => setMode('regenerate')}
                  >
                    {t('twoFactor.regenerate')}
                  </button>
                  <button
                    type="button"
                    className="danger"
                    onClick={() => setMode('disable')}
                  >
                    {t('twoFactor.disable')}
                  </button>
                </>
              ) : (
                <button type="button" onClick={() => setMode('setup')}>
                  {t('twoFactor.start')}
                </button>
              )}
            </div>
          )}
        </>
      ) : (
        <form onSubmit={submit}>
          {mode === 'confirm' ? (
            <>
              <p>{t('twoFactor.scan')}</p>
              {qr && (
                <img
                  className="two-factor-qr"
                  src={qr}
                  alt={t('twoFactor.qr')}
                  width={240}
                  height={240}
                />
              )}
              <p>{t('twoFactor.secret')}</p>
              <code className="two-factor-secret">{secret}</code>
            </>
          ) : (
            <>
              {mode !== 'setup' && (
                <p>
                  {t(
                    mode === 'disable'
                      ? 'twoFactor.disableHelp'
                      : 'twoFactor.regenerateHelp',
                  )}
                </p>
              )}
              <div className="field">
                <label htmlFor="two-factor-password">
                  {t('twoFactor.password')}
                </label>
                <input
                  id="two-factor-password"
                  type="password"
                  autoComplete="current-password"
                  required
                  maxLength={200}
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </div>
            </>
          )}
          {mode !== 'setup' && (
            <TwoFactorCode
              id="two-factor-code"
              value={code}
              onChange={setCode}
            />
          )}
          <div className="two-factor-actions">
            <button type="submit" disabled={busy}>
              {busy
                ? t('common.saving')
                : t(
                    mode === 'confirm'
                      ? 'twoFactor.confirm'
                      : mode === 'disable'
                        ? 'twoFactor.disable'
                        : mode === 'regenerate'
                          ? 'twoFactor.regenerate'
                          : 'twoFactor.continue',
                  )}
            </button>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              onClick={() => void cancel()}
            >
              {t('twoFactor.cancel')}
            </button>
          </div>
        </form>
      )}
      <p className="muted field-help">{t('twoFactor.sessionHelp')}</p>
    </section>
  )
}
