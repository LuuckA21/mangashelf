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
  publisherId: string | null
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

export type PurchaseSettings = { chfRate: number; discountPercent: number }

export type ListSummary = {
  id: string
  description: string
  month: number
  year: number
  closed: boolean
  itemCount: number
  unbookedCount: number
  totalCents: number
}

export type ItemView = {
  id: string
  editionId: string
  seriesId: string
  seriesTitle: string
  editionName: string
  editionVolumeCount: number
  volume: number
  releaseDate: string | null
  priceEurCents: number
  priceChfCents: number
  booked: boolean
  alsoIn: string[]
}

export type ListDetail = {
  id: string
  description: string
  month: number
  year: number
  closed: boolean
  items: ItemView[]
  subtotalCents: number
  discountCents: number
  discountAuto: boolean
  pointsDiscountCents: number
  totalCents: number
}

/** Values proposed by the add-row form; null = nothing to propose. */
export type Proposal = {
  volume: number | null
  priceEurCents: number | null
  releaseDate: string | null
}

export type Publisher = { id: string; name: string }

export type SpendTotals = {
  totalCents: number
  averageCents: number
  spentCents: number
  upcomingCents: number
}

export type MonthSpend = {
  month: number
  spentCents: number
  upcomingCents: number
  hasLists: boolean
  aboveAverage: boolean
}

export type YearSpend = {
  year: number
  spentCents: number
  upcomingCents: number
  aboveAverage: boolean
}

export type SpendingSummary = {
  years: number[]
  year: number | null
  months: MonthSpend[]
  monthTotals: SpendTotals
  byYear: YearSpend[]
  yearTotals: SpendTotals
}
