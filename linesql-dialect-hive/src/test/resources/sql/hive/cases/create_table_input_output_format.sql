create table mart.sequence_events (
  user_id string,
  payload string
)
stored as inputformat 'org.apache.hadoop.mapred.SequenceFileInputFormat'
outputformat 'org.apache.hadoop.hive.ql.io.HiveSequenceFileOutputFormat'
