create global aggregate function analytics.my_sum_int(int)
returns int
properties (
  "symbol" = "com.example.SumInt",
  "type" = "StarrocksJar",
  "file" = "hdfs://warehouse/udf/sum.jar"
);
