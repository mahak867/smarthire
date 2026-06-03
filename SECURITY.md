# Security Policy — SmartHire

## Supported Versions

| Version | Security Fixes |
|---------|---------------|
| 1.x     | ✅ Active      |

## Implemented Security Controls

### Authentication & Authorisation
- **BCrypt** password hashing at strength 12 — never stored or logged in plaintext
- **JWT** access tokens (15 min) + refresh tokens (7 days)
- Refresh tokens stored as **SHA-256 hash** in database — raw token never persisted
- **Token rotation** on every refresh — reuse detection triggers full user lockout
- **Role-based access control** (`ADMIN`, `RECRUITER`, `CANDIDATE`) enforced at method level with `@PreAuthorize`
- **Audit logging** of every auth event (login, logout, failed login, password change)

### Transport & Headers
- **HTTPS-only** in production (HSTS: `max-age=31536000; includeSubDomains`)
- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `X-XSS-Protection: 1; mode=block`
- `Referrer-Policy: strict-origin-when-cross-origin`
- `Content-Security-Policy: default-src 'self'`

### Rate Limiting (Redis-backed sliding window via Bucket4j)
| Endpoint | Limit |
|---|---|
| `POST /auth/login` | 5 requests / IP / 15 minutes |
| `POST /auth/register` | 3 requests / IP / hour |
| All other endpoints | 100 requests / user / minute |

Returns `429 Too Many Requests` with `Retry-After` header on breach.

### Input Validation
- `@Valid` on every request body — Zod on the frontend, Jakarta Bean Validation on the backend
- HTML-escaped text fields before DB persistence
- Parameterised JPQL queries only — zero string concatenation
- File upload: PDF MIME type + 5 MB size limit enforced at controller level
- UUID parameters validated before any DB query

### Dependency Security
- OWASP Dependency Check runs in CI — fails on CVSS score ≥ 7
- Trivy container scan on every build — fails on CRITICAL vulnerabilities
- All dependencies pinned to exact versions in `pom.xml` and `package.json`

### Secrets Management
- Zero hardcoded credentials anywhere in the codebase
- All secrets via environment variables
- GitHub Actions uses encrypted repository secrets
- `.env.example` contains placeholder values only — `.env` is `.gitignore`d

## Reporting a Vulnerability

**Do not open a public GitHub issue for security vulnerabilities.**

Please email **security@smarthire.app** with:
1. Description of the vulnerability
2. Steps to reproduce
3. Potential impact

We will respond within **48 hours** and aim to release a patch within **7 days** for critical issues.

You will be credited in the release notes unless you prefer anonymity.
