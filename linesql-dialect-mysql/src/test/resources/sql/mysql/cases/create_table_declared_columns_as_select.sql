create table mart.user_snapshot (
  user_id bigint,
  display_name varchar(128)
)
as
select id as source_id, name as source_name
from app.users;
