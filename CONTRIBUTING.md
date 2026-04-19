# Contributing

Thanks for your interest in contributing to **db-migration-quality-checker**.

## Quick start

1. Fork the repo and clone your fork.
2. Make sure you have **JDK 25** and **Docker** (for integration tests) installed.
3. Build and run unit tests:
   ```bash
   ./mvnw test
   ```
4. Run the full suite (unit + fast MySQL integration test):
   ```bash
   ./mvnw verify
   ```
5. Run the slow DB2 + MySQL end-to-end suite (takes several minutes on a cold start):
   ```bash
   ./mvnw verify -Dgroups=integration
   ```

## Development workflow

- Keep changes small and focused.
- Add or update unit tests for every behaviour change.
- Integration-level behaviour that touches the DB2 catalog queries should be covered by `QualityCheckerIT`; pure MySQL side changes belong in `MySqlRepositoryIT`.
- Format before committing:
  ```bash
  ./mvnw spotless:apply
  ```
- CI enforces `spotless:check`; a PR with formatting drift will fail.

## Commit and PR conventions

- Use [Conventional Commits](https://www.conventionalcommits.org/) (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`) — release automation relies on them.
- One logical change per PR. Link the issue it closes.
- Fill in the pull request template — description, testing notes, breaking-change flag.

## Reporting issues

- Bugs: open a GitHub issue with the *Bug report* template; include DB versions, sample `tables.csv`, and the failing report output when possible.
- Security vulnerabilities: follow [`SECURITY.md`](SECURITY.md) instead of opening a public issue.

## Releasing

Maintainers only. Releases are driven by `release.yml` on tag push — see `CHANGELOG.md` for the format.

## Code of Conduct

By participating you agree to abide by the [Code of Conduct](CODE_OF_CONDUCT.md).
