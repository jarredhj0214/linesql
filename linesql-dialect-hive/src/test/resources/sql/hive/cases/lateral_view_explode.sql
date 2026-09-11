SELECT u.id, item
FROM ods.users u
LATERAL VIEW explode(u.items) e AS item
WHERE item IS NOT NULL
