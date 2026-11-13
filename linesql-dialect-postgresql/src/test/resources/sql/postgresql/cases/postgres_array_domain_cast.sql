SELECT e.tags::app.tag_code[] AS codes,
       CAST(e.metrics AS numeric(10, 2)[][]) AS matrix
FROM ods.events AS e;
