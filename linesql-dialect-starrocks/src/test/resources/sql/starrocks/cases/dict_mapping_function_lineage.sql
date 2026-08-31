select
  order_id,
  dict_mapping('dim.product_dict', product_id, 'product_name', true) as product_name
from dwd.orders
where dt = '2026-08-31';
