# Stile del frontend

Guida allo stile visivo e alle convenzioni CSS del frontend React. La fonte di
verità resta [`frontend/src/styles.css`](../frontend/src/styles.css): questo
documento spiega le regole che quel file segue, così le nuove schermate restano
coerenti.

## Principio

Indaco e vermiglione su carta: i due inchiostri della stampa xilografica.

- **Indaco** porta la struttura: dorsi, titoli, filetti, stati attivi, volumi
  posseduti.
- **Vermiglione** compare solo dove l'interfaccia chiede di agire (pulsante
  primario, hover di un link, conferma di eliminazione). Non
  è mai decorazione.
- **Carta** e **cenere** sono il fondo e il testo secondario.

Pochi elementi, angoli quasi vivi (`2px`), filetti sottili al posto di ombre.
Niente ombre, gradienti o icone decorative.

## Token

Tutti definiti in `:root`. Usa sempre la variabile, mai l'esadecimale.

| Token              | Valore    | Uso                                           |
| ------------------ | --------- | --------------------------------------------- |
| `--paper`          | `#f1efe8` | sfondo pagina                                 |
| `--paper-raised`   | `#fbfaf7` | pannelli, input, topbar, righe in evidenza    |
| `--indigo`         | `#1e3a5f` | link, struttura, tile possedute, chip attive  |
| `--indigo-deep`    | `#142942` | testo principale, filetti forti, hover indaco |
| `--indigo-wash`    | `#dfe4ec` | hover dei pulsanti `quiet`                    |
| `--vermilion`      | `#c93427` | azione primaria, errori, pericolo             |
| `--vermilion-deep` | `#a82a1f` | hover dell'azione primaria                    |
| `--ash`            | `#706b63` | testo secondario, label, intestazioni tabella |
| `--ash-line`       | `#d6d2c8` | bordi e separatori                            |

Valori fissi già presenti fuori dai token (non aggiungerne altri senza motivo):
verde `#4a7c4e` per il successo, `#f7e9e7` per righe in eliminazione.

### Contrasto

`--ash` e `--vermilion` restano sopra 4.5:1 (WCAG AA) su entrambe le carte e
su bianco. È verificato in
[`frontend/src/styles.test.ts`](../frontend/src/styles.test.ts): se cambi un
colore, aggiorna anche il test.

## Tipografia

| Token            | Famiglia                                   | Uso                                        |
| ---------------- | ------------------------------------------ | ------------------------------------------ |
| `--font-display` | serif (Iowan Old Style, Palatino, Georgia) | `h1`–`h3`, brand, titoli di opere/edizioni |
| `--font-body`    | sans di sistema                            | testo corrente, form, pulsanti             |
| `--font-data`    | monospace di sistema                       | numeri, tile, eyebrow, intestazioni        |

- Corpo `16px`, interlinea `1.55`. Titoli peso `600`, `letter-spacing: -0.015em`.
- Scala in uso: `32px` titolo pagina (`26px` su mobile), `30px` auth, `21px`
  sezione, `17–19px` titoli in lista, `14–15px` testo secondario, `12–13px`
  meta e label.
- Label ed eyebrow: `12px`, maiuscolo, spaziatura larga, colore `--ash`.
- Cifre sempre in `--font-data` e allineate a destra (classe `.num`), così le
  virgole si incolonnano.

## Spaziatura e misure

- Unità base `--s: 8px`; le spaziature si scrivono come multipli:
  `calc(var(--s) * 2)`. Evita valori arbitrari.
- `--radius: 2px` per tutto; `999px` solo per chip e badge a pillola.
- `--shell: 1080px` larghezza massima di `.page` e topbar.
- Le copertine mantengono il 2:3 del volume stampato (`46×69`, `34×51`,
  `96×144`, `120×180`) con `object-fit: cover` e sfondo `--indigo-wash`.

## Componenti

Riusa queste classi prima di crearne di nuove.

### Pulsanti

| Classe                   | Aspetto                                       | Quando                                        |
| ------------------------ | --------------------------------------------- | --------------------------------------------- |
| `button` (default)       | pieno vermiglione, testo bianco               | l'azione principale della schermata           |
| `button.quiet`           | trasparente, bordo `--ash-line`, testo indaco | azioni secondarie, annulla                    |
| `button.danger`          | contorno vermiglione, si riempie in hover     | eliminazioni (mai pieno a riposo)             |
| `.link-button`           | solo glifo, `--ash`, vermiglione in hover     | azioni compatte in riga (modifica, rimuovi)   |
| `.chip` / `.chip.active` | pillola; attiva = piena indaco                | filtri a poche opzioni, al posto di un select |

