# Valutazione delle patch di sicurezza — 14 settembre 2026

Base analizzata: `master`, commit `efeda94d2f50a8d6c437ea966a9fa82f7ac1a1cb`
(2FA già integrato, PR #19). Documento in ingresso: `security-patches.md`.
L'analisi di tutti i punti precede le modifiche. Le proposte sono state confrontate
con implementazione, chiamanti, configurazione di deployment e test esistenti.

## Decisioni

| Proposta | Riscontro sul codice attuale | Decisione |
| --- | --- | --- |
| 1. DNS pinning | Esiste già una allowlist esatta, HTTPS obbligatorio, porta 443, rifiuto di userinfo, indirizzi privati e redirect. La risoluzione preliminare resta separata da quella del client HTTP. | Aggiunto un resolver che valida gli indirizzi effettivamente usati dal client; conservata la allowlist. |
| 2. Path traversal | `safeName` vieta già separatori e nomi arbitrari; i chiamanti usano ID. Scrittura con temporaneo unico, rename atomico e permessi corretti già presente. | Aggiunta normalizzazione e verifica del contenimento come difesa supplementare; conservate atomicità, pulizia e permessi 0644. |
| 3. Ritardo progressivo | Sono già presenti contatori per ID canonico e IP, prenotazione dei tentativi paralleli e limite globale di 16 verifiche. La stessa classe protegge anche il 2FA con contatori indipendenti. | Respinta la sostituzione proposta. Il problema del blocco volontario resta un compromesso documentato, non viene dichiarato risolto. |
| 4. Rate limiting nginx | Mancava un limite prima di Spring. La patch proposta però ripristina `proxy_add_x_forwarded_for`, già rimosso per impedire lo spoofing. | Implementato mantenendo un unico blocco proxy con header bonificati, quote separate per registrazione e login, inclusi completamento 2FA e richieste di recupero email. |
| 5. Autorizzazione dei metodi | Le scritture del catalogo e tutto `/api/metadata/**` sono già admin-only per URL. I servizi non avevano autorizzazione dichiarativa. | Abilitata method security; annotati i sette metodi di scrittura e `MetadataService`, che importa direttamente tramite repository. |
| 6. Password/sessioni | Esistono già `PUT /api/auth/me/password`, verifica password e 2FA, blocco della riga utente, incremento di `sessionVersion`, filtro di revoca e interfaccia tradotta. | Nessun secondo endpoint o registro in memoria. Conservati i controlli attuali, inclusi reset, revoca dei challenge 2FA e modifiche amministrative. |
| 7a. CSP e permessi browser | Le anteprime provengono da AniList, le copertine salvate sono locali, il QR 2FA usa dati locali. | Limitato `img-src` a self/AniList/data, aggiunti `object-src 'none'` e Permissions-Policy, anche sugli asset. Omettiamo l'obsoleta direttiva `interest-cohort`. |
| 7b. SameSite Strict | Esistono ora link email e protezione CSRF Spring sulle scritture. | Conservato `Lax`; non è stata dimostrata una vulnerabilità che richieda questo cambio di comportamento. |
| 7c. Digest Docker | I tag erano modificabili. | Pinnate tutte le immagini: PostgreSQL, Maven, JRE, Node e nginx, usando i digest degli indici multiarch del registry ufficiale. |
| 7d. Dependabot | Configurazione assente. La proposta copriva solo il Dockerfile backend. | Aggiunti Maven, npm, entrambi i Dockerfile, Docker Compose e Actions; esclusi gli aggiornamenti automatici di major PostgreSQL. |
| 7e. Scansione CI | `npm audit --audit-level=high` era già eseguito. `trivy-action@master` sarebbe modificabile. | Conservato npm audit, aggiunte scansioni Trivy per backend, frontend e DB. Action Trivy e Actions esistenti fissate a SHA completi. |
| 8. Branch/verifiche | Nessuna esigenza di modificare `master` o avviare il Compose di produzione. | Branch separato e verifiche isolate; nessun merge o deploy automatico. |

## Perché non applicare il documento alla lettera

### Login e registrazione

Il blocco per account può effettivamente essere provocato da chi conosce il nome:
cinque fallimenti possono impedire nuovi accessi per quindici minuti. Non revoca
le sessioni già aperte. I controlli per account restano utili contro tentativi
distribuiti su molti IP: rimuoverli non equivale a migliorare automaticamente
la sicurezza. Gli IP possono essere condivisi tramite NAT, quindi il costo di un
blocco IP non ricade necessariamente solo sull'attaccante.

Il codice proposto riconta il testo inserito anziché l'ID dell'account: username
ed email tornerebbero ad avere budget separati. L'attesa non serializza le richieste:
molte richieste possono dormire insieme e poi eseguire BCrypt contemporaneamente.
Il progetto usa virtual thread, ma richieste, memoria e CPU rimangono risorse
limitate. Si perderebbero inoltre le prenotazioni prima della verifica e il limite
globale; modificare `LoginAttempts` cambierebbe anche la protezione del 2FA.

La registrazione proposta controlla un contatore alimentato dai fallimenti di
login senza incrementarlo per le registrazioni. Il limiter di registrazione
attuale consuma invece una quota **prima** del servizio, anche per i tentativi
falliti. Sostituirlo sarebbe una regressione.

Il limite nginx aggiuntivo riduce gli abusi da un singolo IP prima di interrogare
il database. Non risolve gli attacchi distribuiti né il blocco mirato dell'account.
Una revisione futura di quest'ultimo richiede un progetto specifico: recupero
sicuro, budget canonici e paralleli, e separazione esplicita dalle politiche MFA.

### Password e sessioni

Il nuovo endpoint proposto sarebbe accessibile agli utenti autenticati e
controllerebbe solo la password corrente, creando una via alternativa al cambio
password che oggi richiede anche TOTP/codice di recupero. La revoca basata su
versione persistente è già comune a cambio password, reset e amministrazione.
Un secondo `SessionRegistry` locale aggiungerebbe stato e percorsi da mantenere
senza sostituire questi controlli. I test esistenti coprono vecchia password,
revoca di una seconda sessione e richiesta del secondo fattore.

### DNS, filesystem e proxy

L'allowlist limita la sfruttabilità del rebinding: con la configurazione predefinita
l'utente non può inserire un dominio DNS sotto il proprio controllo. Il pinning
aggiunto è difesa ulteriore in caso di compromissione o ampliamento dei domini
fidati. Il pool riceve esattamente gli indirizzi validati e usa il nome originale
per TLS; non vengono disabilitate verifica dei certificati o hostname verification.
Retry, redirect e cookie HTTP sono disabilitati; connessioni e tempi di attesa
sono limitati e il client viene chiuso da Spring allo spegnimento.

Gli IP letterali non hanno una seconda risoluzione DNS e restano soggetti al
filtro preliminare. La verifica aggiunta non è un firewall e conserva la politica
sugli intervalli IP già adottata dal progetto. La allowlist rimane necessaria;
non va estesa a domini non fidati.

Il contenimento dei percorsi è una difesa aggiuntiva, non la prova di un traversal
oggi raggiungibile. La variante `.part` fissa proposta eliminerebbe il temporaneo
unico e il rename atomico attuali, con rischi su scritture concorrenti. I test
verificano anche che sostituire un symlink non scriva sul suo bersaglio; non si
pretende di proteggere il volume da un amministratore locale ostile.

Le nuove quote nginx sono 20 richieste/minuto con burst 10 per login/2FA/recupero,
e 5/minuto con burst 5 per registrazione; i limiti backend restano più specifici.
Le risposte generate da nginx sono `429` JSON, mentre quelle del backend mantengono
codice applicativo e `Retry-After`. `/api/auth/me`, logout e annullamento 2FA non
consumano le quote. Un IP condiviso condivide anche la quota: è un limite noto.

### Method security e cookie

Le annotazioni Spring proteggono le chiamate che attraversano il proxy, incluse
quelle da altri bean; il documento descrive erroneamente queste ultime come
un caso che salta il proxy. La self-invocation interna allo stesso oggetto è invece
il caso tipico non intercettato. Restano i controlli espliciti sulle cancellazioni;
il chiamante non può autorizzarsi passando semplicemente un principal admin.
Le letture del catalogo restano disponibili ai membri. Ricerca/import AniList
restano riservati agli admin come già previsto dalle regole URL.

`Strict` omette il cookie sulla navigazione iniziale da un sito esterno: questo
non dimostra da solo che una SPA si rompa, perché le richieste successive possono
riottenere la sessione. Tuttavia la premessa “nessun flusso da siti terzi” è ormai
inesatta per i link email; `Lax` con CSRF sulle mutazioni resta la scelta attuale.

## Verifiche e limiti

Aggiunti test del resolver con risposte miste IPv4/IPv6, cambio dell'indirizzo
tra risoluzioni, mantenimento dell'hostname TLS, client HTTP reale senza retry
e senza redirect. Aggiunti test sui nomi traversal e sulla sostituzione dei
symlink. I nuovi test di integrazione chiamano direttamente i proxy Spring per
verificare ogni metodo protetto, anche passando un principal admin da un contesto
non autorizzato. Il test nginx usa l'immagine effettiva del Dockerfile, verifica
sintassi, header, spoofing, separazione delle quote, session reads, JSON 429 e
conservazione delle risposte upstream.

CI esegue l'intera suite backend con PostgreSQL/Testcontainers, frontend,
script operativi e nginx in container isolati. Trivy blocca vulnerabilità HIGH o
CRITICAL per cui esiste una correzione; un esito verde non significa assenza di
vulnerabilità senza fix o di problemi logici. Dependabot aprirà PR dopo il merge
della sua configurazione sul branch predefinito: i digest fissati richiedono
manutenzione e revisione degli aggiornamenti.

Verifiche manuali prima del merge: login con e senza 2FA; errore e recupero del
secondo fattore; import AniList con anteprima e copertina salvata; caricamento di
una copertina; operazioni personali di un membro; cambio password e revoca su un
secondo browser; link email aperto da un browser già autenticato. Nessuna migrazione
DB né nuova variabile ambiente è necessaria per queste modifiche.

## Riferimenti tecnici

- [Apache HttpClient: DnsResolver](https://hc.apache.org/httpcomponents-client-5.6.x/current/httpclient5/apidocs/org/apache/hc/client5/http/DnsResolver.html)
- [Spring Security: method security](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- [nginx: limit_req](https://nginx.org/en/docs/http/ngx_http_limit_req_module.html)
- [GitHub: opzioni Dependabot](https://docs.github.com/en/code-security/reference/supply-chain-security/dependabot-options-reference)
- [MDN: Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie)
- [OWASP: Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)

I digest delle immagini sono stati letti il 14 settembre 2026 dall'API Docker Hub
`/v2/repositories/library/<immagine>/tags/<tag>`; gli SHA delle Actions provengono
dai riferimenti Git del repository ufficiale, dereferenziando il tag annotato
Trivy v0.36.0 fino al commit.

## Riscontro delle prime scansioni CI

La prima esecuzione ha superato backend (anche integrazione e 2FA) e frontend,
ma ha individuato pacchetti preesistenti nelle immagini per cui esistono fix:
OpenSSL 3.5.7 → 3.5.8, libexpat 2.8.2/2.8.3 → 2.8.4 e libuuid 2.42.1 → 2.42.3-r1.
Sono stati aggiunti aggiornamenti mirati nelle immagini applicative, mantenendo
il ramo Alpine e la verifica delle firme dei repository. La base rimane pinnata;
i pacchetti correttivi sono ottenuti dal repository del ramo al momento del build,
quindi il digest della base non implica che l'intera build sia riproducibile byte
per byte. Quando le immagini ufficiali incorporeranno i fix si potranno togliere
questi aggiornamenti dopo aver ricontrollato la scansione.

Tomcat embedded 11.0.24 è stato aggiornato alla patch 11.0.25 tramite la proprietà
BOM comune a tutti i moduli Tomcat, dopo verifica degli advisory Apache. Le tre
segnalazioni CRITICAL dello scanner riguardano CVE-2026-65182, CVE-2026-65905 e
CVE-2026-68525. La gravità attribuita da Apache e i prerequisiti differiscono da
quelli dello scanner: MangaShelf usa Spring Security e non gli autenticatore
DIGEST/FORM del container, quindi questi numeri non dimostrano da soli un bypass
sfruttabile nell'app. L'aggiornamento di manutenzione elimina comunque quei
componenti obsoleti. La proprietà andrà rimossa quando il BOM Boot li includerà.
Fonte: https://tomcat.apache.org/security-11.html

Il database ufficiale ha riportato 9 segnalazioni OS e 22 sulla libreria Go
incorporata in `gosu`. La presenza di una versione Go non prova che le funzioni
vulnerabili siano raggiungibili nel programma: il progetto upstream richiede
una verifica con `govulncheck` prima di attribuire tali CVE a gosu.
Fonte: https://github.com/tianon/gosu/blob/master/SECURITY.md
Queste segnalazioni restano visibili nella scansione del DB; non sono state
aggiunte esclusioni o dichiarazioni di non sfruttabilità non dimostrate.

Il test nginx inizialmente ha incontrato un errore intermittente di `nc`, che
poteva terminare alla chiusura dello stdin prima di leggere la risposta.
Il fixture mantiene ora stdin aperto fino alla chiusura HTTP dal server, senza
ritentare o ignorare le richieste fallite. Usa inoltre lo stage `runtime-base`
del Dockerfile, includendo gli aggiornamenti OS realmente distribuiti.
