SELECT 1;
SET 'execution.runtime-mode' = 'streaming';
INSERT INTO mart.events (id)
SELECT id FROM ods.events
ON CONFLICT DO NOTHING;
