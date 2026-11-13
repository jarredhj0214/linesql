MERGE dbo.jobs AS j
USING (VALUES (1, 'ready'), (2, 'queued')) AS d(id, state)
ON j.id = d.id
WHEN MATCHED THEN UPDATE SET state = d.state
WHEN NOT MATCHED THEN INSERT (id, state) VALUES (d.id, d.state)
OUTPUT deleted.state AS previous_state;
