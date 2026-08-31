select
  user_id,
  count(*) over (
    partition by user_id
    order by created_at
    range interval 1 day preceding
  ) as daily_order_count
from app.orders;
