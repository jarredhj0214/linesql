create materialized view mart.mv_active_users
build immediate
refresh fast on demand
as
select id, name
from ods.users
where status = 'ACTIVE';
