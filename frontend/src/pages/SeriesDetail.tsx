import { useCallback, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  catalog, collection, type Series, type SeriesProgress, type Volume,
} from '../api/client'
import { ApiError } from '../api/client'
import { useSession } from '../api/session'
import ConfirmDelete from '../components/ConfirmDelete'
import Layout from '../components/Layout'
import Shelf from '../components/Shelf'

/** One edition: its volumes, and which of them are on your shelf. */
export default function SeriesDetail() {
  const { user } = useSession()
  const isAdmin = user?.role === 'ADMIN'

  const { id } = useParams()
  const seriesId = Number(id)
  const navigate = useNavigate()

  const [series, setSeries] = useState<Series | null>(null)
  const [volumes, setVolumes] = useState<Volume[]>([])
  const [progress, setProgress] = useState<SeriesProgress | null>(null)
  const [error, setError] = useState<string | null>(null)

  const [managing, setManaging] = useState(false)
  const [nextCount, setNextCount] = useState('5')
  const [from, setFrom] = useState('1')
  const [to, setTo] = useState('10')
  const [working, setWorking] = useState(false)

  const reload = useCallback(async () => {
    const [vols, prog] = await Promise.all([
      catalog.listVolumes(seriesId),
      collection.progress(seriesId),
    ])
    setVolumes(vols)
    setProgress(prog)
  }, [seriesId])

  useEffect(() => {
    catalog.getSeries(seriesId).then(setSeries).catch(() => setError('Edizione non trovata.'))
    reload().catch(() => setError('Non riesco a caricare i volumi.'))
  }, [seriesId, reload])

  // The highest number, not the count: a run with gaps has fewer volumes
  // catalogued than its top number.
  const lastNumber = volumes.length
    ? Math.max(...volumes.map((v) => v.number))
    : 0

  /**
   * In management mode a click removes the volume from the catalogue for
   * everyone; otherwise it only changes what this user owns. The grid
   * changes colour in that mode so the two meanings are never confused.
   */
  async function handleToggle(volume: Volume, owned: boolean) {
    if (managing) {
      try {
        await catalog.deleteVolume(volume.id)
      } catch (e) {
        setError(e instanceof ApiError && e.code === 'volume_is_owned'
          ? `Non posso eliminare il volume ${volume.number}: qualcuno lo possiede.`
          : 'Eliminazione non riuscita.')
      }
      await reload()
      return
    }

    setProgress((current) => current && {
      ...current,
      ownedNumbers: owned
        ? current.ownedNumbers.filter((n) => n !== volume.number)
        : [...current.ownedNumbers, volume.number],
      ownedCount: current.ownedCount + (owned ? -1 : 1),
    })
    try {
      if (owned) await collection.remove(volume.id)
      else await collection.add(volume.id)
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

  async function handleCreateVolumes() {
    setWorking(true)
    setError(null)
    try {
      await catalog.createVolumes(seriesId, Number(from), Number(to))
      await reload()
    } catch {
      setError('Non sono riuscito a creare i volumi.')
    } finally {
      setWorking(false)
    }
  }

  /**
   * Catalogues the volumes that follow the highest number already present.
   *
   * An ongoing series grows a volume every couple of months, and the tedious
   * part is not the typing but remembering where the run stopped. Reading
   * that from the catalogue removes the only step that needs thinking.
   */
  async function handleAddNext() {
    setWorking(true)
    setError(null)
    try {
      const count = Number(nextCount)
      await catalog.createVolumes(seriesId, lastNumber + 1, lastNumber + count)
      await reload()
    } catch {
      setError('Non sono riuscito a creare i volumi.')
    } finally {
      setWorking(false)
    }
  }

  async function handleOwnRange() {
    setWorking(true)
    setError(null)
    try {
      await collection.addRange(seriesId, Number(from), Number(to))
      await reload()
    } catch {
      setError('Non sono riuscito a segnare l’intervallo.')
    } finally {
      setWorking(false)
    }
  }

  const percent = progress && progress.totalVolumes > 0
    ? Math.round((progress.ownedCount / progress.totalVolumes) * 100)
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
            <button
              className={managing ? 'danger' : 'quiet'}
              onClick={() => setManaging(!managing)}
            >
              {managing ? 'Esci da gestione volumi' : 'Gestisci volumi'}
            </button>
            <ConfirmDelete
              what={`l’edizione “${series.name}” e i suoi volumi`}
              onConfirm={async () => {
                try {
                  await catalog.deleteSeries(series.id)
                  navigate(`/manga/${series.mangaId}`)
                } catch (e) {
                  setError(e instanceof ApiError && e.code === 'series_has_owned_volumes'
                    ? 'Non posso eliminare: qualcuno possiede volumi di questa edizione.'
                    : 'Eliminazione non riuscita.')
                }
              }}
            />
          </div>
        )}
      </div>

      {error && <div className="error">{error}</div>}

      {progress && (
        <>
          <div className="row" style={{ justifyContent: 'space-between' }}>
            <span className="eyebrow">
              {progress.ownedCount} di {progress.totalVolumes} volumi
            </span>
            <span className="eyebrow">{percent}%</span>
          </div>
          <div className="progress"><i style={{ width: `${percent}%` }} /></div>
        </>
      )}

      <Shelf
        volumes={volumes}
        ownedNumbers={progress?.ownedNumbers ?? []}
        onToggle={handleToggle}
        canCreate={isAdmin}
        managing={managing}
      />

      {managing && (
        <p className="muted" style={{ marginTop: 16, fontSize: 14 }}>
          Modalità gestione: un clic elimina il volume dal catalogo, per tutti.
          I volumi posseduti da qualcuno non possono essere eliminati.
        </p>
      )}

      {!managing && progress && progress.missingNumbers.length > 0 && (
        <p className="muted" style={{ marginTop: 24, fontSize: 14 }}>
          Ti mancano i volumi {progress.missingNumbers.join(', ')}.
        </p>
      )}

      {isAdmin && (
        <div className="panel" style={{ marginTop: 32 }}>
          <p className="eyebrow" style={{ marginTop: 0 }}>Nuove uscite</p>
          <div className="row">
            <span>
              {lastNumber > 0
                ? `Ultimo volume catalogato: ${lastNumber}.`
                : 'Nessun volume catalogato.'}
              {' '}Aggiungine altri
            </span>
            <input
              type="number" min={1} max={50} value={nextCount}
              onChange={(e) => setNextCount(e.target.value)}
              style={{ width: 72 }}
              aria-label="Quanti volumi aggiungere"
            />
            <button onClick={handleAddNext} disabled={working || !nextCount}>
              {lastNumber > 0
                ? `Aggiungi ${lastNumber + 1}–${lastNumber + Number(nextCount || 0)}`
                : `Aggiungi 1–${nextCount}`}
            </button>
          </div>
          <p className="muted" style={{ fontSize: 13, marginBottom: 0 }}>
            Continua dalla fine della collana, così non devi ricordare a che
            numero eri arrivato.
          </p>
        </div>
      )}

      <div className="panel" style={{ marginTop: 16 }}>
        <p className="eyebrow" style={{ marginTop: 0 }}>Lavora su un intervallo</p>
        <div className="row" style={{ marginBottom: 16 }}>
          <div style={{ width: 90 }}>
            <label htmlFor="from">Dal</label>
            <input id="from" type="number" min={0} value={from}
                   onChange={(e) => setFrom(e.target.value)} />
          </div>
          <div style={{ width: 90 }}>
            <label htmlFor="to">Al</label>
            <input id="to" type="number" min={0} value={to}
                   onChange={(e) => setTo(e.target.value)} />
          </div>
        </div>
        <div className="row">
          {isAdmin && (
            <button className="quiet" onClick={handleCreateVolumes} disabled={working}>
              Crea questi volumi
            </button>
          )}
          <button onClick={handleOwnRange} disabled={working}>
            Segna come posseduti
          </button>
        </div>
        <p className="muted" style={{ fontSize: 13, marginBottom: 0 }}>
          {isAdmin
            ? 'Crea i volumi che l’editore ha pubblicato, poi segna quelli che hai davvero. I numeri già presenti vengono saltati.'
            : 'Segna i volumi che hai sullo scaffale. I numeri già segnati vengono saltati.'}
        </p>
      </div>
    </Layout>
  )
}
