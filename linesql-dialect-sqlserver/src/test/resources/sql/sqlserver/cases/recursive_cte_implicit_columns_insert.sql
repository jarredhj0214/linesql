WITH walk AS (
    SELECT s.source_id AS ancestor, s.current_id AS current_id FROM org.seeds s
    UNION ALL
    SELECT w.current_id, e.child_id
    FROM walk w JOIN org.edges e ON e.parent_id = w.current_id
)
INSERT INTO mart.paths (origin, endpoint)
SELECT ancestor, current_id FROM walk OPTION (MAXRECURSION 6);
