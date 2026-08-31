select order_id, amount
from iceberg.sales.orders timestamp as of '2026-08-28 10:00:00'
