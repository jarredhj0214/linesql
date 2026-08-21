create table mart.user_bucket_summary (
  user_id bigint,
  user_name varchar(128)
)
engine = InnoDB
partition by hash (user_id) partitions 16
as
select id as user_id, name as user_name
from app.users
