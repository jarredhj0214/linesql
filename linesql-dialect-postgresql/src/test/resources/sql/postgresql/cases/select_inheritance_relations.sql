SELECT p.id AS parent_id, c.status
FROM ONLY (public.events) AS p
JOIN public.event_details * AS c ON p.id = c.event_id;
