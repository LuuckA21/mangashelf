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
  'auth.login.title': 'Accedi',
  'auth.login.submit': 'Accedi',
  'auth.login.passwordChanged':
    'Password cambiata. Accedi con la nuova password.',
  'auth.username': 'Nome utente',
  'auth.password': 'Password',
  'auth.changePassword.title': 'Cambia la password',
  'auth.changePassword.intro':
    'Per continuare scegli una nuova password (da 12 a 64 caratteri).',
  'auth.currentPassword': 'Password attuale',
  'auth.newPassword': 'Nuova password',
  'auth.changePassword.submit': 'Cambia password',
  'catalog.title': 'Catalogo',
  'series.title': 'Serie',
  'edition.title': 'Edizione',
  'users.title': 'Utenti',
  'account.title': 'Account',
  'privacy.title': 'Privacy',
  'notFound.title': 'Pagina non trovata',
  'notFound.back': 'Torna alla collezione',
  'footer.privacy': 'Privacy',
} as const

export type TranslationKey = keyof typeof it
