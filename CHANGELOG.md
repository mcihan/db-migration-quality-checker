# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Apache 2.0 licence, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`, `CHANGELOG.md`.
- GitHub Actions workflows: `ci.yml` (build + unit + fast IT on push/PR), `it.yml` (nightly full IT with DB2), `release.yml` (tag-triggered release).
- Dependabot config covering Maven and GitHub Actions ecosystems.
- Issue and pull-request templates under `.github/`.
- `.editorconfig` to keep formatting consistent.
- Spotless + JaCoCo Maven plugins.
- Comprehensive unit test suite (61 tests) covering every comparison service, repository mapper, utility and the report writer.
- Integration tests under `src/test/java/.../integration/` using Testcontainers (MySQL fast path + optional DB2 end-to-end), with reuse enabled so first boot is paid only once.

### Changed
- Moved `Dockerfile` and `docker-compose.yml` into `docker/`.
- Docker image is now built locally via a multi-stage build; no private registry reference.
- Replaced silent `e.printStackTrace()` calls with proper SLF4J error logging.
- Test infrastructure: Lombok bumped to 1.18.38, Mockito configured to use the subclass mock maker for JDK 21+.

### Removed
- Vendored `db2jcc4-4.19.72.jar` (proprietary, now pulled via Maven dep only).
- `build_scripts/` (internal GoCD pipeline + Artifactory push script).

## [0.0.1] — initial internal release
- Baseline comparison checks: column names, column metadata, indexes, row count, random-row sampling.
- CLI runner, DB2 + MySQL JDBC repositories, report writer.
