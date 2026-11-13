UPDATE mart.users AS "Existing"
SET name = d.name
FROM staging.users_delta AS d
WHERE "Existing".id = d.id
RETURNING "Existing".*, d.*, "Existing".id AS user_id;
