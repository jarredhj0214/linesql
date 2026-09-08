select aggregate(items, cast(0 as double), (acc, x) -> acc + x.amount, acc -> acc) as total_amount
from ods.orders
