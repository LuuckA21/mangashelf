# Autenticazione a due fattori

2FA facoltativa per account tramite app TOTP (per esempio un'app compatibile con
`otpauth://`), QR generato localmente nel browser e 10 codici di recupero monouso.
L'email rimane il canale per verifica indirizzo, reset password e avvisi; non è
un secondo fattore e non consente di disattivare la 2FA.

## Configurazione del server

Prima di abilitare la funzione, generare **una sola volta** una chiave casuale:

```bash
openssl rand -base64 32
```

Inserire il risultato nel `.env` del server, senza pubblicarlo o committarlo:

```dotenv
APP_2FA_ENCRYPTION_KEY=valore-generato
```

Conservare questa chiave nel gestore di segreti, separata dai dump del database.
Serve anche dopo il ripristino di un backup. **Non rigenerarla a ogni deploy.**
Una chiave vuota disabilita nuove attivazioni, ma non rimuove la protezione dagli
account già configurati: il loro accesso 2FA fallisce con servizio non disponibile.
Una chiave presente ma malformata impedisce l'avvio. La rotazione delle chiavi
richiede una migrazione dedicata dei ciphertext e non è automatica.

```bash
./deploy.sh feature/totp-2fa
```

Il deploy applica V12 e ricostruisce backend e frontend. Eseguire prima il backup
previsto dallo script. La migrazione aggiunge colonne e una tabella, senza
modificare gli account esistenti: la 2FA resta disattivata finché ciascun utente
non la configura. Il rollback a codice privo di 2FA **aggirerebbe la protezione**
degli account già iscritti: dopo la prima attivazione usare correzioni compatibili
in avanti, non versioni precedenti del backend.

In produzione mantenere HTTPS e `APP_COOKIE_SECURE=true`, il proxy fidato esatto
e l'orologio del server sincronizzato (NTP). I codici dipendono dall'ora UTC;
il fuso orario dell'interfaccia non cambia il risultato.

## Utilizzo

1. In **Impostazioni → Autenticazione a due fattori**, scegliere Attiva 2FA.
2. Inserire la password corrente, scansionare il QR o copiare la chiave nell'app.
3. Confermare con un codice valido. Fino a questa conferma la 2FA non è attiva.
4. Salvare i 10 codici di recupero, mostrati una sola volta, prima di chiudere
   la schermata. Il download è facoltativo e avviene solo premendo il pulsante.
5. Al login, inserire password e poi codice app oppure un codice di recupero.
   La password non rimane nello stato React quando compare la seconda schermata.

Ogni codice app accettato è monouso: per un'altra operazione immediata attendere
il codice successivo oppure usare un codice di recupero. Ogni codice di recupero
vale per un solo utilizzo, anche se presentato contemporaneamente in due sessioni.

Disattivazione e rigenerazione dei codici richiedono nuovamente password e secondo
fattore. Anche cambio password autenticato e richiesta eliminazione account lo
richiedono quando la 2FA è attiva. Le modifiche alla 2FA invalidano altre sessioni
e verifiche login pendenti, mantenendo autenticata la sessione che le ha confermate.

Il reset password via email mantiene segreto TOTP e codici di recupero:
per accedere con la nuova password serve ancora il secondo fattore.
Se si perdono sia l'app sia tutti i codici di recupero, non esiste un bypass
automatico via email o pannello amministrativo. Qualsiasi recupero operativo
richiede una procedura amministrativa separata con verifica dell'identità;
non cancellare manualmente i campi di sicurezza come normale soluzione.

Con SMTP attivo vengono inviati avvisi per attivazione, disattivazione e
rigenerazione codici. Non contengono segreti e partono solo dopo il commit.
Un problema di consegna viene registrato senza dati sensibili e non annulla
la modifica già completata. Gli avvisi sono best effort, non una coda durevole.

## Garanzie e confini di sicurezza

- TOTP RFC 6238: HMAC-SHA1, segreto casuale da 160 bit, 6 cifre, periodo 30 secondi,
  tolleranza di un solo intervallo prima/dopo quello corrente. I vettori ufficiali
  SHA1 sono verificati anche oltre l'anno 2038.
- Segreti attivi e di configurazione cifrati AES-256-GCM, nonce casuale da 96 bit,
  dati associati contenenti ID account e versione del formato. Copiare il valore
  cifrato su un altro account o alterarlo non produce un segreto utilizzabile.
- Codici di recupero casuali da 128 bit ciascuno; nel database solo SHA-256.
  La DELETE del codice avviene nella stessa transazione della verifica, sotto
  lock dell'account, come l'avanzamento del contatore TOTP anti-riuso.
- Configurazione provvisoria valida 10 minuti, legata alla versione dell'account;
  non è esposta da endpoint GET e non può sostituire una 2FA già attiva.
- Password valida su account 2FA crea solo una sessione preliminare di 5 minuti.
  Nessun `SecurityContext` autenticato, nessun privilegio e nessuna password/hash
  in tale sessione. Non esiste un token di verifica accettato da altri browser:
  la verifica è legata alla sessione server che ha completato il primo fattore.
- Massimo 5 tentativi per verifica. Limiti aggiuntivi indipendenti dal login
  password: 5 errori/account, 30/IP, 15 minuti di blocco e 16 operazioni
  contemporanee per istanza. Rifare il primo fattore non azzera questi contatori.
  I tentativi vengono riservati prima del lavoro e non annullati dal rollback DB.
- Verifica sotto lock di esistenza, stato, ruolo e versione dell'account: reset,
  disabilitazione o cambiamenti di autorizzazione invalidano verifiche pendenti.
- Rotazione dell'ID sessione al completamento; tutte le mutazioni richiedono CSRF.
  Risposte sensibili ereditano il divieto di cache di Spring Security. Nessun
  servizio QR esterno, token nel localStorage o segreto nelle email di avviso.
- V12 cancella i codici di recupero in cascata solo insieme al relativo account.

TOTP non è resistente al phishing in tempo reale; passkey/WebAuthn sarebbero un
possibile passo successivo. I contatori sono locali alla singola istanza e si
azzerano al riavvio; per più repliche occorre un limite condiviso e una strategia
di sessioni condivise. Non è una protezione contro compromissione del server o
furto di una sessione già autenticata. Il runtime deve poter accedere alla chiave
di cifratura; la separazione della chiave protegge soprattutto un dump DB isolato.

## Verifica prima del merge

La PR esegue unit test crittografici, test di integrazione su PostgreSQL reale,
regressioni frontend, build/lint e audit npm. La suite include autenticazione
parziale, assenza chiave, CSRF, doppio utilizzo concorrente di codici, scadenza,
rate limit indipendente, reset password, rotazione e operazioni sensibili.

Prova manuale su un account di test:

1. Configurare la chiave e attivare 2FA; controllare QR e inserimento manuale
   nell'app scelta, salvando i codici di recupero.
2. Accedere in una finestra privata: password corretta, codice errato, poi un
   codice valido. Verificare che prima del secondo fattore il catalogo non sia accessibile.
3. Usare un codice di recupero una volta; ripresentarlo in un altro login e
   controllare che sia rifiutato. Un codice diverso deve funzionare.
4. Resettare la password via email e verificare che la 2FA sia ancora richiesta.
5. Rigenerare i codici; controllare che i precedenti non funzionino più e che
   un'altra sessione aperta richieda un nuovo login.
6. Disattivare con password e codice nuovo; controllare login standard e avviso
   email. Non utilizzare l'unico account amministrativo per prove di perdita chiave.

Riferimenti: [RFC 6238](https://www.rfc-editor.org/rfc/rfc6238),
[OWASP MFA Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html).
