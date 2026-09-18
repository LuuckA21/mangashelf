# Backup operativi: stesso schema di Kutt

Questa configurazione sostituisce i kit esterni con script versionati insieme
all'applicazione. Non occorre scaricare o installare l'archivio MangaShelf
proposto in precedenza. Kutt non viene modificato.

## Struttura e comandi

| Elemento | Kutt | MangaShelf |
| --- | --- | --- |
| Applicazione | `/srv/apps/kutt` | `/srv/apps/mangashelf` |
| Backup locale | `backup.sh` | `backup.sh` |
| Invio cloud | `cloud-backup.sh` | `cloud-backup.sh` |
| Directory copie | `/srv/backups/kutt/daily-…` | `/srv/backups/mangashelf/daily-…` |
| Ultimo backup locale | `last-success`, un percorso | `last-success`, un percorso |
| Ultimo successo cloud | `last-cloud-success.json` | `last-cloud-success.json` |
| Configurazione Restic | `~/.config/restic/kutt` | `~/.config/restic/mangashelf` |
| Servizio e timer utente | `kutt-backup.*` | `mangashelf-backup.*` |
| Esecuzione | 03:40 Europe/Zurich | 04:15 Europe/Zurich |
| Conservazione locale | Almeno 14 giorni | Almeno 14 giorni |
| Conservazione cloud | 14 giornalieri / 8 settimanali / 12 mensili | Identica |

I due orari sono separati per distribuire il carico. `Persistent=true` recupera
le scadenze perse. Ogni copia locale e una directory
`daily-AAAAMMGG-HHMMSS-NANOSECONDI`, con accesso riservato all'utente runtime.
Il timer non introduce un ritardo casuale; AccuracySec e un minuto, come Kutt.

Ogni copia contiene `database.dump`, `covers.tar.gz`, `.env`, `docker-compose.yml`,
eventuali override Compose standard, `manifest.txt` con commit Git, ID/digest
immagini, `SHA256SUMS` e `.managed-mangashelf-backup-v1`. Non archivia i layer
Docker: per ricostruire il server servono il codice del commit indicato e le
immagini di base ancora disponibili nel registry o archiviate separatamente.
Il backup non include file personalizzati fuori da questi percorsi.

## Prerequisiti

- Eseguire come `appsvc`, con Git, Docker Compose rootless, flock, sha256sum.
- Servizio Docker utente e lingering gia attivi.
- Per Swiss Backup: Restic (configurazione verificata con 0.18), jq, openssl e
  `~/.config/restic/kutt/env.sh` gia funzionante.
- `/srv/backups/mangashelf` deve appartenere ad appsvc ed essere privato.
- Applicazione gia avviata con le tre immagini disponibili e `.env` definitivo.
- Non eseguire deploy, restore o modifiche a `.env`/Compose durante il backup.

Gli entry point operativi individuano il socket rootless in `/run/user/UID`.
Non usano `source .env`: il formato Compose non e uno script shell.

## Installazione o migrazione dell'istanza gia configurata

Prima aggiornare il checkout al branch approvato (o a master dopo il merge).
Non occorre ricostruire i container: questo aggiornamento riguarda soltanto
script e configurazione operativa.

Su prd-apps-01, come opsadmin:

```bash
sudo install -d -o appsvc -g appsvc -m 0700 /srv/backups/mangashelf
sudo loginctl enable-linger appsvc
sudo -u appsvc -H bash /srv/apps/mangashelf/install-backup.sh --cloud-from-kutt
```

L'installer:

1. Controlla che non ci sia un backup in corso e rifiuta override systemd sconosciuti.
2. Legge le credenziali S3 di Kutt, senza stamparle, e deriva il percorso separato
   del repository sostituendo il solo suffisso `/kutt` con `/mangashelf`.
3. Genera una password Restic distinta, oppure conserva quella MangaShelf gia
   presente. Una configurazione esistente diversa non viene sovrascritta.
4. Inizializza il repository solo se non accessibile come repository esistente;
   Restic rifiuta di reinizializzare un repository gia creato. Qualsiasi errore
   interrompe la procedura prima di modificare le unita systemd.
