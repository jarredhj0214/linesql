USE analytics;
CREATE GLOBAL TEMP VIEW events_view AS SELECT id FROM raw.old_events;
ALTER VIEW global_temp.events_view AS SELECT id FROM raw.new_events WHERE active;
SELECT id AS updated_id FROM global_temp.events_view;
ALTER VIEW global_temp.events_view RENAME TO events_latest;
SELECT id AS renamed_id FROM global_temp.events_latest;
