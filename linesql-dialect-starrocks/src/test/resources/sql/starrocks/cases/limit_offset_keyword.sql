select id
from ods.users
where status = 'active'
order by updated_at desc
limit 100 offset 20;
