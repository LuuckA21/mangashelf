# Revisione di sicurezza — 12 settembre 2026

Base esaminata: `master` a `4d31739f51c0ea881d425665c5198b214420252d`.
Correzioni: branch `security/general-audit-2026-09`; nessun deploy o merge automatico.

## Perimetro e metodo

Revisione manuale del codice Java/Spring, dei flussi React, delle migrazioni,
dei confini nginx/Docker e degli script operativi. Verificati autenticazione,
sessioni, autorizzazione, isolamento dei dati, CSRF, trattamento dell'input,
token email, richieste esterne, immagini e configurazione dei segreti.
Alle correzioni sono associati test di regressione, inclusi PostgreSQL reale
e nginx reale in CI. Nessun test è stato eseguito contro il server di produzione
o tramite la casella SMTP reale.

Le severità sono valutazioni contestuali, non punteggi CVSS. Non è una
certificazione di assenza di vulnerabilità né un penetration test dell'host.

## Problemi corretti

| ID | Severità | Problema | Correzione |
| --- | --- | --- | --- |
| SEC-01 | Media | Password e hash conservati nella sessione | Rimozione delle credenziali prima di salvare il contesto |
| SEC-02 | Media | Limite login aggirabile con richieste simultanee | Prenotazione atomica dei tentativi e tetto globale di concorrenza |
| SEC-03 | Media, condizionata alla rete | Header IP non attendibili inoltrati al backend | Un solo IP risolto da nginx, senza catene fornite dal client |
| SEC-04 | Media | Un amministratore revocato può completare modifiche già in attesa | Rivalidazione dell'attore sotto lock transazionale |
| SEC-05 | Media | Cancellazioni del catalogo possono eliminare nuovi dati personali | Vincoli PostgreSQL `ON DELETE RESTRICT` |

### SEC-01 — Credenziali nella sessione

`SecurityConfig` chiamava direttamente `DaoAuthenticationProvider.authenticate`:
non veniva eseguita la pulizia delle credenziali normalmente offerta da
`ProviderManager`. `AuthController` salvava il token restituito senza modificarlo.
Inoltre `UserPrincipal` è un record immutabile: il suo campo `password` conteneva
l'hash BCrypt, contrariamente al commento che lo dichiarava cancellato da Spring.

Conseguenza: password in chiaro e hash restavano raggiungibili dal contesto di
sessione, anche a lungo. Richiede accesso alla memoria/session store o una
vulnerabilità aggiuntiva per estrarli; non è un endpoint pubblico che li espone.

Correzione: `ProviderManager` rimuove la password dal token; il controller salva
un nuovo token senza credenziali e una copia del principal priva dell'hash.
Anche `toString()` del principal esclude i segreti.
`AuthIT.loginRotatesAndPersistsTheSessionUntilLogout` verifica entrambi i campi
nella sessione effettiva e continua a verificare login, rotazione e logout.

### SEC-02 — Tentativi paralleli

Il controllo `isBlocked` precedeva BCrypt, ma `recordFailure` avveniva solo al
termine. Molte richieste potevano superare insieme il controllo prima che il
primo errore aggiornasse il contatore, aumentando sia i tentativi sia il lavoro CPU.

Ora una prenotazione atomica conteggia errori già registrati e richieste in corso:
5 per account, 30 per indirizzo, con massimo 16 verifiche contemporanee per
istanza. Il lock protegge solo i contatori, non il calcolo BCrypt. Le prenotazioni
si liberano anche su eccezione; le mappe dei tentativi in corso non sono soggette
all'evizione delle cache. Username ed email mantengono la stessa identità canonica.

I test verificano 32 richieste concorrenti prima di completarne una, limite IP,
tetto globale, rilascio idempotente, scadenza e mantenimento dei tentativi in corso.
Restano limiti locali all'istanza: non costituiscono protezione DDoS distribuita.

### SEC-03 — Catena di proxy

