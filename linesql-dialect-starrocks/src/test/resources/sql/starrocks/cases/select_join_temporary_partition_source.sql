SELECT s.site_id, m.site_name
FROM `mart`.`site_access` TEMPORARY PARTITION (tp1, tp2) AS s
JOIN mart.sites m ON s.site_id = m.id
WHERE s.event_day > '2026-01-01';
