CREATE EXTERNAL TABLE ods.shared_events (id BIGINT)
STORED BY 'org.apache.iceberg.mr.hive.HiveIcebergStorageHandler'
WITH SERDEPROPERTIES ('iceberg.catalog' = 'hive_catalog');
