create external resource if not exists hive0
properties (
  "type" = "hive",
  "hive.metastore.uris" = "thrift://127.0.0.1:9083"
)
