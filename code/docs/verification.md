# Verification record

## Feature polish audit — 2026-11-28

- Reviewed all six screens and the authentication, catalog, stock, POS, reporting, AI and account services. Findings and changes are recorded in [feature audit](feature-audit.md).
- Final backend run: **24 tests passed, zero failures/errors/skips**, including eight new feature regressions and two authentication rate-limit tests. The final run also rejects fractional integer inputs and missing account-state/stock-threshold fields.
- Flyway V1–V5 applied successfully on fresh H2 databases. Receipt snapshots preserve descriptions after SKU/catalog changes; password requests use optimistic versioning.
- Production frontend build passed after the UI changes.
- Browser QA ran on a separate server at port 8091 using an in-memory H2 database with explicit demo data, no live Gemini key and no configured bank account. The user's PostgreSQL data was not changed.
- Verified sign-in/out, unavailable AI/transfer states, cart locking, correct receipt descriptions, draft recovery from history, cash payment (17,500 received as 20,000; change 2,500), SKU editing, forecast import with an entered cost, reports, local AI response and account-screen rendering. Browser console reported no errors or warnings during the final flow.
- Stopped the temporary QA server after verification. Live Gemini, real bank settlement and PostgreSQL upgrade testing were not performed. VietQR settlement remains a cashier confirmation workflow.

## Admin login fix — 2026-11-21

- Confirmed that the saved launcher admin password differed from `.env`, and the launcher previously gave saved credentials priority.
- Fixed website credential precedence to process environment, then `.env`, then saved launcher settings. Incomplete credential pairs are rejected instead of being mixed between sources.
- Added explicit administrator password reset through `Reset-Admin-Password.cmd` / `-ResetAdminPassword`. Ordinary startup preserves stored passwords. Reset refuses non-admin accounts and preserves disabled status.
- PowerShell credential precedence regression checks passed.
- All 14 backend tests passed with the portable Java 21/Maven toolchain, including five new bootstrap/reset tests, JWT authentication, concurrency and supermarket workflows.
- Vue production build passed (46 transformed modules).
- Restarted the local application against PostgreSQL 17.11 and applied the configured administrator password intentionally. Live API verification: `.env` login **200**, `/api/auth/me` **200** with ADMIN role, logout **204**. The old saved launcher password returned **401**. No credentials or bearer tokens were printed.

The older record below describes the initial setup limitations, which no longer apply to the backend checks above.

Checked on 2026-11-05:

- `npm.cmd install`: completed; final Vue 3.5.44 / Vite 6.4.5 dependency report found **0 vulnerabilities**.
- `npm.cmd run build`: passed with 11 transformed modules and production HTML/CSS/JavaScript emitted to `frontend/dist`.
- Maven POM parsed successfully as XML.
- Backend tests were **not executed**. The machine provides Java 8 and no Maven; this project requires Java 17+. A portable JDK download was attempted but timed out after 120 seconds, having received only part of the archive. The setup helper now permits a longer download for subsequent manual use.
- PostgreSQL migration/startup and real database login were **not runtime verified**. PostgreSQL/Docker was not available through the current command environment. Integration tests use H2 in PostgreSQL compatibility mode and still need to be run.
- Browser visual QA was **not completed**: the browser preview tool timed out. The local Vite process started successfully and was stopped afterward.

After preparing Java/Maven and PostgreSQL, run the commands in the project README, execute `mvn test`, and smoke-test login, account identity, role restrictions and revoked-token rejection against PostgreSQL.
