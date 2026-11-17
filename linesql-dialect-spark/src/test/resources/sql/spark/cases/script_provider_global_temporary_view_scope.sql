USE analytics;
CREATE GLOBAL TEMPORARY VIEW incoming USING csv OPTIONS (path '/data/events');
SELECT id FROM global_temp.incoming WHERE state = 'ready';
