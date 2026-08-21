create view mart.user_snapshot_v (user_id, display_name) as
select id as source_id, name as source_name
from app.users;
