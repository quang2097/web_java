# Supermarket: sign-in with the required project stack

Vue 3 + Vite frontend, Java 17+ Spring Boot REST backend, Spring Security JWT, Spring Data JPA/Hibernate, PostgreSQL and Flyway. The completed feature is sign-in/sign-out. The normalized schema and JPA entities also cover products, SKUs, warehouses, inventory, invoices, invoice lines, employees and customers.

See [architecture and requirement coverage](docs/architecture.md) for the ER diagram, role permissions, concurrency design and remaining POS/payment modules.

## Prerequisites

- JDK 17+ and Maven 3.6.3+.
- Node.js 22 LTS recommended and npm.
- PostgreSQL 17, either installed locally or using Docker Compose.

## Start on Windows PowerShell

From the project root, configure your local database password and optionally launch PostgreSQL using Docker:

```powershell
$env:DB_PASSWORD = Read-Host 'Local PostgreSQL password'
docker compose up -d
```

Alternatively create a database `supermarket` owned by a login role `supermarket` on your PostgreSQL server, with that password. The default connection is `jdbc:postgresql://localhost:5432/supermarket`; override `DB_URL` and `DB_USER` if needed. Java reads environment variables, not `.env` files. The supplied `.env.example` is for Docker Compose only. Compose binds PostgreSQL to localhost and retains its data in a named volume.

Build the Vue frontend before launching the backend:

```powershell
cd frontend
npm.cmd install
npm.cmd run build
cd ../backend
$randomBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($randomBytes)
$env:JWT_SECRET = [Convert]::ToBase64String($randomBytes)
$rng.Dispose()
$env:APP_ADMIN_USERNAME = 'admin'
$env:APP_ADMIN_PASSWORD = Read-Host 'Initial admin password (12–72 printable ASCII characters)'
mvn spring-boot:run
```

Open [the application](http://localhost:8080). Sign in with the admin account you supplied. Flyway creates the schema; the transactional bootstrap creates both an employee and the associated account, with a BCrypt password hash. It never resets an existing account password. Account names use 3–64 ASCII letters, digits, dot, underscore or hyphen. Passwords use printable ASCII characters up to 72 bytes. Remove `APP_ADMIN_USERNAME` and `APP_ADMIN_PASSWORD` from the environment after initialization. Retain the same securely stored `JWT_SECRET` across restarts and application instances; changing it invalidates tokens.

For a packaged application run `mvn clean package`, then `java -jar target/supermarket-1.0.0.jar`. Maven includes `frontend/dist/` in the jar.

For frontend development, keep the Java server on port 8080 and run `npm.cmd run dev` in `frontend` in another terminal. Vite proxies `/api` to the backend. The Vite preview command only serves static build output and has no backend proxy.

## REST API

| Method | Path | Contract |
| --- | --- | --- |
| POST | `/api/auth/login` | JSON `{ "accountName": "admin", "password": "your-password" }`; 200 returns `{accessToken, tokenType, expiresIn, user}` |
| GET | `/api/auth/me` | Bearer token required; returns `{accountName, fullName, roles}` |
| POST | `/api/auth/logout` | Bearer token required; revokes token and returns 204 |

Malformed input returns 400; incorrect credentials return 401. Missing, expired, tampered or revoked tokens return 401; insufficient role permissions return 403. No password hashes are returned by the API. JWTs expire after 15 minutes and remain in browser memory only: reloading requires a new login. Forgot password currently provides administrator guidance.

## Database queries

Repositories use ORM instead of hand-written authentication SQL. The derived JPA query `findByAccountName` generates a parameterized lookup equivalent to:

```sql
SELECT a.*, e.* FROM accounts a
JOIN employees e ON e.id = a.employee_id
WHERE a.account_name = ?;
```

Bootstrap saves employee/account entities. Logout saves a token ID and expiration in `revoked_tokens`; a scheduled task cleans expired records. Each authenticated request also checks account state and revocation, so this design deliberately uses database validation alongside JWT verification.

The previous file-based H2 database is not migrated. This version initializes a fresh PostgreSQL schema. H2 remains a **test-only dependency** for isolated tests in PostgreSQL compatibility mode.

## Verification

```powershell
cd frontend
npm.cmd run build
cd ../backend
mvn test
```

Backend tests cover ORM authentication, invalid credentials, malformed input, disabled accounts, JWT expiry/tampering, role permissions, role changes, logout revocation and competing stock updates. H2 tests do not replace a real PostgreSQL smoke test. With PostgreSQL running, verify successful and failed login, `/api/auth/me`, logout and rejection of the revoked token.

If Java and Maven are unavailable, optional `scripts/setup-local-java.ps1` downloads a portable JDK 21 and Maven into the ignored `.tools` directory without changing your system installation. Run `scripts/test-backend.ps1` afterward to test with that toolchain. Downloads require internet access.

Use HTTPS and login throttling when deploying. Database credentials, JWT secret and any future merchant secrets belong outside source control.
