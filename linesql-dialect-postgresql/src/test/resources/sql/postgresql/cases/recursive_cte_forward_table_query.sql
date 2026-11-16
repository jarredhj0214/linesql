WITH RECURSIVE first_rows AS (TABLE later_rows),
later_rows AS (SELECT n.id, n.amount FROM public.nodes n WHERE n.active)
SELECT id, amount FROM first_rows;
