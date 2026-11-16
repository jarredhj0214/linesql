UPDATE mart.events e
SET payload = pg_catalog.jsonb_set_lax(e.payload, ARRAY['value'], to_jsonb(s.new_value))
FROM staging.event_changes s
WHERE e.id = s.event_id;
