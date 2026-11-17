SELECT "a.b".c.id AS left_id, a."b.c".id AS right_id
FROM "a.b".c JOIN a."b.c" ON "a.b".c.id = a."b.c".id;
