# Security Policy

## Supported Versions

GraphORM is currently in pre-release (0.1.0-SNAPSHOT). No stable version has been released.

| Version | Supported |
|---------|-----------|
| 0.1.0-SNAPSHOT | Development only — not for production use |

---

## Reporting a Vulnerability

**Do not report security vulnerabilities through public GitHub issues.**

If you discover a security vulnerability, please report it privately:

1. **GitHub Security Advisories** — Use [GitHub's private vulnerability reporting](https://github.com/JithendraKavala/graphorm/security/advisories/new) if available.
2. **Email** — Contact the maintainers directly (see `CODEOWNERS` for contact details).

Please include:
- A description of the vulnerability
- Steps to reproduce
- Potential impact
- Any suggested remediation

We will acknowledge your report within 48 hours and aim to provide a fix within 14 days for critical issues.

---

## Security Design Principles

GraphORM is designed with these security properties:

1. **Parameterized SQL always** — GraphORM never concatenates user-controlled input into SQL queries. All values are bound via JDBC prepared statements.
2. **Identifier validation** — Dynamic column and table identifiers are validated against trusted schema metadata before use in queries.
3. **No automatic schema exposure** — GraphQL fields are only exposed through explicit schema mapping, not by scanning database columns automatically.
4. **Sensitive field protection** — Fields can be marked sensitive to prevent exposure through generated inputs or introspection.
5. **Query complexity limits** — GraphQL query depth and complexity are treated as resource-consumption risks. Limits are configurable.
6. **No credential logging** — Database credentials and sensitive configuration values are never written to logs.

---

## Known Limitations

- Authorization (who can access which records) is not built into GraphORM v1. It is the responsibility of the consuming application to implement field-level and record-level authorization.
- GraphQL query depth limits are configurable but not enforced by default in the current development snapshot.
