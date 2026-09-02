create external catalog if not exists iceberg_catalog
properties
(
  "type" = "iceberg",
  "iceberg.catalog.type" = "hive",
  "iceberg.catalog.hive.metastore.uris" = "thrift://127.0.0.1:9083"
);
