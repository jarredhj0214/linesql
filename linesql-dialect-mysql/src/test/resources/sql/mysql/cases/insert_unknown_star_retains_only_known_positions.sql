INSERT INTO audit.summary(source_id, first_field, second_field, captured_at)
SELECT e.event_id, e.*, e.observed_at FROM ods.events e;
