USE analytics;
CREATE TEMP VIEW events_view AS SELECT id FROM raw.old_events;
ALTER VIEW events_view AS SELECT id FROM raw.new_events WHERE active;
SELECT id AS updated_id FROM events_view;
ALTER VIEW events_view RENAME TO events_latest;
SELECT id AS renamed_id FROM events_latest;
