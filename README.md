# MangaShelf

MangaShelf è un'applicazione self-hosted multiutente per gestire un catalogo manga condiviso, le edizioni possedute e le liste mensili degli acquisti.

Il progetto usa Spring Boot, PostgreSQL, React/Vite, Nginx e Docker Compose. I metadati generali delle opere possono essere importati da AniList; collezioni e acquisti restano separati per utente.

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

Avvia e costruisci i container:

```bash
docker compose up -d --build --wait
docker compose ps
```

Il primo account registrato diventa amministratore. Dopo averlo creato, imposta `APP_REGISTRATION_ENABLED=false` in `.env` e applica la configurazione:

```bash
docker compose up -d --wait
```

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
```

GitHub Actions esegue automaticamente tutte queste verifiche sulle pull request.

## Dati persistenti

Docker Compose usa due volumi nominati:

- `db-data`: database PostgreSQL;
- `covers`: copertine locali.

Non eseguire `docker compose down -v`: l'opzione `-v` elimina entrambi i volumi e quindi i dati persistenti.
