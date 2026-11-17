CREATE CATALOG `catalog.prod` WITH ('type' = 'generic_in_memory', 'default-database' = 'daily.events');
USE CATALOG `catalog.prod`;
SELECT event_id FROM orders;
