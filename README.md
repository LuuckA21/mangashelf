# MangaShelf

MangaShelf è un'applicazione self-hosted multiutente per gestire un catalogo manga condiviso, le edizioni possedute e le liste mensili degli acquisti.

Il progetto usa Spring Boot, PostgreSQL, React/Vite, Nginx e Docker Compose. I metadati generali delle opere possono essere importati da AniList; collezioni e acquisti restano separati per utente.

L'interfaccia è disponibile in italiano e inglese. Ogni account può scegliere
la propria lingua dalle impostazioni; la preferenza viene conservata sul server
e quindi segue l'utente anche su altri dispositivi.

## Ricerca e importazione AniList

Le ricerche riuscite (anche senza risultati) sono conservate in memoria per
5 minuti, fino a 500 combinazioni di titolo e limite. Spazi iniziali/finali e
maiuscole non creano voci duplicate. La cache è condivisa dagli amministratori
della stessa istanza e si svuota al riavvio; lo stato “Già presente” viene sempre
ricalcolato dal catalogo. L’importazione continua a richiedere metadati aggiornati.

In caso di indisponibilità, errore GraphQL o risposta non valida, l’interfaccia
mostra un errore distinto da “nessun risultato”. Un HTTP 429 avvia una pausa
condivisa fra ricerche e importazioni, rispettando Retry-After (secondi o data
HTTP, massimo un giorno; 60 secondi se assente/non valido). Gli altri errori
avviano una pausa di 30 secondi, o quella indicata da Retry-After.
Le ricerche già in cache restano disponibili tramite API durante la pausa.

Il limite locale risponde subito invece di tenere richieste in attesa.
L’interfaccia indica il tempo rimanente e riabilita i pulsanti alla scadenza:
il nuovo tentativo è manuale, senza ripetizioni automatiche delle importazioni.
Catalogo e inserimento manuale restano utilizzabili.

## Requisiti

- Docker Engine con il plugin `docker compose`;
- Git;
- un reverse proxy HTTPS, per esempio Nginx Proxy Manager, per l'uso in produzione;
- spazio persistente sufficiente per il database, le copertine e i backup.

## Primo avvio

Clona il repository e crea la configurazione locale:

```bash
git clone https://github.com/LuuckA21/mangashelf.git
cd mangashelf
cp .env.example .env
```

Modifica `.env`, impostando almeno:

- una password PostgreSQL lunga e casuale;
- `BIND_ADDRESS` con l'IP LAN del server MangaShelf;
- `TRUSTED_PROXY` con l'IP o la rete del reverse proxy;
- `APP_COOKIE_SECURE=true` quando l'applicazione è pubblicata in HTTPS.
- temporaneamente `APP_REGISTRATION_ENABLED=true` per creare il primo account.

Avvia e costruisci i container:

```bash
docker compose up -d --build --wait
docker compose ps
```

La registrazione è chiusa per impostazione predefinita. Il primo account
registrato diventa amministratore; dopo averlo creato, ripristina subito
`APP_REGISTRATION_ENABLED=false` in `.env` e applica la configurazione:

```bash
docker compose up -d --wait
```

## Gestione account

Ogni utente può cambiare lingua e password dalla pagina **Impostazioni**. Il
cambio password disconnette tutte le sessioni attive, compreso il dispositivo
da cui viene eseguito, e richiede quindi un nuovo accesso.

Gli amministratori vedono la pagina **Utenti**, dalla quale possono assegnare o
rimuovere il ruolo amministratore e attivare o disattivare un account. Le
modifiche invalidano immediatamente le sessioni dell'utente interessato. Non è
possibile modificare il proprio ruolo o stato, né rimuovere l'ultimo
amministratore attivo.

Ogni effettivo cambio di ruolo o stato viene salvato nello storico **Audit**, con
amministratore, utente interessato, valori precedente e nuovo e data. Il
registro è consultabile soltanto dagli amministratori e mostra i 100 eventi più
recenti; le operazioni rifiutate o prive di modifiche non producono eventi.

