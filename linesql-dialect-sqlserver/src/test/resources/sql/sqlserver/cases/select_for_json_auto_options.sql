select u.id, u.name
from dbo.users u
where u.status = 'ACTIVE'
order by u.name
for json auto, include_null_values, without_array_wrapper;
