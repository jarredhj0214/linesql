WITH walk(a, b, c, lvl) AS (
    SELECT n.id, n.parent_id, n.root_id, 0 FROM org.nodes n
    UNION ALL
    SELECT r.b, r.c, r.a, r.lvl + 1 FROM walk r WHERE r.a > 0 AND r.lvl < 3
)
SELECT a, b, c FROM walk;
