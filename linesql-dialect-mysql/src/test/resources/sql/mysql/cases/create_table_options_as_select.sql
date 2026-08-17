create table mart.user_summary_options (
  user_id bigint,
  user_name varchar(128)
)
engine = InnoDB
default charset = utf8mb4
collate = utf8mb4_bin
as
select u.id as user_id, u.name as user_name
from app.users u;
