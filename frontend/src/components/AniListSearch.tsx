import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ApiError,
  metadata,
  type Manga,
  type MangaSearchResult,
} from '../api/client'
import { useI18n, type TranslationKey } from '../i18n'

/**
 * Finds a work on AniList and imports its metadata.
 *
 * Results already in the catalogue are marked as such and open the existing
 * entry instead of offering an import, because two rows for the same work
 * would each collect their own editions and split the shelf in half.
 */
export default function AniListSearch({
  onImported,
}: {
  onImported: (m: Manga) => void
}) {
  const navigate = useNavigate()
  const { t } = useI18n()
  const [term, setTerm] = useState('')
  const [results, setResults] = useState<MangaSearchResult[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [importing, setImporting] = useState<number | null>(null)
  const [error, setError] = useState<TranslationKey | null>(null)
  const [searchedTerm, setSearchedTerm] = useState('')
  const [retryUntil, setRetryUntil] = useState(0)
  const [secondsLeft, setSecondsLeft] = useState(0)

  useEffect(() => {
    if (!retryUntil) return
    const timer = window.setInterval(() => {
      const remaining = Math.max(0, Math.ceil((retryUntil - Date.now()) / 1000))
      setSecondsLeft(remaining)
      if (remaining === 0) window.clearInterval(timer)
    }, 250)
    return () => window.clearInterval(timer)
  }, [retryUntil])

  function showError(caught: unknown, fallback: TranslationKey) {
    const codes: Record<string, TranslationKey> = {
      anilist_unavailable: 'anilist.unavailable',
      anilist_rate_limited: 'anilist.rateLimited',
      anilist_media_not_found: 'anilist.notFound',
      anilist_invalid_search: 'anilist.invalidSearch',
      admin_required: 'anilist.adminRequired',
      unauthorized: 'anilist.sessionExpired',
      session_invalid: 'anilist.sessionExpired',
    }
    setError(
      caught instanceof ApiError
        ? (codes[caught.code] ?? fallback)
        : 'common.serverUnavailable',
    )
    const seconds =
      caught instanceof ApiError
        ? Math.min(86400, Math.ceil(caught.retryAfterSeconds ?? 0))
        : 0
    setSecondsLeft(seconds)
    setRetryUntil(seconds > 0 ? Date.now() + seconds * 1000 : 0)
  }

  async function handleSearch(event: FormEvent) {
    event.preventDefault()
    if (!term.trim() || busy || importing !== null || secondsLeft > 0) return
    setBusy(true)
    setError(null)
    setResults(null)
    setSearchedTerm(term.trim())
    try {
      setResults(await metadata.search(term.trim()))
    } catch (caught) {
      showError(caught, 'anilist.searchFailed')
    } finally {
      setBusy(false)
    }
  }

  async function handleImport(result: MangaSearchResult) {
    if (busy || importing !== null || secondsLeft > 0) return
    setImporting(result.anilistId)
    setError(null)
    try {
      onImported(await metadata.importManga(result.anilistId))
      setResults(null)
      setTerm('')
    } catch (caught) {
      showError(caught, 'anilist.importFailed')
    } finally {
      setImporting(null)
    }
  }

  return (
    <div className="panel" style={{ marginBottom: 24 }}>
      <p className="eyebrow" style={{ marginTop: 0 }}>
        {t('anilist.title')}
      </p>

      <form
        className="row"
        onSubmit={handleSearch}
        style={{ marginBottom: 16 }}
      >
        <input
          aria-label={t('anilist.searchLabel')}
          placeholder={t('anilist.searchPlaceholder')}
          value={term}
          maxLength={200}
          disabled={busy || importing !== null}
          onChange={(e) => setTerm(e.target.value)}
          style={{ flex: 1, minWidth: 200 }}
        />
        <button
          type="submit"
          disabled={
            busy || importing !== null || secondsLeft > 0 || !term.trim()
          }
        >
          {busy ? t('common.searching') : t('common.search')}
        </button>
      </form>

      {error && (
        <div className="error" role="alert">
          {t(error)}
          {secondsLeft > 0 && (
            <p>
              {t('anilist.retryIn')} {secondsLeft} {t('anilist.seconds')}
            </p>
          )}
          <p>{t('anilist.manualHelp')}</p>
        </div>
      )}

      {results?.length === 0 && (
        <p className="muted" style={{ fontSize: 14 }}>
          {t('anilist.noResults')} “{searchedTerm}”.
        </p>
      )}

      {results && results.length > 0 && (
        <ul className="result-list">
          {results.map((result) => (
            <li key={result.anilistId}>
              {result.coverUrl && (
                <img
                  src={result.coverUrl}
                  alt=""
                  className="result-cover"
                  loading="lazy"
                />
              )}
              <div className="result-body">
                <div className="result-title">
                  {result.titleEnglish ?? result.titleRomaji}
                </div>
                <div className="muted" style={{ fontSize: 13 }}>
                  {[
                    result.authors,
                    result.startYear,
                    result.totalVolumes
                      ? `${result.totalVolumes} ${t('common.volumeShort')}`
                      : null,
                  ]
                    .filter(Boolean)
                    .join(' · ')}
                </div>
              </div>
              {result.alreadyInCatalogue ? (
                <button
                  className="quiet"
                  onClick={() => navigate(`/manga/${result.mangaId}`)}
                >
                  {t('anilist.alreadyPresent')}
                </button>
              ) : (
                <button
                  onClick={() => handleImport(result)}
                  disabled={busy || importing !== null || secondsLeft > 0}
                >
                  {importing === result.anilistId
                    ? t('common.importing')
                    : t('common.import')}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
