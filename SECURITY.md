# Security Policy

## Supported versions

Only the latest minor release receives security updates. Older releases are
maintained on a best-effort basis.

| Version | Supported |
| ------- | --------- |
| 1.x     | yes       |
| < 1.0   | no        |

## Reporting a vulnerability

**Do not open a public GitHub issue.**

Please report suspected vulnerabilities privately via GitHub's
[Security Advisories](https://docs.github.com/en/code-security/security-advisories/guidance-on-reporting-and-writing/privately-reporting-a-security-vulnerability)
on this repository, or email the maintainers listed in the repo's profile.

When reporting please include:

- Affected version(s).
- A short description of the issue and its impact.
- Reproduction steps or a proof-of-concept if available.

We aim to acknowledge reports within **5 working days** and to publish a fix
(or a mitigation) within **30 days** of a confirmed, high-severity report.

## Handling sensitive configuration

This project reads database credentials from environment variables. Never
commit real credentials — the defaults in `application.yml` are placeholders
for local development only.
