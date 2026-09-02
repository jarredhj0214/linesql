create table function analytics.split_words(varchar)
returns varchar
properties (
  "symbol" = "com.example.SplitWords",
  "type" = "StarrocksJar",
  "file" = "hdfs://warehouse/udf/split.jar"
);
