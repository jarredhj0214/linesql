select user_id,
       row_number() over (partition by user_id order by created_at desc) as rn,
       sum(amount) over (partition by user_id) as user_amount
from ods.orders
