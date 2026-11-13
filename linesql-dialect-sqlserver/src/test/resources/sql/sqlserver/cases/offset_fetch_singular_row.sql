SELECT u.id AS user_id
FROM dbo.users AS u
ORDER BY u.created_at DESC
OFFSET 1 ROW FETCH NEXT 1 ROW ONLY;