Gli account non vengono cancellati definitivamente: la disattivazione conserva
collezioni, liste acquisti e storico associati all'utente.

## Reverse proxy

Il reverse proxy deve inoltrare il traffico HTTPS verso `BIND_ADDRESS:HTTP_PORT`. Il database non viene pubblicato sulla rete host; solo Nginx espone la porta configurata.

`TRUSTED_PROXY` deve contenere esclusivamente l'indirizzo IP o il CIDR del proxy autorizzato. Non usare `0.0.0.0/0`.

## Aggiornamento e deploy

Lo script di deploy accetta il branch da pubblicare in modo esplicito:

```bash
./deploy.sh master
```

Senza argomenti aggiorna il branch corrente:

```bash
./deploy.sh
```

Prima di modificare il codice, lo script:

- verifica che il branch esista su `origin`;
- rifiuta modifiche locali tracciate e aggiornamenti non fast-forward;
- crea un backup verificato di database e copertine;
- ricostruisce i container e attende gli health check;
- controlla direttamente backend e frontend;
- salva commit precedente, commit pubblicato e percorso del backup in
  `../.mangashelf-last-deploy`.

Se il deploy fallisce senza aver introdotto migrazioni Flyway, ripristina il
codice precedente con:

```bash
./deploy.sh --rollback
```

Il rollback automatico viene bloccato quando il deploy modifica una migrazione
SQL: tornare al vecchio codice dopo un cambiamento del database può essere
pericoloso. In questo caso lo script mostra il backup pre-deploy da conservare e
verificare prima su un'istanza separata.

Per cambiare il tempo massimo di attesa degli health check, espresso in secondi:

```bash
MANGASHELF_HEALTH_TIMEOUT=300 ./deploy.sh master
```

## Backup

Il backup include:

- dump PostgreSQL in formato custom;
- archivio delle copertine;
- checksum SHA-256;
- manifest con data UTC e commit Git.

Esegui:

```bash
./scripts/backup.sh
```

Il risultato viene salvato in `backups/mangashelf-AAAAMMGGTHHMMSSZ`. Per scegliere un'altra destinazione:

```bash
./scripts/backup.sh /percorso/dei/backup
```

La directory contiene dati personali e deve essere conservata con accesso limitato, preferibilmente anche su un supporto esterno al server. Il file `.env` non viene incluso: salvalo separatamente in un gestore di segreti o in un archivio cifrato.

Un backup non è considerato affidabile finché non è stato copiato fuori dal server e provato almeno una volta con il ripristino.

### Backup automatici con systemd

Il timer incluso nel repository crea un backup ogni giorno alle 03:30, con un
ritardo casuale massimo di 15 minuti. Se il server è spento all'orario previsto,
`Persistent=true` avvia il backup al successivo avvio.

Installa il servizio per l'utente che gestisce MangaShelf:

```bash
mkdir -p ~/.config/systemd/user
cp ops/systemd/mangashelf-backup.service ~/.config/systemd/user/
cp ops/systemd/mangashelf-backup.timer ~/.config/systemd/user/
systemctl --user daemon-reload
systemctl --user enable --now mangashelf-backup.timer
systemctl --user list-timers mangashelf-backup.timer
```

Il servizio presuppone che il repository si trovi in `~/mangashelf`. Per
eseguire i timer utente anche senza una sessione aperta, controlla:

```bash
loginctl show-user "$USER" -p Linger
```

Se il risultato è `Linger=no`, abilitalo una sola volta:

```bash
sudo loginctl enable-linger "$USER"
```

Esegui subito un backup supervisionato e controllane il risultato:

```bash
systemctl --user start mangashelf-backup.service
systemctl --user status mangashelf-backup.service --no-pager
journalctl --user -u mangashelf-backup.service -n 100 --no-pager
cat backups/last-success
```

