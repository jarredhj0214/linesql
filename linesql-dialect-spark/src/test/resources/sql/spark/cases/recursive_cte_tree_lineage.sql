WITH RECURSIVE walk(node_id, root_id, depth) MAX RECURSION LEVEL 20 AS (
    SELECT n.id, n.id, 0 FROM org.roots n
    UNION ALL
    SELECT c.child_id, w.root_id, w.depth + 1
    FROM org.edges c JOIN walk w ON c.parent_id = w.node_id
)
SELECT node_id, root_id, depth FROM walk;
