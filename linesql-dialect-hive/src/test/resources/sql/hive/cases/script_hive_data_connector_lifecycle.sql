CREATE CONNECTOR IF NOT EXISTS reports_jdbc
TYPE 'mysql' URL 'jdbc:mysql://example.invalid:3306/reports' COMMENT 'sample reports database'
WITH DCPROPERTIES ('hive.sql.database' = 'reports');
ALTER CONNECTOR reports_jdbc SET URL 'jdbc:mysql://replica.example.invalid:3306/reports';
ALTER CONNECTOR reports_jdbc SET OWNER ROLE analysts;
ALTER CONNECTOR reports_jdbc SET DCPROPERTIES ('hive.sql.database' = 'reports');
SHOW CONNECTORS;
DESCRIBE CONNECTOR EXTENDED reports_jdbc;
DROP CONNECTOR IF EXISTS reports_jdbc;
