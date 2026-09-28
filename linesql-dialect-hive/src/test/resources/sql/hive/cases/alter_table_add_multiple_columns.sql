alter table mart.users add columns (
  profile struct<email:string,tags:array<string>> comment 'user profile',
  last_seen timestamp
)
