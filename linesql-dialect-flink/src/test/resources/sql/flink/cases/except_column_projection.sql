SELECT id, name FROM ods.users
EXCEPT
SELECT id, name FROM ods.blocked_users;
