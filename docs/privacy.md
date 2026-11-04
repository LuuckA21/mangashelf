# Privacy — mangashelf

Inventory of personal data for this project (records of processing). Keep it current: update it in the
same PR that adds a field, a processor or a purpose. See project-standards 09-privacy-legal.md.

Controller: <name, address>, privacy contact: <email>
EU representative (GDPR art. 27): <name / "not appointed, because …">
Last reviewed: 2026-11-04

## Processing activities

| Purpose | Data categories | Data subjects | Legal basis (GDPR art. 6) | Retention |
|---------|-----------------|---------------|---------------------------|-----------|
| User accounts and login | Username, password hash (bcrypt), session data (Spring Session JDBC, expires after 14 days of inactivity) | Users | Contract (b) | Until account deletion; sessions 14 days |
| Providing the service | Collection (series, editions, owned volumes) | Users | Contract (b) | Until account deletion |
| Security logs | IP address, timestamps (reverse proxy access logs) | Visitors, users | Legitimate interest (f): security | 14–30 days |
| Backups | All of the above | All | Legitimate interest (f): data safety | 12 months |

## Processors

| Processor | Purpose | Data location | Transfer basis | DPA |
|-----------|---------|---------------|----------------|-----|
| Hetzner Online GmbH | Hosting, server backups | Germany / Finland | EU: adequate | <link> |
| Infomaniak Network SA (Swiss Backup) | Encrypted backups (restic) | Switzerland | Switzerland (no transfer for a Swiss controller; adequate for the EU) | <link> |
| GitHub | Source code (no personal data) | USA | DPF | — |

## Technical and organizational measures

- HTTPS everywhere (HSTS at the proxy); encrypted backups (restic)
- Passwords stored only as bcrypt hashes; session cookies HttpOnly, SameSite=Lax, CSRF protection on
- Secrets in Bitwarden
- Access to servers: SSH keys only, separate admin and app users (06)
- Personal data deleted physically; deleted data stays in encrypted backups until they expire (max 12 months)
- Expired sessions are removed by the Spring Session JDBC cleanup job
- Log retention: Nginx Proxy Manager 14–30 days

## Requests and incidents log

| Date | Type (access / erasure / breach / …) | Summary | Outcome / answered on |
|------|--------------------------------------|---------|-----------------------|
