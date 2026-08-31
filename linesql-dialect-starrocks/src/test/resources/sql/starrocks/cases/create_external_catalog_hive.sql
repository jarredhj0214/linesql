create external catalog if not exists hive_catalog
properties (
  "type" = "hive",
  "hive.metastore.uris" = "thrift://127.0.0.1:9083"
)
