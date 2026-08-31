select u.user_id
from ods.users u
join native_query(
  "catalog" = "jdbc0",
  "db" = "app",
  "sql" = "select user_id from orders"
) q
on u.user_id = q.user_id
