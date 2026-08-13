create materialized view mart.mv_users
as
select id as user_id, name
from ods.users
