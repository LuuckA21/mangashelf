// Italian UI texts. Flat dotted keys; `{name}` marks a parameter (see t.ts).
export const it = {
  'app.name': 'MangaShelf',
  'nav.collection': 'Collezione',
  'nav.catalog': 'Catalogo',
  'nav.users': 'Utenti',
  'nav.account': 'Account',
  'nav.logout': 'Esci',
  'collection.title': 'La mia collezione',
  'shelf.volumeOwned': 'Volume {n}, posseduto',
  'shelf.volumeMissing': 'Volume {n}, mancante',
  'common.loading': 'Caricamento…',
  'common.retry': 'Riprova',
  'error.network': 'Errore di rete',
  'error.generic': 'Si è verificato un errore',
} as const

export type TranslationKey = keyof typeof it
