WITH RECURSIVE walk(node_id, inherited_root) AS (
    SELECT n.id, n.root_id FROM org.roots n
    UNION ALL
    SELECT e.child_id, w.node_id FROM org.edges e JOIN walk w ON e.parent_id = w.node_id
)
SEARCH BREADTH FIRST BY inherited_root SET breadth_position
CYCLE inherited_root SET seen USING path
SELECT node_id, inherited_root, breadth_position, seen, path FROM walk ORDER BY breadth_position;
