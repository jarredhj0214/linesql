WITH RECURSIVE user_tree(id, parent_id) AS (
    SELECT u.id, u.parent_id FROM public.users AS u WHERE u.parent_id IS NULL
    UNION ALL
    SELECT c.id, c.parent_id FROM public.users AS c JOIN user_tree AS p ON c.parent_id = p.id
)
SEARCH DEPTH FIRST BY id SET visit_order
CYCLE id SET is_cycle USING cycle_path
SELECT id, visit_order, is_cycle, cycle_path FROM user_tree
ORDER BY visit_order;
