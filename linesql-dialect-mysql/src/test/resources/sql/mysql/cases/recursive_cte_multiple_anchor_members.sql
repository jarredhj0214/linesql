WITH RECURSIVE walk(a,b,lvl) AS (
    SELECT primary_id, sibling_id, 0 FROM org.primary_seeds
    UNION ALL
    SELECT secondary_id, neighbor_id, 0 FROM org.secondary_seeds
    UNION ALL
    SELECT w.b, w.a, w.lvl + 1 FROM walk w WHERE w.lvl < 2
)
SELECT a, b FROM walk;
