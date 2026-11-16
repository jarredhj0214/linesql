WITH RECURSIVE first_rows AS (
    SELECT q.id FROM (
        WITH later_rows AS (SELECT id FROM later_rows)
        SELECT id FROM later_rows
    ) q
), later_rows AS (
    SELECT id FROM public.nodes
)
SELECT id FROM first_rows;
