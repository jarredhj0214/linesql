select *
from native_query(
  "catalog" = "jdbc0",
  "db" = "app",
  "sql" = "select user_id, amount from orders where status = 'PAID'"
) q
