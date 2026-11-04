import { useState } from 'react'
import type { EditionResponse } from '../../api/types'
import { useLocation, useNavigate, useParams } from 'react-router'
import ConfirmDelete from '../../components/ConfirmDelete'
import Shelf from '../../components/Shelf'
import { t } from '../../i18n/t'
import { useMe } from '../auth/useMe'
import {
  errorText,
  useDeleteEdition,
  useSetCover,
  useUpdateEdition,
} from '../catalog/api'
import CoverInput from '../catalog/CoverInput'
import EditionForm from '../catalog/EditionForm'
import {
  useAddToCollection,
  useEdition,
  useOwned,
  useRemoveFromCollection,
  useSetOwned,
} from './api'
import { formatMissing } from './formatMissing'

function AdminActions({ edition }: { edition: EditionResponse }) {
  const navigate = useNavigate()
  const update = useUpdateEdition(edition)
  const remove = useDeleteEdition(edition)
  const [editing, setEditing] = useState(false)
  return (
    <>
      {!editing && (
        <>
          <button className="quiet" onClick={() => setEditing(true)}>
            {t('edition.edit')}
          </button>
          <ConfirmDelete
            label={t('edition.delete')}
            what={t('edition.deleteWhat', { n: edition.collectionCount })}
            onConfirm={() =>
              remove.mutateAsync().then(
                () => navigate(`/series/${edition.seriesId}`),
                () => undefined,
              )
            }
          />
          {remove.isError && (
            <p className="error" role="alert">
              {errorText(remove.error)}
            </p>
          )}
        </>
      )}
      {editing && (
        // A new version (after a stale-edit refetch) remounts the form with fresh values.
        <EditionForm
          key={edition.version}
          initial={{ ...edition, publisher: edition.publisher ?? '' }}
          submitLabel={t('common.save')}
          pending={update.isPending}
          error={update.error}
          conflictNote={t('edition.reloaded')}
          onCancel={() => setEditing(false)}
          onSubmit={(values) =>
            update.mutate(values, { onSuccess: () => setEditing(false) })
          }
        />
      )}
    </>
  )
}

function CoverActions({ edition }: { edition: EditionResponse }) {
  const cover = useSetCover(edition)
  return (
    <div>
      {/* Remounting after a failure drops the preview, so the current cover shows. */}
      <CoverInput
        key={cover.failureCount}
        disabled={cover.isPending}
        onSelect={(file) => cover.mutate(file)}
      />
      {edition.hasCover && (
        <ConfirmDelete
          label={t('cover.remove')}
          what={t('cover.removeWhat')}
          onConfirm={() =>
            cover.mutateAsync(null).then(
              () => undefined,
              () => undefined,
            )
          }
        />
      )}
      {cover.isError && (
        <p className="error" role="alert">
          {errorText(cover.error)}
        </p>
      )}
    </div>
  )
}

export default function EditionPage() {
  const id = useParams().id ?? ''
  const isAdmin = useMe().data?.role === 'ADMIN'
  // Set when the edition was just created but its cover upload failed.
  const createdCoverError = (
    useLocation().state as { coverError?: string } | null
  )?.coverError
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
            {[
              edition.publisher,
              t(`type.${edition.type}`),
              t(`status.${edition.status}`),
              `${t('edition.volumes')} ${edition.volumeCount}`,
            ]
              .filter(Boolean)
              .join(' · ')}
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
            {isAdmin && <AdminActions edition={edition} />}
          </div>
          {createdCoverError && (
            <p className="error" role="alert">
              {createdCoverError}
            </p>
          )}
          {(add.isError || remove.isError) && (
            <p className="error" role="alert">
              {t('error.generic')}
            </p>
          )}
          {edition.canEditCover && (
            <details className="cover-actions">
              <summary>{t('cover.manage')}</summary>
              <CoverActions edition={edition} />
            </details>
          )}
        </div>
      </div>

      <h2 className="section-title">{t('shelf.title')}</h2>
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
