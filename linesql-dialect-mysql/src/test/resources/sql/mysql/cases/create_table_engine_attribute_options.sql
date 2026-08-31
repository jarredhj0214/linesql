create table mart.archive_orders (
  id bigint primary key,
  payload json
)
engine = InnoDB
checksum = 1
password = 'legacy'
connection = 'mysql://archive'
engine_attribute = '{"compression":"zstd"}'
secondary_engine_attribute = '{"secondary_engine":"rapid"}';
