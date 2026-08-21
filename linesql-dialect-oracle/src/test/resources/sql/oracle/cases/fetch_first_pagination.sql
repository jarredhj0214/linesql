select id, name
from mart.users
where status = 'ACTIVE'
order by created_at desc
offset 10 rows fetch next 20 rows only;
