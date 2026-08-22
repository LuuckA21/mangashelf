import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { metadata, type Manga, type MangaSearchResult } from '../api/client'
import { useI18n } from '../i18n'

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
  const [error, setError] = useState<string | null>(null)

  async function handleSearch(event: FormEvent) {
    event.preventDefault()
    if (!term.trim()) return
    setBusy(true)
    setError(null)
    try {
      setResults(await metadata.search(term))
    } catch {
      setError(t('anilist.searchFailed'))
    } finally {
      setBusy(false)
    }
  }

  async function handleImport(result: MangaSearchResult) {
    setImporting(result.anilistId)
    setError(null)
    try {
      onImported(await metadata.importManga(result.anilistId))
      setResults(null)
      setTerm('')
    } catch {
      setError(t('anilist.importFailed'))
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
          onChange={(e) => setTerm(e.target.value)}
          style={{ flex: 1, minWidth: 200 }}
        />
        <button type="submit" disabled={busy}>
          {busy ? t('common.searching') : t('common.search')}
        </button>
      </form>

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}

      {results?.length === 0 && (
        <p className="muted" style={{ fontSize: 14 }}>
          {t('anilist.noResults')} “{term}”.
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
                  disabled={importing !== null}
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
