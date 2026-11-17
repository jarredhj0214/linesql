INSERT INTO mart.selected (event_id, event_kind)
WITH staged AS (SELECT id, kind FROM ods.events)
TABLE staged;
