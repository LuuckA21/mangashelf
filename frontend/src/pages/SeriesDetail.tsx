import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, catalog, collection, type Series, type SeriesProgress } from '../api/client'
import { useSession } from '../api/session'
import ConfirmDelete from '../components/ConfirmDelete'
import Layout from '../components/Layout'
import Shelf from '../components/Shelf'

const DELETE_ERRORS: Record<string, string> = {
  series_has_owned_volumes: 'Non posso eliminare: qualcuno possiede volumi di questa edizione.',
  series_in_purchase_list: 'Non posso eliminare: questa edizione compare in una lista d’acquisto.',
}

/** One edition: which of its volumes are on your shelf. */
export default function SeriesDetail() {
  const { user } = useSession()
  const isAdmin = user?.role === 'ADMIN'

  const { id } = useParams()
  const seriesId = Number(id)
  const navigate = useNavigate()

  const [series, setSeries] = useState<Series | null>(null)
  const [progress, setProgress] = useState<SeriesProgress | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [from, setFrom] = useState('1')
  const [to, setTo] = useState('10')
  const [working, setWorking] = useState(false)

  const reload = useCallback(async () => {
    setProgress(await collection.progress(seriesId))
  }, [seriesId])

  useEffect(() => {
    catalog.getSeries(seriesId).then(setSeries).catch(() => setError('Edizione non trovata.'))
    reload().catch(() => setError('Non riesco a caricare i volumi.'))
  }, [seriesId, reload])

  /** Optimistic: the tile flips at once, then reconciles with the server. */
  async function handleToggle(number: number, owned: boolean) {
    setProgress((current) => current && {
      ...current,
      ownedNumbers: owned
        ? current.ownedNumbers.filter((n) => n !== number)
        : [...current.ownedNumbers, number].sort((a, b) => a - b),
      ownedCount: current.ownedCount + (owned ? -1 : 1),
    })
    try {
      if (owned) await collection.remove(seriesId, number)
      else await collection.add(seriesId, number)
    } catch (e) {
      // "already owned" and "not owned" mean the server is already in the
      // requested state. With the optimistic update this happens when the
      // view had fallen behind, and reporting it as an error would confuse
      // without there being anything to fix.
      const benign = e instanceof ApiError
        && (e.code === 'already_owned' || e.code === 'not_owned')
      if (!benign) setError('Modifica non riuscita.')
    }
    await reload()
  }

  async function handleRange() {
    setWorking(true)
    setError(null)
    try {
      await collection.addRange(seriesId, Number(from), Number(to))
      await reload()
    } catch (e) {
      setError(e instanceof ApiError && e.code === 'invalid_range'
        ? 'Intervallo non valido: usa numeri fra 0 e 999.'
        : 'Non sono riuscito a segnare l’intervallo.')
    } finally {
      setWorking(false)
    }
  }

  const percent = progress && progress.upTo > 0
    ? Math.round((progress.ownedCount / progress.upTo) * 100)
    : 0

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">
          {series
            ? <Link to={`/manga/${series.mangaId}`}>{series.mangaTitle}</Link>
            : <Link to="/">Catalogo</Link>}
        </p>
        <h1>{series?.name ?? '…'}</h1>
        {series && <p className="muted">{series.publisher}</p>}
        {isAdmin && series && (
          <div className="inline-actions" style={{ marginTop: 12 }}>
            <ConfirmDelete
              what={`l’edizione “${series.name}”`}
              onConfirm={async () => {
                try {
                  await catalog.deleteSeries(series.id)
                  navigate(`/manga/${series.mangaId}`)
                } catch (e) {
                  setError(e instanceof ApiError
                    ? (DELETE_ERRORS[e.code] ?? 'Eliminazione non riuscita.')
                    : 'Eliminazione non riuscita.')
                }
              }}
            />
          </div>
        )}
      </div>

      {error && <div className="error">{error}</div>}

      {progress && progress.upTo > 0 && (
        <>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <span className="eyebrow">
              {progress.ownedCount} di {progress.upTo} volumi
              {progress.declaredTotal == null && ' segnati'}
            </span>
            <span className="eyebrow">{percent}%</span>
          </div>
          <div className="progress"><i style={{ width: `${percent}%` }} /></div>
        </>
      )}

      <Shelf
        upTo={progress?.upTo ?? 0}
        ownedNumbers={progress?.ownedNumbers ?? []}
        onToggle={handleToggle}
      />

      {progress && progress.missingNumbers.length > 0 && (
        <p className="muted" style={{ marginTop: 24, fontSize: 14 }}>
          Ti mancano i volumi {progress.missingNumbers.join(', ')}.
        </p>
      )}

      <div className="panel" style={{ marginTop: 32 }}>
        <p className="eyebrow" style={{ marginTop: 0 }}>Segna un intervallo</p>
        <div className="row" style={{ marginBottom: 16 }}>
          <div className="field-narrow">
            <label htmlFor="from">Dal</label>
            <input id="from" type="number" min={0} max={999} value={from}
                   onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div className="field-narrow">
            <label htmlFor="to">Al</label>
            <input id="to" type="number" min={0} max={999} value={to}
                   onChange={(e) => setTo(e.target.value)} />
          </div>
          <button onClick={handleRange} disabled={working}>
            {working ? 'Segno…' : 'Segna come posseduti'}
          </button>
        </div>
        <p className="muted" style={{ fontSize: 13, marginBottom: 0 }}>
          Per registrare una collana in un colpo solo. I numeri già segnati
          vengono saltati.
        </p>
      </div>
    </Layout>
  )
}
