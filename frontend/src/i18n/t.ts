import { it, type TranslationKey } from './it'

export type { TranslationKey }

export function t(
  key: TranslationKey,
  params: Record<string, string | number> = {},
): string {
  return it[key].replace(/\{(\w+)\}/g, (match, name: string) =>
    name in params ? String(params[name]) : match,
  )
}