Una sola azione piena per area. Le eliminazioni passano da un componente `ConfirmDelete`: conferma inline che
nomina cosa verrà eliminato, niente `window.confirm` né dialoghi.

### Form

- Struttura: `<div class="field"><label>…</label><input /></div>`.
- Input, select e textarea occupano il 100% e hanno fondo `--paper-raised`.
- Campi stretti con classe, non con larghezze inline: `.field-narrow`,
  `.field-medium`, `.field-date` (su mobile tornano a tutta larghezza).
- Testo d'aiuto sotto il campo: `.field-help` (spesso con `.muted`).
- Affiancamenti: `.row` (flex che va a capo) o `.grid-2` (due colonne, una su
  schermi stretti).

### Messaggi e stati

- `.error`: filetto sinistro vermiglione su carta rialzata.
- `.success`: stesso schema, filetto verde.
- `.empty`: riquadro tratteggiato centrato per liste vuote.
- `.muted`: testo secondario. `.eyebrow`: sopratitolo monospace maiuscolo.
- `.app-state`: schermata intera di caricamento o errore.

### Contenitori e liste

- `.page` + `.page-head`: impaginazione di ogni pagina.
- `.panel`: carta rialzata con bordo; `.settings-panel` lo limita a `520px`.
- Filetto sinistro di `3px` per dare un "dorso" a un blocco: indaco per le
  edizioni (`.edition-row`, `.edition-list a`, vermiglione in hover),
  vermiglione per le zone pericolose (`.account-danger`).
- Liste (`.manga-list`, `.edition-list`): niente punti
  elenco, righe separate da `1px solid var(--ash-line)`; l'elemento che cresce
  ha `.grow` (`flex: 1; min-width: 0`).

### Scaffale

L'elemento caratteristico dell'app (componente `Shelf`):
la serie come griglia di tile numerate.

- `.tile`: volume posseduto, pieno indaco, numero in monospace.
- `.tile.missing`: mancante, contorno tratteggiato, così un buco nella serie si
  vede subito.
- `.tile.busy`: in salvataggio (l'update è ottimistico, vedi la spec). Legenda in `.shelf-legend`, avanzamento in
  `.progress`.

### Tabelle

I dati da confrontare in colonna vanno in tabella, non in card
(`.admin-users-table`).

- Intestazioni monospace maiuscole in `--ash`, filetto `--indigo-deep` sotto
  l'intestazione e sopra i totali.
- Interruttori di stato con `aria-pressed` (le tile dello scaffale).

## Responsive

Un solo breakpoint principale: `@media (max-width: 700px)` (più `640px` per
`.grid-2`). Su mobile:

- ogni controllo (`button`, `input`, `select`, link-button) è alto
  almeno **44px**;
- le tabelle diventano card: `thead` nascosto e ogni cella si etichetta da sé
  con `data-label` (`content: attr(data-label)`). Quando aggiungi una colonna,
  aggiungi anche il suo `data-label`;
- nelle righe testo + pulsante il pulsante va a capo a tutta larghezza invece
  di schiacciare il testo;
- la nav della topbar va sotto il brand.

Le regole principali sono verificate in `styles.test.ts`: se le modifichi,
aggiorna il test.

## Accessibilità

- Focus sempre visibile: `outline: 2px` indaco (vermiglione sulle tile), mai
  `outline: none` senza sostituto.
- `@media (prefers-reduced-motion: reduce)` azzera animazioni e transizioni;
  le transizioni esistenti durano al massimo `100ms`.
- Colore mai come unico segnale: le tile mancanti cambiano anche bordo, gli
  stati hanno testo o `aria-pressed`.
- Contrasto minimo AA come descritto sopra.

## Convenzioni di codice

- Tutti gli stili stanno in `frontend/src/styles.css`, divisi per sezioni con
  un commento-intestazione (`/* ---- shelf */`). Niente CSS modules, framework
  CSS o CSS-in-JS.
- Nomi di classe in kebab-case, descrittivi del ruolo (`edition-row`,
  `missing-line`); modificatori come classi aggiuntive (`.tile.missing`,
  `.chip.active`) o attributi ARIA.
- Gli stili `style={{ … }}` inline si usano solo per piccoli margini una
  tantum; tutto ciò che deve cambiare su mobile va in una classe, perché una
  media query non può sovrascrivere uno stile inline.
- Commenta il _perché_ di una scelta visiva, come fa il resto del file.
- Solo italiano: ogni testo visibile passa da `t('chiave')`, con le chiavi in
  [`i18n/it.ts`](../frontend/src/i18n/it.ts).
- Formattazione con Prettier (`semi: false`, `singleQuote: true`); prima del
  commit esegui `npm run lint`, `npm run format:check` e `npm test` in
  `frontend/`.
