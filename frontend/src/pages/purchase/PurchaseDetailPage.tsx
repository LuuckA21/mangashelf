import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { ApiError, purchases, type PurchaseList } from '../../api/client'
import ConfirmDelete from '../../components/ConfirmDelete'
import Layout from '../../components/Layout'
import { formatPeriod } from '../../format'
import { useI18n } from '../../i18n'
import AddItem from './AddItem'
import CarryOver from './CarryOver'
import ListSettings from './ListSettings'
import PurchaseItemsTable from './PurchaseItemsTable'
import Suggestions from './Suggestions'

/** One purchase list: status, rows, carry-over and additions. */
export default function PurchaseDetailPage() {
  const { locale, t } = useI18n()
  const { id } = useParams()
  const listId = Number(id)
  const navigate = useNavigate()
  const [list, setList] = useState<PurchaseList | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [editing, setEditing] = useState(false)
  const [transfer, setTransfer] = useState<string | null>(null)

  useEffect(() => {
    purchases
      .get(listId)
      .then(setList)
      .catch(() => setError(t('purchase.notFound')))
  }, [listId, t])

  if (!list) {
    return (
      <Layout>
        <p className="eyebrow">
          <Link to="/purchases">{t('nav.purchases')}</Link>
        </p>
        {error ? (
          <div className="error" role="alert">
            {error}
          </div>
        ) : (
          <p className="muted">{t('common.loading')}</p>
        )}
      </Layout>
    )
  }

  const closed = list.paidAt != null
  const period = formatPeriod(list.periodYear, list.periodMonth, locale)

  return (
    <Layout>
      <div className="page-head">
        <p className="eyebrow">
          <Link to="/purchases">{t('nav.purchases')}</Link>
          {period && ` · ${period}`}
          {list.items.length > 0 &&
            ` · ${list.reservedCount} ${t('purchases.reserved')}`}
          {list.items.length > 0 &&
            ` · ${list.purchasedCount} ${t('collection.of')} ${list.items.length} ${t('purchases.purchased')}`}
        </p>
        <h1>
          {list.name}
          {closed && <span className="paid-badge">{t('purchases.paid')}</span>}
        </h1>
        <div className="inline-actions" style={{ marginTop: 12 }}>
          <button
            className={closed ? 'quiet' : ''}
            onClick={async () => {
              try {
                setList(await purchases.setPaid(list.id, !closed))
              } catch {
                setError(t('purchase.changeStatusFailed'))
              }
            }}
          >
            {closed ? t('purchase.reopen') : t('purchase.markPaid')}
          </button>
          <button
            className="quiet"
            disabled={list.purchasedCount === 0}
            title={
              list.purchasedCount === 0
                ? t('purchase.markFirstPurchased')
                : t('purchase.addToCollection')
            }
            onClick={async () => {
              setTransfer(t('purchase.adding'))
              try {
                const result = await purchases.toCollection(list.id)
                setTransfer(
                  [
                    result.added > 0
                      ? `${result.added} ${t('purchase.addedVolumes')}`
                      : null,
                    result.alreadyOwned > 0
                      ? `${result.alreadyOwned} ${t('purchase.alreadyOwned')}`
                      : null,
                    result.notPurchased > 0
                      ? `${result.notPurchased} ${t('purchase.unpurchasedIgnored')}`
                      : null,
                  ]
                    .filter(Boolean)
                    .join(' · ') || t('purchase.noChanges'),
                )
              } catch {
                setTransfer(null)
                setError(t('purchase.addCollectionFailed'))
              }
            }}
          >
            {t('purchase.addToCollectionShort')}
          </button>
          {!closed && (
            <button className="quiet" onClick={() => setEditing(!editing)}>
              {editing ? t('common.close') : t('purchase.details')}
            </button>
          )}
          <ConfirmDelete
            what={`${t('purchase.deleteNamedList')} “${list.name}”`}
            disabled={closed}
            onConfirm={async () => {
              try {
                await purchases.remove(list.id)
                navigate('/purchases')
              } catch (caught) {
                setError(
                  caught instanceof ApiError && caught.code === 'list_is_paid'
                    ? t('purchase.reopenBeforeDelete')
                    : t('common.deleteFailed'),
                )
              }
            }}
          />
        </div>
      </div>

      {error && (
        <div className="error" role="alert">
          {error}
        </div>
      )}
      {transfer && (
        <p className="muted" style={{ fontSize: 14 }}>
          {transfer}
        </p>
      )}
      {closed && (
        <p className="muted" style={{ fontSize: 14 }}>
          {t('purchase.closedHelp')}
        </p>
      )}

      {editing && !closed && (
        <ListSettings
          list={list}
          onSaved={(updated) => {
            setList(updated)
            setEditing(false)
          }}
          onError={setError}
        />
      )}

      <PurchaseItemsTable
        list={list}
        closed={closed}
        onUpdated={setList}
        onError={setError}
      />

      {!closed && (
        <CarryOver
          list={list}
          onMoved={async (moved) => {
            setList(await purchases.get(list.id))
            setTransfer(
              moved === 0
                ? t('purchase.carryNone')
                : `${moved} ${t('purchase.carriedVolumes')}`,
            )
          }}
          onError={setError}
        />
      )}
      {!closed && (
        <Suggestions
          listId={list.id}
          itemCount={list.items.length}
          onAdded={setList}
          onError={setError}
        />
      )}
      {!closed && (
        <AddItem listId={list.id} onAdded={setList} onError={setError} />
      )}
    </Layout>
  )
}
