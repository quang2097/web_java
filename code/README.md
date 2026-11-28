# Supermarket management

Vue 3 + Vite frontend, Java 17+ Spring Boot REST backend, Spring Security JWT, Spring Data JPA/Hibernate, PostgreSQL and Flyway. Implemented features include sign-in/out, role-scoped account management, password recovery requests, catalog management, stock imports, cash/VietQR checkout, invoice history and printing, reports, forecasting, recommendations and AI assistance.

See [architecture and requirement coverage](docs/architecture.md) for the ER diagram, role permissions and concurrency design, and [feature audit](docs/feature-audit.md) for the completed polish and operational limits.

## Prerequisites

- JDK 17+ and Maven 3.6.3+.
- Node.js 22 LTS recommended and npm.
- PostgreSQL 17, either installed locally or using Docker Compose.

## Start on Windows

### Normal startup on this machine

1. Keep your installed PostgreSQL server running on `localhost:5432`. The `supermarket` database has already been created on this machine.
2. Double-click `Start-Supermarket.cmd` in the project folder. It uses the portable Java/Maven in `.tools`, loads your saved credentials, builds the frontend, and starts Spring Boot.
3. Wait for `Started SupermarketApplication`, then open [Supermarket](http://localhost:8080).
4. Sign in with your **website account**. Admin credentials are selected from process environment variables first, then `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` in `.env`, then the saved launcher credentials. Set the username/password together in the same source.

Docker is not required for this setup. Do not run `docker compose up -d` alongside the installed PostgreSQL server: both use port 5432. The standard launcher now uses the installed database without a Docker prompt.

Keep the launcher window open while using the app. Press Ctrl+C in that window to stop the app; PostgreSQL remains running. If the app is already running, another launch reports its URL and exits without starting a second server. Configuration changes take effect after stopping and restarting the running app.

### Admin password in `.env` does not work

Changing `APP_ADMIN_PASSWORD` does not automatically overwrite an existing PostgreSQL account's password. To intentionally apply the configured admin password, stop the running launcher with Ctrl+C, then double-click **`Reset-Admin-Password.cmd`**. It starts the application and resets only the configured existing ADMIN account's password. It preserves disabled status and never promotes a staff/manager account. Previously issued JWTs for the changed account become invalid. Future starts can use `Start-Supermarket.cmd` as usual. No passwords are printed in startup logs.

The equivalent command is `powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File scripts/start-supermarket.ps1 -ResetAdminPassword`. A direct Java launch can opt in with `APP_ADMIN_RESET_PASSWORD=true`; remove that variable after the one intended reset. A direct Java/Maven launch reads `.env` when present; process environment variables take priority.

### The three different credentials

| Credential | Where to use it | Meaning |
| --- | --- | --- |
| PostgreSQL role `postgres` and server password | Launcher database setup / pgAdmin server connection | Existing database login. This machine's saved login was verified against localhost:5432. |
| Website username and password | Supermarket sign-in page | Separate account created during initial setup. Website passwords require 12-72 printable ASCII characters; usernames use 3-64 letters, digits, dot, underscore or hyphen. |
| pgAdmin master password | pgAdmin only | Unlocks pgAdmin's saved passwords. It is not a database username or the website password. |

Double-click `Configure-Database.cmd` to update the saved PostgreSQL login. This changes only the launcher's connection settings; it does not change a PostgreSQL password or an existing website account. For pgAdmin, use host `localhost`, port `5432`, maintenance database `postgres`, username `postgres`, and the matching server password.

Double-click `Find-Postgres-Password.cmd` to look for a saved local `postgres` password in the launcher settings or your PostgreSQL password file. It displays a masked password with a Show password option. It does not recover an unknown password from a database hash or reset passwords.

Saved credentials and a stable JWT secret are kept in `.tools/launcher-config.xml`. Passwords and the JWT secret are encrypted for the Windows account/computer that saved them. Keep this file for future launches. Deleting it repeats setup, changes the JWT secret, and does not reset existing database or website passwords. The first launch without this file prompts separately for the database login and website account. An inherited `DB_URL` overrides the default database address.

### Logs and troubleshooting

Each launch writes its own file under `.tools/logs/startup-<timestamp>-<process-id>.log`. The launcher prints the exact path; `.tools/latest-startup.txt` contains the latest path. Logs include frontend build output, Spring/Maven output, and launcher errors after toolchain activation. You can read a log while the server runs.

The old `.tools/last-startup.log` is a legacy file and may remain occupied by an older running instance. New launches do not overwrite it. Stop the old launcher with Ctrl+C before restarting; there is no need to delete the log.

| Message | Action |
| --- | --- |
| Already running | Open http://localhost:8080. Stop the existing launcher first if you need to restart. |
| Port 8080 already in use | Close the other server before launching another copy. |
| Password authentication failed | Run `Configure-Database.cmd`; use the PostgreSQL role and server password, not website credentials or a pgAdmin master password. |
| Database supermarket does not exist | Create a database named `supermarket` in pgAdmin, owned by the configured PostgreSQL role. This was already done on this machine. |
| Connection refused | Check that your installed PostgreSQL service is running on port 5432. |
| Docker engine / named pipe error | Use the normal local launcher. Docker is unnecessary for the current setup. |
| Maven not recognized | Use the launcher, or activate the portable toolchain using the command below. |

### Optional terminal commands

From the project root, activate the portable Java/Maven for the current PowerShell session:

```powershell
. .\scripts\activate.ps1
```

To run with the same saved settings as the double-click launcher:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File .\scripts\start-supermarket.ps1
```

For a fresh checkout without `.tools`, run `scripts/setup-local-java.ps1` first (internet required). Node.js/npm is also required. The database must exist and its configured role must have permission to create application tables. Flyway creates the schema; initial bootstrap creates the website account and never resets an existing account password.

### Optional Docker alternative

Only use this instead of a native PostgreSQL instance. Docker Desktop must be installed and running, and port 5432 must be free. The supplied Compose database uses role `supermarket`; configure the launcher for that role and the container's database password before using it.

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -STA -File .\scripts\start-supermarket.ps1 -UseDocker
```

An existing Docker volume keeps its original database password; changing the launcher password does not reset it. Docker data remains after the app stops.

For frontend development, keep the backend on port 8080 and run `npm.cmd run dev` in `frontend` in another terminal. Vite proxies `/api` to the backend. Vite preview serves static files only.

## REST API

| Method | Path | Contract |
| --- | --- | --- |
| POST | `/api/auth/login` | JSON `{ "accountName": "admin", "password": "your-password" }`; 200 returns `{accessToken, tokenType, expiresIn, user}` |
| GET | `/api/auth/me` | Bearer token required; returns `{accountName, fullName, roles}` |
| POST | `/api/auth/logout` | Bearer token required; revokes token and returns 204 |
| POST | `/api/auth/change-password` | Bearer token required; changes password and invalidates prior JWTs |
| POST | `/api/auth/password-requests` | Public; creates a password reset request verified against employee code and full name |
| GET/POST | `/api/pos/skus`, `/api/pos/invoices`, `/api/pos/invoices/{id}/pay` | POS barcode lookup, invoice creation, cash/VietQR transfer completion |
| POST | `/api/pos/ai/recognize-product` | Multipart image upload for Gemini Vision product recognition |
| GET/POST | `/api/management/categories`, `/api/management/products`, `/api/management/imports` | Catalog, pricing, inventory and stock import management |
| GET/POST | `/api/management/reports/summary`, `/api/management/reports/forecast`, `/api/management/ai/chat` | Revenue KPIs, 4-week WMA demand forecasting and AI data assistant |
| GET/POST | `/api/management/accounts`, `/api/management/password-requests` | Role-scoped account creation and password-request approval (`MANAGER` → `STAFF`, `ADMIN` → all) |

Set `GEMINI_API_KEY` in the environment to enable live Google Gemini responses for vision recognition and natural-language analytics; when unset, the analytics assistant automatically uses the built-in local analytics engine.

Malformed input returns 400; incorrect credentials return 401. Missing, expired, tampered or revoked tokens return 401; insufficient role permissions return 403. Public login and recovery requests are rate limited (429 with `Retry-After`). No password hashes are returned by the API. JWTs expire after 15 minutes and remain in browser memory only: reloading requires a new login. Forgot password creates an employee-verified request for a manager or administrator to review.

VietQR requires configured receiving bank details and cashier confirmation that funds arrived; it does not provide bank settlement verification. Without those details, use cash. Draft invoices remain available in **Hoá đơn** after leaving POS or signing in again. Repeating an identical completed payment returns the saved result without another stock deduction. Catalog, import costs and cash use whole VNĐ amounts.

New databases start without sample products or sales. Set `APP_DEMO_ENABLED=true` only for demonstration databases. This does not remove sample data already present. A direct Java launch reads `.env` automatically. If no JWT secret is supplied, the server generates a random key for that process; use a stable random `JWT_SECRET` for deployment. The Windows launcher retains its encrypted saved key.

## Database queries

Repositories use ORM instead of hand-written authentication SQL. The derived JPA query `findByAccountName` generates a parameterized lookup equivalent to:

```sql
SELECT a.*, e.* FROM accounts a
JOIN employees e ON e.id = a.employee_id
WHERE a.account_name = ?;
```

Bootstrap saves employee/account entities. Logout saves a token ID and expiration in `revoked_tokens`; a scheduled task cleans expired records. Each authenticated request also checks account state and revocation, so this design deliberately uses database validation alongside JWT verification.

The previous file-based H2 database is not migrated. This version initializes a fresh PostgreSQL schema. H2 is available for isolated tests and local QA in PostgreSQL compatibility mode; PostgreSQL is the operational database.

## Verification

```powershell
cd frontend
npm.cmd run build
cd ../backend
mvn test
```

Backend tests cover ORM authentication, invalid credentials, malformed input, disabled accounts, JWT expiry/tampering, role permissions, role changes, logout revocation and competing stock updates. H2 tests do not replace a real PostgreSQL smoke test. With PostgreSQL running, verify successful and failed login, `/api/auth/me`, logout and rejection of the revoked token.

If Java and Maven are unavailable, optional `scripts/setup-local-java.ps1` downloads a portable JDK 21 and Maven into the ignored `.tools` directory without changing your system installation. Run `scripts/test-backend.ps1` afterward to test with that toolchain. Downloads require internet access.

Use HTTPS when deploying. The built-in authentication limiter is per process and uses the direct client address; multi-instance deployments should apply a shared limiter at the trusted gateway. Database credentials, JWT secret and merchant secrets belong outside source control.
