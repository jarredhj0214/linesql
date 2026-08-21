create function analytics.mask_name(varchar)
returns varchar
properties (
  "symbol" = "com.example.MaskName",
  "type" = "StarrocksJar",
  "file" = "hdfs://warehouse/udf/mask.jar"
);
