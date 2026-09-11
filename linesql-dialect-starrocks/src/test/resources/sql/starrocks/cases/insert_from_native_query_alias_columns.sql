insert into ods.remote_users (user_id, user_name)
select q.user_id, q.user_name
from native_query(
  "catalog" = "jdbc0",
  "query" = "select id, name, status from crm.users"
) as q(user_id, user_name, status)
where q.status = 'ACTIVE'
order by q.user_id;
