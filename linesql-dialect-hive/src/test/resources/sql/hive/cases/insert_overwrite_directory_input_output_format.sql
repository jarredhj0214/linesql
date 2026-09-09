INSERT OVERWRITE DIRECTORY '/tmp/raw_export'
STORED AS INPUTFORMAT 'org.apache.hadoop.mapred.TextInputFormat'
OUTPUTFORMAT 'org.apache.hadoop.hive.ql.io.HiveIgnoreKeyTextOutputFormat'
SELECT r.id, r.payload
FROM ods.raw_events r;
