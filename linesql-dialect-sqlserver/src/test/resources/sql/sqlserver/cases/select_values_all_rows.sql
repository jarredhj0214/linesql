SELECT v.user_id
FROM (VALUES ((SELECT MAX(a.id) FROM dbo.active_users AS a)),
             ((SELECT MAX(b.id) FROM dbo.archived_users AS b))) AS v(user_id);
