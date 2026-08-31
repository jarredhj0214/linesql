alter table mart.users
add column (
  region varchar(64) comment 'user region',
  last_seen_at datetime null
)
