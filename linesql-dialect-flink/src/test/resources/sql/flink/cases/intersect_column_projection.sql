SELECT id, name FROM ods.users
INTERSECT
SELECT id, name FROM ods.vip_users;
