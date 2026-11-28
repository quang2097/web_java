# Feature audit — 2026-11-28

The audit covered all six application screens, authentication/session handling, their REST controllers and services, schema migrations and the existing tests.

| Area | Issues addressed |
| --- | --- |
| POS | Locked cart during checkout, enforced stock/quantity limits, synchronized server prices, guarded barcode scanning, debounced search with stale-response protection, cleared stale recommendations and validated image type/size before upload. |
| Payments | Shared payment panel in POS and history; cash validation, explicit manual transfer confirmation, disabled unconfigured VietQR, QR-load errors, optional bank reference, retry recovery, and idempotent completed payment/cancellation. |
| Invoices | Reopen drafts from history, refresh selected details, keyboard-accessible selection, empty/loading/error states, receipt printing, and immutable item descriptions/codes/units after catalog edits. |
| Catalog | Product edits and active status, additional SKU creation, validated SKU editing, category rename/removal when unused, product search, submission guards and bounded fields. |
| Stock | Warehouse selection and creation, supplier creation/editing including contact information, editable/removable import lines, editable warning thresholds, actual costs required for forecast suggestions and integer-overflow/total limits. |
| Reports | Loading/error/retry handling, accessible daily revenue table, chart cleanup on navigation, forecasting restricted to sellable warehouse stock and active products. |
| Accounts | Unified password policy, preserved password whitespace, linked labels and autocomplete, submission guards and optimistic locking for competing password-request reviews. |
| AI | Disabled unavailable recognition UI, bounded image uploads, handled empty/multipart model responses and interrupted requests, local fallback for failures, and revenue included in mixed revenue/inventory questions. |
| Shared | Hash navigation supports browser back/forward; network timeout/error messages; stale unauthorized responses cannot clear a newer session; logout checks server status; bounded public-auth rate limiting; random signing key when unset; sample data requires explicit opt-in. |

## Verification and deployment limits

Automated regression coverage includes malformed nested input, payment retry behavior, insufficient cash, unconfigured transfer, cancelled drafts, receipt snapshots, warehouse stock/forecast consistency, password policy and authentication rate-limit windows. Tests use H2 compatibility mode; they are not a substitute for deployment testing against PostgreSQL.

VietQR is a QR presentation and cashier confirmation workflow. No automatic bank settlement, refund or payment-provider webhook integration is implemented. Live Gemini responses require a valid configured provider key; local analytics remains available. Receipt printing uses the browser print dialog.

The first created warehouse is the selling warehouse. Forecasts describe that warehouse; imports can target any warehouse. Stock transfers between warehouses are not implemented. Existing sample data is preserved; disabling demo initialization does not delete records. V5 backfills old receipt descriptions from the catalog as it exists at upgrade time.

For deployment, configure HTTPS, real bank details, a stable random signing key, backups and a shared authentication limiter if running multiple instances or behind a shared proxy. The bundled limiter is per process and trusts only the direct client address.
