SELECT a.id AS id, merged.id AS alias_id, id AS result_id
FROM public.events a RIGHT JOIN public.legacy_events b USING (id) AS merged
WHERE id > 0
ORDER BY id;
