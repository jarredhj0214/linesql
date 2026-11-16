WITH walk(a, b, lvl) AS (
    SELECT n.first_id, n.second_id, 0 FROM org.roots n
    UNION ALL
    SELECT w.b, e.next_id, w.lvl + 1
    FROM walk w JOIN org.primary_edges e ON w.a = e.prior_id
    WHERE w.lvl < 2
    UNION ALL
    SELECT f.next_id, w.a, w.lvl + 1
    FROM walk w JOIN org.secondary_edges f ON w.b = f.prior_id
    WHERE w.lvl < 2
)
SELECT a, b FROM walk ORDER BY a OPTION (MAXRECURSION 8);
