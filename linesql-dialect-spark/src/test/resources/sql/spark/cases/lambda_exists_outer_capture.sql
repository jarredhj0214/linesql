select exists(items, x -> x.status = status_filter) as has_status
from ods.orders
