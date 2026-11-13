SELECT ROW_NUMBER() OVER (PARTITION BY u.group_id ORDER BY u.created_at) AS seq
FROM app.users AS u
UNION ALL
SELECT ROW_NUMBER() OVER (PARTITION BY a.group_id ORDER BY a.created_at) AS admin_seq
FROM app.admins AS a
ORDER BY seq;
