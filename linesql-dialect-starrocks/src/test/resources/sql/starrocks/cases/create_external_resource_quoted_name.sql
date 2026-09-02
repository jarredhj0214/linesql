create external resource "spark0"
properties
(
  "type" = "spark",
  "spark.master" = "yarn",
  "working_dir" = "hdfs://127.0.0.1:10000/tmp/starrocks"
);
