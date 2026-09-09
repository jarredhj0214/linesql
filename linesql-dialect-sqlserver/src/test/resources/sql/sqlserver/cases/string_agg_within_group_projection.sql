select
  o.customer_id,
  string_agg(o.product_name, ',') within group (order by o.created_at desc) as product_names
from sales.orders o
group by o.customer_id;
