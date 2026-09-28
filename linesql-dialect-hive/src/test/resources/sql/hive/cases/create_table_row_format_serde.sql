create external table mart.raw_events (
  user_id string,
  payload string
)
row format serde 'org.apache.hadoop.hive.serde2.lazy.LazySimpleSerDe'
with serdeproperties ('field.delim'='\t')
stored as textfile
location 'hdfs:///warehouse/mart/raw_events'
