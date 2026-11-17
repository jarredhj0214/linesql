USE warehouse;
UPDATE target
SET total = incoming.total
OUTPUT inserted.total AS written_total
FROM dbo.summary AS target
JOIN dbo.delta AS incoming ON target.id = incoming.id
WHERE incoming.active = 1;
