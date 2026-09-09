WITH RECURSIVE user_tree(id, parent_id) AS (
  SELECT u.id, u.parent_id
  FROM public.users u
  WHERE u.parent_id IS NULL
  UNION ALL
  SELECT c.id, c.parent_id
  FROM public.users c
  JOIN user_tree p ON c.parent_id = p.id
)
SELECT id, parent_id
FROM user_tree;
