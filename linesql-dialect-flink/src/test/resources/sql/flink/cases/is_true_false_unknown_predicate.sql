select order_id, user_id
from dwd.orders
where is_valid is true
  and deleted is not false
  and risk_flag is unknown;
