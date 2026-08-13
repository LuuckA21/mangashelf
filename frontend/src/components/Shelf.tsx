import { useState } from 'react'
import type { Volume } from '../api/client'

interface Props {
  volumes: Volume[]
  ownedNumbers: number[]
  onToggle: (volume: Volume, owned: boolean) => Promise<void>
  /** Whether the viewer may add volumes to the catalogue. */
  canCreate: boolean
  /** In management mode a click deletes the volume rather than owning it. */
  managing?: boolean
}

/**
 * The volumes of an edition, laid out as a numbered grid.
 *
 * Owned volumes are solid; missing ones keep their place as outlines.
 * Reading a gap at a glance is the job of this screen, so absent volumes
 * are given the same footprint as the present ones rather than being left
 * out of the sequence.
 */
export default function Shelf({
  volumes, ownedNumbers, onToggle, canCreate, managing = false,
}: Props) {
  const owned = new Set(ownedNumbers)
  const [busy, setBusy] = useState<number | null>(null)

  async function toggle(volume: Volume) {
    setBusy(volume.id)
    try {
      await onToggle(volume, owned.has(volume.number))
    } finally {
      setBusy(null)
    }
  }

  if (volumes.length === 0) {
    return (
      <div className="empty">
        {canCreate
          ? 'Nessun volume in questa edizione. Aggiungine un intervallo qui sotto.'
          : 'Nessun volume catalogato in questa edizione.'}
      </div>
    )
  }

  return (
    <>
      <div className={`shelf${managing ? ' managing' : ''}`}>
        {volumes.map((volume) => {
          const isOwned = owned.has(volume.number)
          return (
            <button
              key={volume.id}
              className={`tile${isOwned ? '' : ' missing'}${busy === volume.id ? ' busy' : ''}`}
              onClick={() => toggle(volume)}
              aria-pressed={isOwned}
              title={
                managing
                  ? `Elimina il volume ${volume.number} dal catalogo`
                  : isOwned
                    ? `Volume ${volume.number} — ce l'hai. Clicca per toglierlo.`
                    : `Volume ${volume.number} — ti manca. Clicca per aggiungerlo.`
              }
            >
              {volume.number}
            </button>
          )
        })}
      </div>
      {!managing && (
        <div className="shelf-legend">
          <span><i className="swatch owned" /> Posseduto</span>
          <span><i className="swatch missing" /> Mancante</span>
        </div>
      )}
    </>
  )
}
