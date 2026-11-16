SET statement_timeout = 0;
COPY "staging"."notes" ("note") FROM STDIN;
SELECT id FROM ghost.tables;
DELIMITER $$
/* this is data, with a stray ' quote
GO
\.
COPY staging.empty (id) FROM STDIN;
\.
SELECT id FROM staging.events WHERE active;
