USE analytics;
CREATE TEMPORARY VIEW incoming (id INT, state STRING) USING json OPTIONS (path '/data/events');
SELECT id AS stage_id FROM incoming WHERE state = 'ready';
DROP VIEW incoming;
SELECT id AS stored_id FROM incoming;
