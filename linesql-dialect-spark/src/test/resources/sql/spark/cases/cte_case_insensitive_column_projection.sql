WITH u AS (
  SELECT id AS User_ID
  FROM ods.users
)
SELECT user_id
FROM u
