INSERT OVERWRITE DIRECTORY '/tmp/orders_seq'
STORED AS INPUTFORMAT 'org.apache.hadoop.mapred.SequenceFileInputFormat'
OUTPUTFORMAT 'org.apache.hadoop.hive.ql.io.HiveSequenceFileOutputFormat'
SELECT order_id, amount
FROM dwd.orders
