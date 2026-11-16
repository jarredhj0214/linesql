INSERT INTO mart.reachability (origin, endpoint)
WITH RECURSIVE walk(root_id, current_id) AS (
    VALUES (0, 1)
    UNION ALL
    SELECT w.current_id + 1, e.next_id
    FROM walk w JOIN org.edges e ON w.current_id = e.prior_id
)
SELECT root_id, current_id FROM walk;
