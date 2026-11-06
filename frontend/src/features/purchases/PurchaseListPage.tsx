import { useState, type SyntheticEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError, fieldMessage } from '../../api/client'
import type { ItemView, ListDetail } from '../../api/types'
import ConfirmDelete from '../../components/ConfirmDelete'
import { t } from '../../i18n/t'
import { usePurchaseList, useListChange, usePurchaseSettings } from './api'
import MoveDialog from './MoveDialog'
import ItemForm from './ItemForm'
import { formatChf, formatEur } from './money'
import { monthLabel } from './monthLabel'
import Totals from './Totals'

const dayFmt = new Intl.DateTimeFormat('it-IT', {
  day: 'numeric',
  month: 'long',
})
const dayLabel = (iso: string) => {
  const [y, m, d] = iso.split('-').map(Number)
  return dayFmt.format(new Date(y, m - 1, d))
}

const byName = (a: ItemView, b: ItemView) =>
  a.seriesTitle.localeCompare(b.seriesTitle) ||
  a.editionName.localeCompare(b.editionName) ||
  a.volume - b.volume

/** Groups ascending by date; the undated group (key "") goes last. */
function groupByDate(items: ItemView[]) {
  const groups = new Map<string, ItemView[]>()
  for (const i of items) {
    const k = i.releaseDate ?? ''
    groups.set(k, [...(groups.get(k) ?? []), i])
  }
  return [...groups.entries()]
    .sort(([a], [b]) => (a === '' ? 1 : b === '' ? -1 : a.localeCompare(b)))
    .map(([date, rows]) => ({ date, rows: rows.sort(byName) }))
}

/** Inline edit of description and month/year; works on closed lists too. */
function HeaderForm({
  list,
  onDone,
}: {
  list: ListDetail
  onDone: () => void
}) {
  const change = useListChange(list.id)
  const [description, setDescription] = useState(list.description)
  const [month, setMonth] = useState(
    `${list.year}-${String(list.month).padStart(2, '0')}`,
  )

  async function submit(ev: SyntheticEvent) {
    ev.preventDefault()
    const [year, m] = month.split('-').map(Number)
    try {
      await change.mutateAsync({
        method: 'PATCH',
        body: { description: description.trim(), month: m, year },
      })
      onDone()
    } catch {
      // shown from change.error
    }
  }

  const err = change.error
  const descError = fieldMessage(err, 'description')
  const monthError = fieldMessage(err, 'month') ?? fieldMessage(err, 'year')
  const general =
    err &&
    (err instanceof ApiError
      ? err.errors.length === 0 && err.detail
      : t('error.generic'))

  return (
    <form className="inline-actions" onSubmit={submit}>
      {general && (
        <p className="error" role="alert">
          {general}
        </p>
      )}
      <div className="field">
        <label htmlFor="list-description">
          {t('purchases.descriptionLabel')}
        </label>
        <input
          id="list-description"
          maxLength={100}
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          aria-invalid={descError ? true : undefined}
          aria-describedby={descError ? 'list-description-error' : undefined}
        />
        {descError && (
          <p id="list-description-error" className="field-help">
            {descError}
          </p>
        )}
      </div>
      <div className="field">
        <label htmlFor="list-month">{t('purchases.monthLabel')}</label>
        <input
          id="list-month"
          type="month"
          required
          value={month}
          onChange={(e) => setMonth(e.target.value)}
          aria-invalid={monthError ? true : undefined}
          aria-describedby={monthError ? 'list-month-error' : undefined}
        />
        {monthError && (
          <p id="list-month-error" className="field-help">
            {monthError}
          </p>
        )}
      </div>
      <button type="submit" disabled={change.isPending}>
        {t('common.save')}
      </button>
      <button type="button" className="quiet" onClick={onDone}>
        {t('common.cancel')}
      </button>
    </form>
  )
}

function Row({
  listId,
  item,
  settings,
  selected,
  onSelect,
  onMove,
}: {
  listId: string
  item: ItemView
  selected: boolean
  onSelect: (on: boolean) => void
  onMove: () => void
  settings: { chfRate: number; discountPercent: number }
}) {
  const change = useListChange(listId)
  const [editing, setEditing] = useState(false)
  const name = `${item.seriesTitle} · ${item.editionName}`
  const volume =
    item.editionVolumeCount === 1
      ? t('purchases.single')
      : t('purchases.volumeShort', { volume: item.volume })

  if (editing)
    return (
      <li className="edition-row item-row">
        <div className="grow">
          <strong>{name}</strong>
          <ItemForm
            listId={listId}
            settings={settings}
            item={item}
            onDone={() => setEditing(false)}
          />
        </div>
      </li>
    )

  return (
    <li className={`edition-row item-row${item.booked ? ' booked' : ''}`}>
      <label className="row-select">
        <input
          type="checkbox"
          checked={selected}
          onChange={(e) => onSelect(e.target.checked)}
          aria-label={t('purchases.select', { name, volume })}
        />
      </label>
      <div className="grow">
        <span className="name">{item.seriesTitle}</span>{' '}
        <span className="muted">{item.editionName}</span>
        <div>{volume}</div>
        {item.alsoIn.map((l) => (
          <div key={l} className="muted">
            {t('purchases.alsoIn', { list: l })}
          </div>
        ))}
      </div>
      <span className="num">{formatEur(item.priceEurCents)}</span>
      <span className="num">{formatChf(item.priceChfCents)}</span>
      <div className="inline-actions">
        <button
          type="button"
          className="quiet"
          aria-pressed={item.booked}
          aria-label={t('purchases.bookedFor', { name, volume })}
          disabled={change.isPending}
          onClick={() =>
            change.mutate({
              method: 'PATCH',
              path: `/items/${item.id}`,
              body: { booked: !item.booked },
            })
          }
        >
          {t('purchases.booked')}
        </button>
        <button
          type="button"
          className="quiet"
          onClick={() => setEditing(true)}
          aria-label={t('purchases.editRowFor', { name, volume })}
        >
          {t('purchases.editRow')}
        </button>
        <button
          type="button"
          className="quiet"
          onClick={onMove}
          aria-label={t('purchases.moveFor', { name, volume })}
        >
          {t('purchases.move')}
        </button>
        <ConfirmDelete
          ariaLabel={t('purchases.deleteFor', { name, volume })}
          what={t('purchases.rowDeleteWhat', {
            name: `${name} ${volume}`,
          })}
          onConfirm={async () => {
            await change.mutateAsync({
              method: 'DELETE',
              path: `/items/${item.id}`,
            })
          }}
        />
      </div>
    </li>
  )
}

