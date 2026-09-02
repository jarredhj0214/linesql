CREATE DATABASE IF NOT EXISTS ods
COMMENT 'ods warehouse'
LOCATION '/user/hive/warehouse/ods'
WITH DBPROPERTIES ('owner' = 'data-platform')
