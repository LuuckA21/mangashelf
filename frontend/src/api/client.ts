/** Stable facade kept for existing imports while each API domain stays isolated. */
export { auth } from './auth'
export { catalog, metadata } from './catalog'
export { collection } from './collection'
export { ApiError, api } from './http'
export { purchases } from './purchases'
export type { PurchaseItemBody, PurchaseListBody } from './purchases'
export type * from './types'
