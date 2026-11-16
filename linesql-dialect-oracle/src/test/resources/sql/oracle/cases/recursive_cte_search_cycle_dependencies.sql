WITH walk(node_id, inherited_root) AS (
    SELECT n.id, n.root_id FROM org.roots n
    UNION ALL
    SELECT e.child_id, w.node_id FROM org.edges e JOIN walk w ON e.parent_id = w.node_id
)
SEARCH DEPTH FIRST BY inherited_root SET visit_order
CYCLE inherited_root SET is_loop TO 'Y' DEFAULT 'N'
SELECT node_id, inherited_root, visit_order, is_loop
FROM walk ORDER BY visit_order;
