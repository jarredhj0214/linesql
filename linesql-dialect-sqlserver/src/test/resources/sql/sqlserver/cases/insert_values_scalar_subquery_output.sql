INSERT INTO dbo.user_flags (user_id, flag)
OUTPUT inserted.user_id AS inserted_user
VALUES ((SELECT MAX(a.id) FROM dbo.active_users AS a), 'active'),
       ((SELECT MAX(b.id) FROM dbo.archived_users AS b), DEFAULT);