I backup sono conservati in `backups/daily` e `backups/weekly`. Per impostazione
predefinita vengono mantenuti gli ultimi 7 giornalieri e gli ultimi 4
settimanali; la copia settimanale viene creata la domenica. Le copie settimanali
usano hard link, quindi restano valide anche dopo la rimozione della copia
giornaliera senza duplicare immediatamente gli stessi dati sul disco.

Per cambiare la conservazione, crea un override del servizio:

```bash
systemctl --user edit mangashelf-backup.service
```

Inserisci, per esempio:

```ini
[Service]
Environment=MANGASHELF_DAILY_RETENTION=14
Environment=MANGASHELF_WEEKLY_RETENTION=8
```

Poi applica la modifica con `systemctl --user daemon-reload`. L'ultimo esito
positivo è registrato in `backups/last-success`; in caso di errore vengono
conservati data e codice di uscita in `backups/last-failure` e nei log di
systemd. Un backup automatico può essere ripristinato passando allo script il
percorso completo, per esempio `backups/daily/mangashelf-AAAAMMGGTHHMMSSZ`.

## Ripristino

Il ripristino sostituisce completamente database e copertine correnti. Per impostazione predefinita lo script crea prima un ulteriore backup di sicurezza in `backups/pre-restore`.

```bash
./scripts/restore.sh backups/mangashelf-AAAAMMGGTHHMMSSZ
```

Lo script:

1. verifica checksum e formato degli archivi;
2. richiede di digitare `RESTORE`;
3. crea un backup di sicurezza;
4. ferma frontend e backend;
5. ricrea il database e ripristina le copertine;
6. riavvia i servizi e attende gli health check.

In un recupero di emergenza, se il database corrente è illeggibile e il backup di sicurezza non può essere creato, è disponibile l'opzione esplicita:

```bash
./scripts/restore.sh backups/mangashelf-AAAAMMGGTHHMMSSZ --skip-safety-backup
```

Per un'esecuzione automatizzata già supervisionata si può aggiungere `--yes`; questa opzione elimina soltanto la conferma testuale, non le verifiche.

## Controlli dopo un ripristino

```bash
docker compose ps
docker compose exec -T backend wget -qO- http://localhost:8080/actuator/health
docker compose exec -T db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;"'
```

Accedi poi dall'interfaccia e verifica catalogo, copertine, collezione e una lista acquisti.

## Test del progetto

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm run format:check
npm test
npm run build
```

Backend (richiede Docker per Testcontainers):

```bash
mvn -B -f backend/pom.xml verify
```

Script operativi, senza modificare Docker o dati reali:

```bash
./scripts/test-backup-restore.sh
./scripts/test-deploy.sh
./scripts/test-scheduled-backup.sh
```

GitHub Actions esegue automaticamente tutte queste verifiche sulle pull request.

## Dati persistenti

Docker Compose usa due volumi nominati:

- `db-data`: database PostgreSQL;
- `covers`: copertine locali.

Le copertine vengono pubblicate con permessi `0644`, così il processo Nginx
può leggerle dal volume condiviso. All'avvio il backend ripara anche i permessi
delle immagini già presenti salvate dalle versioni precedenti con `0600`;
non è necessario reimportare le opere. Il recupero esclude link simbolici,
file temporanei e sottodirectory.

Non eseguire `docker compose down -v`: l'opzione `-v` elimina entrambi i volumi e quindi i dati persistenti.

### Email di registrazione e recupero password

L'invio SMTP è opzionale e disattivato per impostazione predefinita. Per attivarlo,
aggiungere al `.env` del server (vedi anche `.env.example`):

```dotenv
APP_EMAIL_ENABLED=true
APP_PUBLIC_URL=https://manga.example.com
MAIL_FROM=noreply@example.com
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=your-smtp-user
SMTP_PASSWORD='your-smtp-password'
SMTP_AUTH=true
SMTP_STARTTLS=true
SMTP_SSL=false
```

Sostituire dominio, mittente e credenziali con quelli del proprio servizio SMTP.
`MAIL_FROM` deve essere un indirizzo autorizzato dal provider; configurare anche
SPF/DKIM secondo le sue istruzioni. Non committare `.env` né pubblicare credenziali
SMTP nei log o nelle issue. In Compose le virgolette singole proteggono eventuali
caratteri `$` della password dall'interpolazione.

`APP_PUBLIC_URL` è l'origine HTTPS pubblica di MangaShelf, senza percorsi, query o
frammenti; per prove locali è ammesso `http://localhost:porta`. I link non vengono
costruiti dagli header della richiesta. Per SMTP con TLS implicito sulla porta 465
impostare `SMTP_PORT=465`, `SMTP_STARTTLS=false`, `SMTP_SSL=true`. Per un server di
cattura email locale come Mailpit si possono disabilitare autenticazione e TLS;
non usare questa configurazione per un servizio SMTP pubblico. Le connessioni SMTP
hanno timeout di 5 secondi e verifica dell'identità del server TLS. I controlli
salute dell'applicazione non dipendono dal provider SMTP, così un suo disservizio
non blocca il catalogo o il deploy.

