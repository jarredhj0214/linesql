select
  user_id,
  sum(amount) filter (where pay_status = 'SUCCESS') as paid_amount,
  count(*) filter (where refund_status is not null) as refund_count
from dwd.orders
group by user_id
