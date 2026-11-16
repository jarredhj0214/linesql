WITH RECURSIVE walk(node_id, inherited_root, lvl) AS (
    SELECT n.id, n.root_id, 0 FROM org.roots n
    UNION ALL
    SELECT e.child_id, w.node_id, w.lvl + 1
    FROM org.edges e JOIN walk w ON e.parent_id = w.node_id
    WHERE w.lvl < 3
)
SELECT node_id, inherited_root, lvl FROM walk;
