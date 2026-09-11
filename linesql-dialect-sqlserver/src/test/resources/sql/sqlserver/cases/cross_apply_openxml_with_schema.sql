select r.batch_id, x.order_id, x.amount
from dbo.raw_xml_batches r
cross apply openxml(r.doc_handle, '/orders/order', 2)
with (
  order_id bigint '@id',
  amount decimal(10,2) 'amount'
) x
where r.loaded_at >= '2026-09-10';
