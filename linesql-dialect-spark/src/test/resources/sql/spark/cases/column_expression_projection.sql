select
  lower(name) as name_lower,
  price * quantity as amount,
  1 as flag
from ods.orders
