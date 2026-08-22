export interface User {
  id: number
  username: string
  email: string
  role: 'USER' | 'ADMIN'
  language: 'it' | 'en'
}

export type PublicationStatus =
  | 'FINISHED' | 'RELEASING' | 'NOT_YET_RELEASED' | 'CANCELLED' | 'HIATUS'

export interface Manga {
  id: number
  titleRomaji: string
  titleNative: string | null
  titleEnglish: string | null
  displayTitle: string
  authors: string | null
  description: string | null
  coverUrl: string | null
  status: PublicationStatus | null
  genres: string[] | null
  startYear: number | null
  totalVolumes: number | null
  anilistId: number | null
}

export interface Series {
  id: number
  mangaId: number
  mangaTitle: string
  publisher: string
  language: string
  name: string
  totalVolumes: number | null
  completed: boolean
}

export interface SeriesProgress {
  seriesId: number
  seriesName: string
  mangaTitle: string
  declaredTotal: number | null
  upTo: number
  progressTotal: number
  ownedCount: number
  ownedNumbers: number[]
  missingNumbers: number[]
}

export interface EditionSummary {
  seriesId: number
  seriesName: string
  publisher: string
  mangaId: number
  mangaTitle: string
  coverUrl: string | null
  declaredTotal: number | null
  completed: boolean
  upTo: number
  progressTotal: number
  ownedCount: number
  ownedNumbers: number[]
  missingNumbers: number[]
}

export interface OwnedVolume {
  seriesId: number
  number: number
  seriesName: string
  publisher: string
  mangaId: number
  mangaTitle: string
  addedAt: string
}

export interface PageResult<T> {
  content: T[]
  size: number
  number: number
  totalElements: number
  totalPages: number
}

export interface MangaSearchResult {
  anilistId: number
  titleRomaji: string
  titleEnglish: string | null
  titleNative: string | null
  authors: string | null
  coverUrl: string | null
  status: string | null
  startYear: number | null
  totalVolumes: number | null
  alreadyInCatalogue: boolean
  mangaId: number | null
}

export interface PurchaseItem {
  id: number
  seriesId: number
  seriesName: string
  publisher: string
  mangaTitle: string
  volumeNumber: number
  releaseDate: string | null
  priceEurCents: number | null
  priceChfCents: number | null
  reserved: boolean
  purchasedAt: string | null
}

export interface PurchaseList {
  id: number
  name: string
  periodYear: number | null
  periodMonth: number | null
  paidAt: string | null
  discountPercent: string | null
  discountCents: number | null
  items: PurchaseItem[]
  reservedCount: number
  purchasedCount: number
  totalEurCents: number
  subtotalChfCents: number
  discountAppliedCents: number
  totalChfCents: number
}

export interface PurchaseListSummary {
  id: number
  name: string
  periodYear: number | null
  periodMonth: number | null
  paidAt: string | null
  itemCount: number
  reservedCount: number
  purchasedCount: number
  totalChfCents: number
}

export interface PurchaseSuggestion {
  seriesId: number
  seriesName: string
  publisher: string
  mangaTitle: string
  volumeNumber: number
  priceEurCents: number | null
  priceChfCents: number | null
  lastBoughtIn: string
}

export interface YearStats {
  year: number
  listCount: number
  volumeCount: number
  fullChfCents: number
  discountChfCents: number
  netChfCents: number
  averageFullChfCents: number
  averageNetChfCents: number
}

export interface PurchaseStats {
  years: YearStats[]
  listCount: number
  volumeCount: number
  fullChfCents: number
  discountChfCents: number
  netChfCents: number
  averageFullChfCents: number
  averageNetChfCents: number
}

export interface TransferResult {
  added: number
  alreadyOwned: number
  notPurchased: number
}
