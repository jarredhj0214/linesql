COPY mart.events (id, status)
FROM STDIN WITH (FORMAT csv, HEADER MATCH)
WHERE status <> 'deleted';
