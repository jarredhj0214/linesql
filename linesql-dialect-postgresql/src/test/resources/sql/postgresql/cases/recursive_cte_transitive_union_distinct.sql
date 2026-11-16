WITH RECURSIVE walk(a) AS (
    SELECT n.node_id, n.parent_id AS b, n.root_id AS c FROM org.nodes n
    UNION
    SELECT w.b, w.c, w.a FROM walk w WHERE w.a > 0
)
SELECT a, b, c FROM walk;
