INSERT INTO mart.users (tenant_id, email)
SELECT d.tenant_id, d.email FROM staging.users_delta AS d
ON CONFLICT (tenant_id, (lower(email)) COLLATE pg_catalog."C" pg_catalog.text_ops)
WHERE deleted_at IS NULL
DO NOTHING;
