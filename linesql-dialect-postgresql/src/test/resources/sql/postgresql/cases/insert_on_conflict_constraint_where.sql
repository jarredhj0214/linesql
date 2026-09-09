INSERT INTO mart.users (id, name)
SELECT s.id, s.name
FROM staging.users_delta s
ON CONFLICT ON CONSTRAINT users_pkey
DO UPDATE SET name = excluded.name
WHERE mart.users.deleted_at IS NULL;