5. Conserva le unita precedenti, il vecchio drop-in e `last-success` in una
   directory privata `~/.config/systemd/user/mangashelf-backup-previous-…`.
6. Installa le unita dal repository; il nuovo `50-swiss-backup.conf` usa
   `ExecStartPost=…/cloud-backup.sh`, sostituendo anche il drop-in del kit precedente.
7. Esegue subito un backup locale seguito da cloud, rotazione e controllo.
8. Solo dopo il successo abilita/riavvia il timer e mostra gli esiti.

Se il primo test fallisce, l'installer mostra il journal ed esce con errore.
Le nuove unita restano installate per consentire la diagnosi; le precedenti sono
conservate nel percorso stampato. Un timer gia attivo puo ritentare alla prossima
scadenza. Non viene dichiarata riuscita un'installazione con upload fallito.

Le precedenti `daily/`, `weekly/`, `secrets/`, le cartelle `mangashelf-…` e
`ops-local/` non vengono eliminate. La nuova rotazione ignora questi percorsi.
Il link locale `backups`, se gia esistente, puo restare: non e richiesto per
l'installazione standard in `/srv/apps/mangashelf`.

`last-success` cambia dal vecchio formato chiave=valore al percorso singolo
usato da Kutt, soltanto al primo nuovo backup riuscito. La vecchia pianificazione
`scripts/backup-scheduled.sh` e il relativo test sono stati rimossi. Se un'istanza
usa ancora quel comando in systemd o cron, migrare al servizio attuale con
l'installer e disattivare eventuali richiami cron precedenti. Sul server gia
migrato non occorre reinstallare le unita per questa pulizia. I vecchi dump
restano ripristinabili e nessuna copia esistente viene eliminata.

`scripts/backup.sh` resta il componente interno per dump PostgreSQL e archivio
copertine, usato dal backup completo e dalla copia di sicurezza pre-ripristino.
`scripts/restore.sh` resta il comando per ripristinare database e copertine.
I relativi test sono mantenuti: questi file fanno parte del sistema attuale.

Per disabilitare esplicitamente l'invio cloud e installare il solo servizio
locale, usare `./install-backup.sh --local`. Non cancella il repository remoto
ne la password. Per aggiornare script gia installati basta aggiornare Git;
se cambiano le unita, rieseguire l'installer con l'opzione desiderata.

## Segreti e recuperabilita

Le nuove copie locali includono `.env` e quindi password database, credenziali
SMTP e chiave 2FA. Restic le cifra insieme ai dati prima di inviarle a Swiss
Backup. La copia locale e protetta dai permessi, ma non cifrata su disco.

Conservare in un gestore di password esterno al server:

- password indicata da `RESTIC_PASSWORD_FILE` in
  `~/.config/restic/mangashelf/env.sh` (nuove installazioni: `password` nella
  stessa directory);
- credenziali S3, endpoint, regione e indirizzo del repository;
- accesso al repository Git e istruzioni di ripristino.

Non incollare i segreti nei log, nelle issue o nella chat. I file Restic/S3 non
sono inclusi nei backup dell'applicazione. La perdita della password Restic
impedisce il recupero. Le credenziali S3 sono copiate dalla configurazione Kutt:
se vengono ruotate, aggiornare entrambe le configurazioni. La cifratura non
rende il repository immutabile.

## Esecuzione manuale e controlli

Da opsadmin, per eseguire l'intera catena:

```bash
sudo -u appsvc -H bash <<'SCRIPT'
set -euo pipefail
export XDG_RUNTIME_DIR="/run/user/$(id -u)"
export DBUS_SESSION_BUS_ADDRESS="unix:path=$XDG_RUNTIME_DIR/bus"
systemctl --user start mangashelf-backup.service
systemctl --user show mangashelf-backup.service -p Result -p ExecMainStatus
systemctl --user list-timers mangashelf-backup.timer --no-pager
cat /srv/backups/mangashelf/last-success
cat /srv/backups/mangashelf/last-cloud-success.json
SCRIPT
```

