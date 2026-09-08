select zip_with(left_items, right_items, (l, r) -> coalesce(l.score, 0) + coalesce(r.score, 0)) as scores
from ods.order_pairs
