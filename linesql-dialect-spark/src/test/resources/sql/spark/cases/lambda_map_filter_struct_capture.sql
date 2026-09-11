select map_filter(attributes, (k, v) -> v.score > min_score) as valid_attributes
from ods.orders
