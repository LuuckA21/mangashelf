/** Stable public API surface. Feature modules keep transport and types small. */
export { ApiError, api } from './http'
export * from './types'
export { auth, adminAccounts } from './users'
export { catalog } from './catalog'
export { metadata } from './metadata'
export { purchases } from './purchases'
export { collection } from './collection'
