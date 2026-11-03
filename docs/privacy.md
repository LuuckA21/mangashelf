# Privacy — mangashelf

Inventory of personal data for this project (records of processing). Keep it current: update it in the
same PR that adds a field, a processor or a purpose. See project-standards 09-privacy-legal.md.

Controller: <name, address>, privacy contact: <email>
EU representative (GDPR art. 27): <name / "not appointed, because …">
Last reviewed: YYYY-MM-DD

## Processing activities

| Purpose | Data categories | Data subjects | Legal basis (GDPR art. 6) | Retention |
|---------|-----------------|---------------|---------------------------|-----------|
| User accounts and login | email, name, password hash, session | Users | Contract (b) | Until account deletion |
| Providing the service | <data the app stores> | Users | Contract (b) | Until account deletion |
| Security logs | IP address, user agent, timestamps | Visitors, users | Legitimate interest (f): security | 30 days |
| Error tracking | Technical context, user ID (no PII) | Users | Legitimate interest (f): stability | 90 days |
| Invoices | Name, address, amounts | Customers | Legal obligation (c) | 10 years (CO art. 958f) |
| Backups | All of the above | All | Legitimate interest (f): data safety | 12 months |

## Processors

| Processor | Purpose | Data location | Transfer basis | DPA |
|-----------|---------|---------------|----------------|-----|
| Hetzner Online GmbH | Hosting, server backups | Germany / Finland | EU: adequate | <link> |
| Infomaniak Network SA (Swiss Backup) | Encrypted backups (restic) | Switzerland | Switzerland (no transfer for a Swiss controller; adequate for the EU) | <link> |
| <Sentry / other> | Error tracking | <EU region?> | <adequate / DPF / SCC> | <link> |
| <Email provider> | Transactional email | <region> | <…> | <link> |
| GitHub | Source code (no personal data) | USA | DPF | — |

## Technical and organizational measures

- HTTPS everywhere; encrypted backups (restic); secrets in Bitwarden
- Access to servers: SSH keys only, separate admin and app users (06)
- Personal data deleted physically; scheduled purge jobs: <list>
- Log retention: Nginx <N> days, app logs <N> days

## Requests and incidents log

| Date | Type (access / erasure / breach / …) | Summary | Outcome / answered on |
|------|--------------------------------------|---------|-----------------------|
