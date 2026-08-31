select order_id, amount
from iceberg.sales.orders version as of 123456
