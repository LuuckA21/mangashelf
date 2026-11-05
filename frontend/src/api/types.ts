// Mirrors the backend DTO records. Ids are strings, Instants are ISO strings.
export type Role = 'ADMIN' | 'USER'
export type EditionType = 'REGULAR' | 'VARIANT' | 'JAPANESE'
export type EditionStatus = 'ONGOING' | 'COMPLETED'

export type Page<T> = {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

export type MeResponse = {
  id: string
  username: string
  role: Role
  mustChangePassword: boolean
}

export type UserResponse = MeResponse & { createdAt: string }

export type SeriesSummary = {
  id: string
  title: string
  editionCount: number
}

export type EditionResponse = {
  id: string
  seriesId: string
  seriesTitle: string
  name: string
  publisher: string | null
  type: EditionType
  status: EditionStatus
  volumeCount: number
  hasCover: boolean
  canEditCover: boolean
  version: number
  collectionCount: number
  inMyCollection: boolean
}

export type SeriesDetail = {
  id: string
  title: string
  editions: EditionResponse[]
}

export type CollectionEntry = {
  editionId: string
  seriesId: string
  seriesTitle: string
  editionName: string
  publisher: string | null
  type: EditionType
  status: EditionStatus
  volumeCount: number
  hasCover: boolean
  version: number
  owned: number[]
}

/** One chart bar: owned volumes per key (publisher, enum name, or null for "Altri"). */
export type StatBar = { key: string | null; volumes: number }

export type CollectionStats = {
  editions: number
  series: number
  ownedVolumes: number
  totalVolumes: number
  completedEditions: number
  byPublisher: StatBar[]
  byType: StatBar[]
  byStatus: StatBar[]
}
