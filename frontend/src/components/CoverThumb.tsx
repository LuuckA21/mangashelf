/** Small 2:3 cover on list rows, or an empty placeholder of the same size. */
export default function CoverThumb({
  editionId,
  hasCover,
  version,
}: {
  editionId: string
  hasCover: boolean
  version: number
}) {
  return hasCover ? (
    <img
      className="thumb"
      // ?v= busts the private one-hour cache when the cover changes.
      src={`/api/v1/editions/${editionId}/cover?v=${version}`}
      alt=""
      width={34}
      height={51}
      loading="lazy"
    />
  ) : (
    <div className="thumb" aria-hidden="true" />
  )
}
