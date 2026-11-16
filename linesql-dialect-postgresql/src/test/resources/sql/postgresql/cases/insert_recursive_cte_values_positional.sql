WITH RECURSIVE walk(previous_id, current_id) AS (
    VALUES (0, 1)
    UNION ALL
    SELECT w.current_id, e.child_id
    FROM walk w JOIN org.edges e ON w.current_id = e.parent_id
)
INSERT INTO mart.reachability (origin, endpoint) SELECT previous_id, current_id FROM walk;
