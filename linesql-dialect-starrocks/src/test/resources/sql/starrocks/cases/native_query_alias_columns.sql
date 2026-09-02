select q.user_id, q.amount
from native_query(
  "catalog" = "jdbc0",
  "db" = "app",
  "sql" = "select user_id, amount from orders where status = 'PAID'"
) as q(user_id, amount);
