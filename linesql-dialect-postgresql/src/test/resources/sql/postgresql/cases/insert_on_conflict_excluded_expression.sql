INSERT INTO mart.users AS u (id, name, updated_at)
SELECT d.id, d.name, d.updated_at FROM staging.users_delta AS d
ON CONFLICT (id) WHERE deleted_at IS NULL DO UPDATE
SET name = COALESCE(excluded.name, u.name)
WHERE excluded.updated_at > u.updated_at
RETURNING u.id;