Dopo la configurazione ricreare i container con lo script di deploy:

```bash
./deploy.sh feature/account-emails
```

Comportamento con email abilitate:

- I nuovi account ricevono una conferma nella lingua scelta (italiano/inglese) e
  possono accedere solo dopo averla completata. Il link vale 24 ore. L'apertura del
  link mostra un pulsante di conferma: una scansione automatica dell'email non lo
  consuma. Il primo account conserva la regola di assegnazione amministratore.
- La migrazione V9 considera già verificati gli account esistenti, mantenendone
  l'accesso. Nessuna email viene inviata automaticamente agli utenti esistenti.
- Da login sono disponibili «Password dimenticata?» e «Reinvia email di conferma».
  Il recupero vale per account abilitati e verificati; per un account in attesa
  bisogna prima reinviare/completare la conferma. La chiusura delle registrazioni
  non impedisce il recupero o la conferma di account già creati.
- I reset scadono dopo 30 minuti. Ogni link è monouso; un nuovo invio riuscito
  sostituisce il precedente. Cambi password o modifiche amministrative che
  incrementano la versione delle sessioni rendono inutilizzabili i vecchi reset.
  Il reset invalida tutte le sessioni e richiede un nuovo accesso.
- Il database conserva solo hash SHA-256 di token casuali da 256 bit. Il token
  viene passato nel frammento del link, che non raggiunge gli access log HTTP,
  rimosso dalla voce corrente della cronologia e inviato via POST con protezione
  CSRF. Ricaricando una pagina di conferma/reset occorre riaprire il link originale.
- Richieste di recupero e reinvio restituiscono sempre la stessa risposta per
  indirizzi assenti, disabilitati o non idonei. L'invio avviene in background per
  non rivelare l'esistenza dell'account attraverso il tempo di risposta SMTP.
  Limiti per istanza: 20 richieste/tentativi di conferma/reset per IP ogni ora e
  un invio per indirizzo al minuto, con massimo 100 richieste in coda e 2 worker.
- La coda in memoria non sopravvive a un riavvio e non effettua retry automatici:
  l'utente può richiedere un nuovo invio dopo un minuto. Un errore SMTP conserva
  il link precedente e produce un avviso privo di indirizzi/token nei log del
  backend. Se fallisce l'email iniziale, la registrazione viene annullata con 503.

Con `APP_EMAIL_ENABLED=false` la registrazione mantiene il comportamento precedente
(accesso senza conferma) e non sono disponibili nuovi invii email. Gli account già
in attesa di conferma restano in attesa: disabilitare SMTP non li verifica. I link
validi già consegnati possono ancora essere completati.

Verifiche dopo il deploy:

