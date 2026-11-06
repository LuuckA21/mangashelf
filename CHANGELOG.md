# Changelog

All notable changes to this project. Format: [Keep a Changelog](https://keepachangelog.com/),
versions: [SemVer](https://semver.org/). The `## [X.Y.Z]` section of a version becomes its
GitHub Release notes (see `.github/workflows/release.yml`), so write it for users.

## [Unreleased]

### Changed

- The publisher of an edition is now chosen from a list managed by admins on the new Amministrazione page. Existing editions start without a publisher.

### Added

- Purchase lists: monthly lists of volumes to buy, with release dates, EUR and CHF prices, discount and totals; mark rows booked, move rows between lists, close a list (booked rows become owned) and reopen it.

## [0.1.0] - 2026-11-04

First release.

### Added

- Personal manga collection tracked volume by volume on a shared catalog of series and editions.
- Catalog: series and editions (type, status, volume count), with a cover for each edition.
- Collection: search and filters (type, status, only incomplete), covers, progress and missing volumes per edition.
- Shelf of volumes toggled with mouse or keyboard, saved at once; "mark all" and "none".
- Accounts created by the admin with a temporary password, changed at first login; admin user management.
- Account page with password change, personal data export and account deletion; privacy page.
