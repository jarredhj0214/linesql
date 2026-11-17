CREATE VIEW mart.events_view AS SELECT id FROM ods.old_events;
ALTER VIEW mart.events_view AS SELECT id FROM ods.current_events WHERE active;
SELECT id AS current_id FROM mart.events_view;
ALTER VIEW mart.events_view RENAME TO events_latest;
SELECT id AS latest_id FROM mart.events_latest;
DROP VIEW mart.events_latest;