export default function PurchaseListPage() {
  const { id = '' } = useParams()
  const { data, isPending, isError, error } = usePurchaseList(id)
  const settings = usePurchaseSettings().data
  const change = useListChange(id)
  const navigate = useNavigate()
  const [picked, setPicked] = useState<string[]>([])
  const [moving, setMoving] = useState<string[] | null>(null)
  const [editingHead, setEditingHead] = useState(false)
  // Rows that are gone (deleted, moved) cannot stay selected.
  const selected = picked.filter((x) => data?.items.some((i) => i.id === x))
  const unbooked = data?.items.filter((i) => !i.booked).length ?? 0
  return (
    <>
      <Link to="/purchases" className="back-link">
        {t('purchases.back')}
      </Link>
      <div className="page-head">
        {editingHead && data ? (
          <HeaderForm list={data} onDone={() => setEditingHead(false)} />
        ) : (
          <h1 tabIndex={-1}>
            {data
              ? t('purchases.listTitle', {
                  description: data.description,
                  month: monthLabel(data.month, data.year),
                })
              : t('purchases.title')}
          </h1>
        )}
        {data && (
          <div className="inline-actions">
            {!editingHead && (
              <button
                type="button"
                className="quiet"
                onClick={() => setEditingHead(true)}
                aria-label={t('purchases.editListLabel')}
              >
                {t('purchases.editList')}
              </button>
            )}
            {data.closed ? (
              <button
                type="button"
                className="quiet"
                disabled={change.isPending}
                onClick={() =>
                  change.mutate({ method: 'POST', path: '/reopen' })
                }
              >
                {t('purchases.reopen')}
              </button>
            ) : (
              <>
                {unbooked > 0 ? (
                  <ConfirmDelete
                    className="quiet"
                    label={t('purchases.close')}
                    what=""
                    question={
                      unbooked === 1
                        ? t('purchases.closeQuestionOne')
                        : t('purchases.closeQuestion', { count: unbooked })
                    }
                    yesLabel={t('purchases.yesClose')}
                    busyLabel={t('purchases.closing')}
                    onConfirm={async () => {
                      await change.mutateAsync({
                        method: 'POST',
                        path: '/close',
                      })
                    }}
                  />
                ) : (
                  <button
                    type="button"
                    className="quiet"
                    disabled={change.isPending}
                    onClick={() =>
                      change.mutate({ method: 'POST', path: '/close' })
                    }
                  >
                    {t('purchases.close')}
                  </button>
                )}
                <ConfirmDelete
                  label={t('purchases.deleteList')}
                  what={t('purchases.listDeleteWhat', {
                    name: data.description,
                  })}
                  onConfirm={async () => {
                    await change.mutateAsync({ method: 'DELETE' })
                    navigate('/purchases')
                  }}
                />
              </>
            )}
          </div>
        )}
      </div>
      {isPending && <p className="muted">{t('common.loading')}</p>}
      {isError && (
        <p className="error" role="alert">
          {error instanceof ApiError && error.status === 404
            ? t('purchases.notFound')
            : t('error.generic')}
        </p>
      )}
      {data && settings && (
        <>
          <section className="panel">
            <h2>{t('purchases.addTitle')}</h2>
            <ItemForm listId={id} settings={settings} onDone={() => {}} />
          </section>
          {data.items.length === 0 && (
            <p className="empty">{t('purchases.noRows')}</p>
          )}
          {groupByDate(data.items).map((g) => (
            <section key={g.date} className="item-group">
              <h2>{g.date ? dayLabel(g.date) : t('purchases.undated')}</h2>
              <ul className="edition-list">
                {g.rows.map((i) => (
                  <Row
                    key={i.id}
                    listId={id}
                    item={i}
                    settings={settings}
                    selected={selected.includes(i.id)}
                    onSelect={(on) =>
                      setPicked((s) =>
                        on ? [...s, i.id] : s.filter((x) => x !== i.id),
                      )
                    }
                    onMove={() => setMoving([i.id])}
                  />
                ))}
              </ul>
            </section>
          ))}
          <Totals list={data} settings={settings} />
          {selected.length > 0 && (
            <div
              className="selection-bar"
              role="region"
              aria-label={t('purchases.moveSelected')}
            >
              <span>
                {selected.length === 1
                  ? t('purchases.selectedOne')
                  : t('purchases.selectedCount', { count: selected.length })}
              </span>
              <button type="button" onClick={() => setMoving(selected)}>
                {t('purchases.moveSelected')}
              </button>
            </div>
          )}
          {moving && (
            <MoveDialog
              listId={id}
              itemIds={moving}
              onClose={() => setMoving(null)}
            />
          )}
        </>
      )}
    </>
  )
}
