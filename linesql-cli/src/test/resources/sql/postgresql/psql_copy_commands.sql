\copy staging.notes (note) FROM STDIN
SELECT fake FROM discarded.note;
\.
\copy staging.events (id) FROM '/tmp/events.csv' WITH (FORMAT csv)
SELECT id FROM staging.events WHERE id > 0;
