create external catalog hive_metastore_catalog
comment "External catalog to Hive"
properties (
  "type" = "hive",
  "hive.metastore.uris" = "thrift://127.0.0.1:9083"
);
