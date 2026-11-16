INSERT INTO paths (node_id, root_node)
WITH RECURSIVE walk(id, root_id, lvl) AS (
    SELECT n.id, n.root_id, 0 FROM seed_nodes n
    UNION ALL
    SELECT e.child_id, w.id, w.lvl + 1
    FROM graph.edges e JOIN walk w ON e.parent_id = w.id
    WHERE w.lvl < 3
)
SELECT id, root_id FROM walk;
