# Verification record

Checked on 2026-11-05:

- `npm.cmd install`: completed; final Vue 3.5.44 / Vite 6.4.5 dependency report found **0 vulnerabilities**.
- `npm.cmd run build`: passed with 11 transformed modules and production HTML/CSS/JavaScript emitted to `frontend/dist`.
- Maven POM parsed successfully as XML.
- Backend tests were **not executed**. The machine provides Java 8 and no Maven; this project requires Java 17+. A portable JDK download was attempted but timed out after 120 seconds, having received only part of the archive. The setup helper now permits a longer download for subsequent manual use.
- PostgreSQL migration/startup and real database login were **not runtime verified**. PostgreSQL/Docker was not available through the current command environment. Integration tests use H2 in PostgreSQL compatibility mode and still need to be run.
- Browser visual QA was **not completed**: the browser preview tool timed out. The local Vite process started successfully and was stopped afterward.

After preparing Java/Maven and PostgreSQL, run the commands in the project README, execute `mvn test`, and smoke-test login, account identity, role restrictions and revoked-token rejection against PostgreSQL.