`backup.sh` crea solo la copia locale; `cloud-backup.sh` invia l'ultima copia
completa. La rotazione locale tocca soltanto cartelle dirette `daily-…` con
marcatore valido, piu vecchie di almeno 14 giorni, e parte dopo un backup riuscito.
Le directory estranee, i link simbolici e il nuovo backup non vengono eliminati.
Il backup pre-deploy usa lo stesso `backup.sh` e rientra quindi nella conservazione
locale; l'invio cloud avviene con il servizio o richiamando `cloud-backup.sh`.

Il servizio oneshot torna normalmente inactive dopo l'esecuzione. Verificare
**Result=success**: ExecMainStatus si riferisce a ExecStart e puo essere 0 anche
quando l'ExecStartPost cloud fallisce. `last-success` certifica solo il locale;
`last-cloud-success.json` certifica upload, conservazione e controllo remoti.

Un upload parziale o fallito non avvia la rotazione cloud. Un errore di rotazione
o controllo lascia invariato il marker cloud, anche se lo snapshot puo gia
esistere. La conservazione usa host/tag mangashelf e raggruppa per host,tags,
non per percorso: le cartelle giornaliere diverse non creano gruppi indipendenti.
Le categorie giornaliera/settimanale/mensile si sovrappongono. `restic check`
verifica la struttura del repository; `restic check --read-data` rilegge tutti i
blocchi e richiede piu tempo/traffico. Non sono previste notifiche o retry orari.

Il lock `.backup.lock` serializza nuovo backup locale, cloud e verifica del
ripristino. `scripts/restore.sh` e l'esecuzione diretta del componente interno
`scripts/backup.sh` non acquisiscono questo lock. Il deploy chiama il nuovo
backup, ma non mantiene il lock durante la ricostruzione: evitare attivita
operative concorrenti, come per Kutt. Dump e copertine sono acquisiti in sequenza,
non come snapshot atomico dell'intera applicazione: per un punto di ripristino
strettamente coerente sospendere le scritture durante la copia.

## Prova di ripristino isolata

```bash
sudo -u appsvc -H bash /srv/apps/mangashelf/verify-backup.sh
```

Recupera l'ultimo snapshot cloud riuscito, verifica checksum (incluso `.env`) e
archivio copertine, poi importa il dump in un PostgreSQL temporaneo usando la
stessa immagine del DB attivo. Il container ha un nuovo volume anonimo, nessuna
rete e nessuna porta pubblicata. Il metodo trust riguarda esclusivamente questo
container isolato. Container, volume e file temporanei vengono eliminati alla
fine, anche in caso di errore. Nessun volume di produzione viene montato.

`last-restore-test.json` viene aggiornato soltanto dopo il successo. Il test
verifica il recupero e il database, ma non login, SMTP, 2FA o UI di una seconda
istanza dell'applicazione. Non ripristina automaticamente `.env` sull'app attiva.

Per un vero recupero usare il commit del manifest, configurare l'istanza di
destinazione e usare `scripts/restore.sh PERCORSO_BACKUP`. Questo comando e
**distruttivo** per i dati dell'istanza di destinazione: provarlo prima su una
macchina separata. Reimpostare correttamente le variabili rootless, gli IP di bind,
il proxy fidato e il dominio. Conservare la chiave 2FA del database ripristinato.

## Personalizzazioni e test

`MANGASHELF_BACKUP_ROOT` consente un'altra directory; il default e
`/srv/backups/mangashelf` per il checkout standard, altrimenti `backups` nel
checkout. `MANGASHELF_LOCAL_RETENTION_DAYS` (default 14) modifica l'eta locale.
`MANGASHELF_BACKUP_HOST` modifica l'host Restic (default hostname).
`MANGASHELF_RESTIC_CONFIG_DIR`, `MANGASHELF_KUTT_CONFIG_DIR` e
`MANGASHELF_SYSTEMD_USER_DIR` consentono percorsi alternativi durante
installazione/test. Gli override d'ambiente necessari a runtime vanno anche
configurati nell'unita systemd; non sono ereditati dalla shell dell'installer.

I test operativi usano filesystem/lock reali e sostituti per Docker, Restic e
systemd: non inviano dati a provider e non usano database reali. Verificano
rotazione limitata, corruzione, errori cloud, migrazione dei kit precedenti,
conservazione della password e isolamento/pulizia del ripristino. La prova
reale sul proprio server e necessaria dopo l'installazione.
