SELECT u.id, e.pos, e.item
FROM ods.users u
LATERAL VIEW OUTER posexplode(u.items) e AS pos, item
