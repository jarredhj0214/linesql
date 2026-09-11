CREATE TEMPORARY TABLE tmp.active_users
WITH (
    'connector' = 'filesystem',
    'path' = '/tmp/active_users',
    'format' = 'json'
) AS
SELECT user_id AS id, user_name
FROM ods.users
WHERE status = 'ACTIVE';
