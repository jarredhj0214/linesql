alter table mart.orders set serde 'org.apache.hadoop.hive.serde2.JsonSerDe'
with serdeproperties ('ignore.malformed.json' = 'true')
