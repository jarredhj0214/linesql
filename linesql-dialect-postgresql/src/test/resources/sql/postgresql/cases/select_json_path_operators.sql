SELECT e.payload #- e.remove_path AS cleaned, e.payload #>> e.read_path AS extracted
FROM ods.events AS e
WHERE e.payload @? e.search_path;
