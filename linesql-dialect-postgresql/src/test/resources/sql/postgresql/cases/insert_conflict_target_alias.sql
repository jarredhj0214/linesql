INSERT INTO mart.excluded AS "Existing" (id, name, previous_name)
SELECT d.id, d.name, d.previous_name
FROM staging.users_delta AS d
ON CONFLICT (id) DO UPDATE
SET name = excluded.name,
    previous_name = "Existing".name
WHERE "Existing".active
RETURNING "Existing".id AS user_id;
