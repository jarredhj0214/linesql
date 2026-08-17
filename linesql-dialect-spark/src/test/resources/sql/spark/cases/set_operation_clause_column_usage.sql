select id
from ods.users
where dt = '${yyyy-MM-dd}'
union all
select id
from dwd.users
where ds = '${yyyy-MM-dd}'
