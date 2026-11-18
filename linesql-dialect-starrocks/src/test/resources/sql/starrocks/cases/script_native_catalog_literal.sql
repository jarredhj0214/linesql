USE 'CATALOG lake';
USE ops;
SELECT id AS event_id FROM events;
SET CATALOG 'warehouse';
USE stage;
SELECT id AS order_id FROM orders;
