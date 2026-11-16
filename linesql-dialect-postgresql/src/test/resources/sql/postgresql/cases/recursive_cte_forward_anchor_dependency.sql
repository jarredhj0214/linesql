WITH RECURSIVE walk(id, root_id) AS (
    SELECT s.id, s.root_id FROM seeds s
    UNION ALL
    SELECT e.child_id, w.id FROM public.edges e JOIN walk w ON e.parent_id = w.id
), seeds AS MATERIALIZED (
    SELECT n.id, n.root_id FROM public.nodes n WHERE n.root_id > 0
)
SELECT id, root_id FROM walk;
