select transform(items, x -> named_struct('sku', x.product.sku, 'score', x.metrics['score'] + base_score)) as enriched_items
from ods.orders
