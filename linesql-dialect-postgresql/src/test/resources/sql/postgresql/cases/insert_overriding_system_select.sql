INSERT INTO mart.users (id, name)
OVERRIDING SYSTEM VALUE
SELECT s.id, s.name
FROM staging.users_delta s;
