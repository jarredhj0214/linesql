select transform(items, x -> x.amount + tax_rate) as adjusted_amounts
from ods.orders
