alter table mart.users add columns (
  city varchar(64) comment 'home city',
  score bigint sum default 0
)