nginx usava `$proxy_add_x_forwarded_for`: anche quando il mittente non era
`TRUSTED_PROXY`, il suo header veniva conservato e allungato. Tomcat, configurato
con elaborazione nativa degli header, può interpretare nuovamente una catena
contenente hop privati e ottenere un IP scelto dal client. Il caso rilevante è
l'accesso diretto dalla LAN o una catena di proxy privati; non si assume che ogni
client Internet potesse falsificare l'IP attraverso una catena corretta.

Ora nginx inoltra soltanto `$remote_addr`, dopo `set_real_ip_from` e la propria
risoluzione della fiducia. L'header standard `Forwarded` ricevuto è eliminato.
`scripts/test-proxy-headers.py` esercita il template di produzione con nginx:
client non fidato, proxy fidato e hop privato non esplicitamente fidato.
Verifica anche protocollo e host inoltrati. I container di test non pubblicano
porte e non montano dati applicativi.

È comunque indispensabile configurare `TRUSTED_PROXY` con il solo proxy reale e
fare in modo che il proxy esterno sovrascriva/costruisca correttamente gli header.
La configurazione Nginx Proxy Manager del server non è stata verificata dal vivo.

### SEC-04 — Privilegi revocati durante l'attesa

`AccountStateFilter` rivalidava correttamente lo stato all'inizio della richiesta,
ma `AdminUserService.updateUser` poteva poi attendere il lock globale. Nel
frattempo un altro amministratore poteva revocare l'attore. Una volta ottenuto
il lock, la richiesta in attesa poteva ancora promuovere un altro account.

Ora il servizio rilegge e blocca l'attore dopo il lock globale, controllando
esistenza, abilitazione, verifica email, ruolo e versione della sessione prima
di modificare il destinatario. La richiesta revocata riceve `session_invalid`.
I test verificano il vecchio principal dopo demozione/disabilitazione e la
revoca concorrente fra due amministratori. Il vincolo sull'ultimo admin rimane.

### SEC-05 — Cancellazioni e dati personali

Le verifiche del servizio contavano collezioni e acquisti prima della DELETE.
Le FK verso `series` avevano però `ON DELETE CASCADE`: un inserimento effettuato
da un altro utente dopo il controllo poteva essere cancellato silenziosamente.

La migrazione V11 cambia soltanto le FK `user_volume.series_id` e
`purchase_item.series_id` in `ON DELETE RESTRICT`. Le FK da account e lista
mantengono la cascata necessaria per eliminare i propri dati. Una gara residua
produce HTTP 409 tramite il gestore dei vincoli, non perdita di dati; i controlli
preventivi conservano i messaggi specifici nei casi ordinari.

I test PostgreSQL inseriscono e confermano un volume fra controllo e DELETE,
provando sia l'edizione sia il manga padre. Verificano anche il blocco diretto
della cancellazione quando esiste una riga di acquisto. La suite di eliminazione
account continua a verificare le cascate intenzionali.

## Protezioni già presenti esaminate

- Autenticazione basata su sessione; cookie `HttpOnly`, `Secure` predefinito e
  `SameSite=Lax`. CSRF attivo anche sugli endpoint pubblici di modifica, senza CORS permissivo.
- Endpoint amministrativi protetti sul backend; query di collezioni e liste
  vincolate al principal, non a un user ID accettato dal client. Query SQL/JPQL
  esaminate parametrizzate; non individuata concatenazione di input in SQL.
- Password BCrypt costo 12, controllo del limite effettivo di 72 byte e verifica
  della password corrente per cambio password/eliminazione. Reset e revoche
  invalidano le sessioni attraverso la versione dell'account.
- Token email casuali da 256 bit, solo hash nel database, scadenze, consumo sotto
  lock e controlli di versione per reset/eliminazione. Conferma esplicita via POST;
  un GET del link non elimina l'account. URL generati dall'origine configurata,
  non dagli header della richiesta; token nel frammento rimosso dalla cronologia.
