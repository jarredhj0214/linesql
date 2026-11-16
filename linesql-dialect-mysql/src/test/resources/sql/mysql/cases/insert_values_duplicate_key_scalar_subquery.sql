INSERT INTO mart.events (id, score) VALUES (42, 0)
ON DUPLICATE KEY UPDATE
    score = (SELECT MAX(b.score) FROM ods.bonuses AS b WHERE b.event_id = events.id),
    touches = touches + 1;
