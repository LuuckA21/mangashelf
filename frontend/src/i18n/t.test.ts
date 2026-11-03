import { describe, expect, it } from 'vitest'
import { t } from './t'

describe('t', () => {
  it('returns the Italian string', () => {
    expect(t('collection.title')).toBe('La mia collezione')
  })

  it('replaces {name} params', () => {
    expect(t('shelf.volumeOwned', { n: 12 })).toBe('Volume 12, posseduto')
  })
})
