DECLARE @events AS TABLE (id bigint NOT NULL);
INSERT INTO @events(id) SELECT id FROM ods.events;
SELECT id FROM @events;
