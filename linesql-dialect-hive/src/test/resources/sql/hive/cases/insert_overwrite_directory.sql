INSERT OVERWRITE DIRECTORY '/tmp/user_export'
STORED AS parquet
SELECT u.id AS user_id, u.name
FROM ods.users u
WHERE u.status = 'active';
