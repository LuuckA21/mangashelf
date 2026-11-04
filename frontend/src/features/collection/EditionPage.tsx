import { useParams } from 'react-router'
import ConfirmDelete from '../../components/ConfirmDelete'
import Shelf from '../../components/Shelf'
import { t } from '../../i18n/t'
import {
  useAddToCollection,
  useEdition,
  useOwned,
  useRemoveFromCollection,
  useSetOwned,
} from './api'
import { formatMissing } from './formatMissing'

export default function EditionPage() {
  const id = useParams().id ?? ''
  const { data: edition, isPending, isError, refetch } = useEdition(id)
  const entry = useOwned(id, edition?.inMyCollection ?? false)
  const setOwned = useSetOwned(id)
  const add = useAddToCollection(id)
  const remove = useRemoveFromCollection(id)

  if (isPending || entry.isLoading)
    return <p className="muted">{t('common.loading')}</p>
  if (isError || (entry.isError && edition?.inMyCollection))
    return (
      <div className="error" role="alert">
        {t('error.generic')}{' '}
        <button
          className="quiet"
          onClick={() => {
            void refetch()
            void entry.refetch()
          }}
        >
          {t('common.retry')}
        </button>
      </div>
    )

  const owned = entry.data?.owned ?? []
  const missing = formatMissing(owned, edition.volumeCount)

  return (
    <>
      <div className="page-head">
        <h1>{edition.seriesTitle}</h1>
      </div>
      <div className="row">
        {edition.hasCover ? (
          <img
            className="detail-cover"
            src={`/api/v1/editions/${id}/cover?v=${edition.version}`}
            alt={t('edition.coverAlt', {
              title: `${edition.seriesTitle} ${edition.name}`,
            })}
            width={120}
            height={180}
          />
        ) : (
          <div className="detail-cover" aria-hidden="true" />
        )}
        <div>
          <h2>{edition.name}</h2>
          <p className="muted">
            {edition.publisher} · {t(`type.${edition.type}`)} ·{' '}
            {t(`status.${edition.status}`)}
            {' · '}
            {t('edition.volumes')} {edition.volumeCount}
          </p>
          <div className="inline-actions">
            {edition.inMyCollection ? (
              <ConfirmDelete
                label={t('edition.remove')}
                what={t('edition.removeWhat')}
                onConfirm={() => remove.mutateAsync().catch(() => undefined)}
              />
            ) : (
              <button onClick={() => add.mutate()} disabled={add.isPending}>
                {t('edition.add')}
              </button>
            )}
            {/* Task 11: admin actions (edit, delete) go here. */}
          </div>
          {(add.isError || remove.isError) && (
            <p className="error" role="alert">
              {t('error.generic')}
            </p>
          )}
        </div>
      </div>

      <Shelf
        volumeCount={edition.volumeCount}
        owned={owned}
        onChange={(next) => setOwned.mutate(next)}
        busy={setOwned.isPending}
      />
      {setOwned.failed && (
        <p className="error" role="alert">
          {t('shelf.saveFailed')}
        </p>
      )}
      {missing && (
        <p className="missing-line">
          {t('shelf.missingSummary', { list: missing })}
        </p>
      )}
    </>
  )
}
