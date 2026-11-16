WITH RECURSIVE walk(a,b,c,lvl) AS (
    SELECT n.id, n.owner_id, n.root_id, 0 FROM org.nodes n
    UNION ALL
    SELECT w.b, w.c, w.a, w.lvl + 1 FROM walk w WHERE w.lvl < 2 AND w.a > 0
    UNION ALL
    SELECT w.a, w.b, w.c, w.lvl + 1 FROM walk w WHERE w.lvl < 2
)
SELECT a, b, c FROM walk;
