WITH RECURSIVE written AS (
    INSERT INTO archive.nodes (id) SELECT id FROM src_rows RETURNING id
), src_rows AS (
    SELECT id FROM public.nodes WHERE active
)
SELECT id FROM written;