- Risposte generiche alle richieste di recupero, invio in coda limitata,
  limitazione per IP/destinatario e timeout SMTP. TLS SMTP con verifica del nome
  del server; testo HTML delle email sottoposto a escaping.
- React rende il testo senza `dangerouslySetInnerHTML`; credenziali e token di
  sessione non salvati in localStorage. CSP, anti-framing e `nosniff` su nginx.
- Download copertine limitato a host HTTPS configurati, controlli sugli indirizzi,
  niente redirect, timeout e massimo 5 MiB. Immagini decodificate e ricodificate
  con limiti a dimensioni/pixel; nomi di file controllati e pubblicazione atomica.
  L'allow-list presuppone host affidabili: non autorizzare domini controllati da utenti.
- Database/backend senza porte host pubblicate nella Compose; runtime Java non
  root e copertine in sola lettura nel frontend. `.env` escluso dal repository.
  Ricerca mirata di chiavi private/token con formati noti senza riscontri nel
  codice corrente; non equivale a una scansione completa della storia Git.
- Script di backup con `umask 077`, checksum, backup di sicurezza e conferma per
  il ripristino. Ripristinare solo archivi fidati: i checksum non ne autenticano
  l'origine e un dump PostgreSQL è input privilegiato. Nessun ripristino reale
  è stato eseguito durante questa revisione.

## Verifica e limiti

Eseguiti localmente: 36 test frontend, lint, formattazione, build e i tre gruppi
di test operativi backup/ripristino, deploy e backup pianificati. `npm audit`
ha restituito **0 vulnerabilità note** nelle dipendenze risolte, incluse quelle
di sviluppo, alla data della verifica.

Java 25, Maven e Docker non sono disponibili insieme nell'ambiente locale:
la suite backend/PostgreSQL e i tre test nginx vengono eseguiti dal workflow
`build` della PR. L'esito definitivo è riportato nella PR e nel relativo run CI.
Non è stata eseguita una scansione CVE completa delle dipendenze Maven transitive
o delle immagini container: l'esito npm non si estende a questi componenti.

## Prima del deploy e prima di 2FA

1. Rivedere e approvare la PR; creare/verificare il backup pre-deploy. V11 non
   elimina righe, ma cambia vincoli: il rollback del codice non annulla la migrazione.
2. Ricostruire sia backend sia frontend: il secondo contiene la correzione nginx.
   Il riavvio del backend elimina le vecchie sessioni in memoria e richiede il login.
3. Verificare sul server HTTPS, `APP_COOKIE_SECURE=true`, `TRUSTED_PROXY` esatto,
   firewall sulla porta LAN e corretta provenienza degli IP nei log. HSTS e
   configurazione TLS del proxy esterno non sono verificabili dal solo repository.
4. Proteggere `.env` e backup; applicare conservazione e copia cifrata off-host.
   Gli account eliminati restano negli archivi fino alla rotazione prevista.
5. Pianificare scansione Maven/container e aggiornamenti regolari. La Compose
   usa il ruolo PostgreSQL di bootstrap anche per l'applicazione: separare in
   futuro ruolo runtime e migrazioni per ridurre l'impatto di una compromissione.
6. Per esposizione pubblica o più repliche: limiti condivisi/edge, quote e limiti
   CPU/memoria. I limiti in memoria si azzerano al riavvio e possono subire evizione;
   quelli del login non coprono ogni operazione autenticata costosa.
7. Le sessioni hanno timeout di inattività e cookie di 30 giorni, non una scadenza
   assoluta breve. Valutare riautenticazione per azioni sensibili e gestione dei
   dispositivi insieme a 2FA. Non usare un semplice reset email come aggiramento
   del secondo fattore; prevedere codici di recupero e revoca sicura.

La 2FA non è stata implementata in questo intervento.
