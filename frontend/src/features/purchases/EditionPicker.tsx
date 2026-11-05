import { useState } from 'react'
import { t } from '../../i18n/t'
import { useSeriesDetail, useSeriesList } from '../catalog/api'

export type PickedEdition = {
  editionId: string
  label: string
  volumeCount: number
}

/** Search a series, then pick one of its editions. */
export default function EditionPicker({
  onPick,
}: {
  onPick: (e: PickedEdition) => void
}) {
  const [text, setText] = useState('')
  const [q, setQ] = useState('')
  const [seriesId, setSeriesId] = useState('')
  const list = useSeriesList(q, 1)
  const detail = useSeriesDetail(seriesId)

  // Not a <form>: the picker lives inside ItemForm's form.
  function search() {
    setSeriesId('')
    setQ(text.trim())
  }

  return (
    <div className="edition-picker">
      <div className="search-form" role="search">
        <div className="field">
          <label htmlFor="picker-q">{t('purchases.searchSeries')}</label>
          <input
            id="picker-q"
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                e.preventDefault()
                search()
              }
            }}
          />
        </div>
        <button type="button" className="quiet" onClick={search}>
          {t('purchases.search')}
        </button>
      </div>
      {q && !seriesId && (
        <ul className="chips">
          {list.data?.content.map((s) => (
            <li key={s.id}>
              <button
                type="button"
                className="chip"
                onClick={() => setSeriesId(s.id)}
              >
                {s.title}
              </button>
            </li>
          ))}
        </ul>
      )}
      {seriesId && detail.data && (
        <>
          <p className="muted">
            {t('purchases.pickEdition', { series: detail.data.title })}
          </p>
          <ul className="chips">
            {detail.data.editions.map((e) => (
              <li key={e.id}>
                <button
                  type="button"
                  className="chip"
                  onClick={() =>
                    onPick({
                      editionId: e.id,
                      label: `${detail.data.title} · ${e.name}`,
                      volumeCount: e.volumeCount,
                    })
                  }
                >
                  {e.name}
                </button>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  )
}
