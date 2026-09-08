create table mart.archive_users (
  id bigint primary key,
  payload text
)
engine=InnoDB
row_format=compressed
key_block_size=8
compression='zlib'
tablespace ts_archive
autoextend_size=4194304;