1. Accedere con un account esistente e verificare che collezione e impostazioni
   siano disponibili.
2. Con registrazioni aperte, creare un account di prova con una propria email:
   prima della conferma il login deve fallire; dopo il click deve riuscire.
3. Richiedere un reset, impostare una password nuova, controllare l'accesso e
   verificare che le sessioni precedenti e il vecchio link non funzionino più.
4. Provare il reinvio della conferma con un secondo account non verificato e
   controllare che il link precedente non sia più valido.
5. Controllare ricezione/spam e lingua dei messaggi. Gli automated test usano un
   trasporto SMTP simulato: non verificano la consegna del provider reale.

Riferimento per lo starter SMTP e i timeout:
[Spring Boot — Sending Email](https://docs.spring.io/spring-boot/reference/io/email.html).

### Eliminazione del proprio account con conferma email

Con SMTP attivo, **Impostazioni → Elimina account** permette di richiedere una
conferma all'indirizzo dell'account. La richiesta richiede una sessione valida e
la password attuale; non accetta un utente o un indirizzo destinatario scelti dal
client. L'email è disponibile in italiano e inglese, con lo stesso formato HTML
(e alternativa testuale) delle altre email account.

Il link monouso è valido 30 minuti. Aprirlo mostra username/email e le conseguenze;
la cancellazione avviene solo dopo aver selezionato la casella di conferma e premuto
**Elimina definitivamente il mio account**. Il link può essere aperto su un altro
dispositivo senza sessione, ma viene rifiutato se il browser è autenticato con un
account diverso. Il token resta nel frammento URL e nel corpo POST, mai nella query
string; la pagina lo rimuove dalla voce corrente della cronologia. Dopo un refresh
occorre riaprire il link originale.

La migrazione V10 aggiunge hash del token, scadenza, versione delle sessioni e data
dell'ultimo invio. Un nuovo invio riuscito (massimo uno al minuto per account)
sostituisce il precedente. Un errore SMTP annulla la sostituzione e il cooldown.
Cambi password, reset e modifiche amministrative di ruolo/stato invalidano i link
pendenti. Le richieste e le conferme usano anche il limite IP delle email e CSRF.
Disattivare SMTP impedisce nuove richieste; i link validi già ricevuti restano usabili.

La cancellazione rimuove dalla banca dati attiva:

- l'account, le credenziali e i token;
- i volumi della collezione personale;
- le liste acquisti personali e le relative righe.

Il catalogo, le copertine condivise e i dati degli altri utenti restano intatti.
Gli eventi dell'audit amministrativo restano come storico delle azioni, ma i
riferimenti all'account eliminato diventano null e i relativi username vengono
sostituiti da `[deleted]`. Le sessioni esistenti vengono rifiutate alla richiesta
successiva. I backup già creati mantengono i dati fino alla normale rotazione;
questa funzione non riscrive gli archivi di backup.

L'ultimo amministratore abilitato e con email verificata non può eliminarsi: occorre
prima nominare un altro amministratore con accesso funzionante. Il controllo viene
ripetuto alla conferma e condivide il lock PostgreSQL con le modifiche di ruolo e
stato, evitando che richieste simultanee lascino l'istanza senza amministratore.

Deploy del branch di prova:

```bash
./deploy.sh feature/account-deletion
```

Test manuale con un **account di prova**, dopo aver verificato il backup:

1. Aprire Impostazioni, richiedere l'email con la password attuale e verificare che
   l'account resti utilizzabile prima della conferma.
2. Aprire il link e controllare account mostrato e avviso; chiudere la pagina senza
   confermare deve lasciare tutti i dati invariati.
3. Riaprire il link, confermare la cancellazione e verificare il ritorno al login,
   il rifiuto delle vecchie sessioni e l'inutilizzabilità del link già consumato.
4. Con un altro utente controllare catalogo, collezione e liste acquisti; devono
   restare disponibili. L'ultimo amministratore deve ricevere un blocco esplicito.
