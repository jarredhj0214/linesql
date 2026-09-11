select o.id,
       f.sku,
       f.qty,
       f.ord
from ods.orders o
cross join lateral rows from (
  jsonb_array_elements_text(o.payload -> 'skus'),
  unnest(o.quantities)
) with ordinality as f(sku, qty, ord)
where o.status = 'PAID';
