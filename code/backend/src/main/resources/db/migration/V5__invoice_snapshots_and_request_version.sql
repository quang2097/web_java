-- Preserve receipt descriptions when the catalog is edited later.
ALTER TABLE invoice_lines ADD COLUMN sku_code VARCHAR(64);
ALTER TABLE invoice_lines ADD COLUMN item_name VARCHAR(281);
ALTER TABLE invoice_lines ADD COLUMN item_unit VARCHAR(32);
UPDATE invoice_lines SET sku_code = (SELECT s.code FROM skus s WHERE s.id = invoice_lines.sku_id),
    item_unit = (SELECT s.unit FROM skus s WHERE s.id = invoice_lines.sku_id),
    item_name = (SELECT TRIM(p.name || ' ' || s.variant) FROM skus s JOIN products p ON p.id = s.product_id WHERE s.id = invoice_lines.sku_id);
-- Competing reviews must not overwrite each other's decision.
ALTER TABLE password_requests ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
