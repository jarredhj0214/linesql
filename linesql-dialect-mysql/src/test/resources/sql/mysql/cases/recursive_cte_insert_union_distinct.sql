INSERT INTO mart.snapshots (root_id, parent_id)
WITH RECURSIVE walk (a, b) AS (
    SELECT n.root_id, n.parent_id FROM org.nodes n
    UNION DISTINCT
    SELECT r.b, r.a FROM walk r WHERE r.a > 0
)
SELECT a, b FROM walk;
