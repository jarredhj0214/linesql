SELECT id, amount
FROM FROM_CHANGELOG(
  input => TABLE ods.raw_orders,
  op => DESCRIPTOR(op_type),
  op_mapping => MAP['c', 'INSERT', 'd', 'DELETE']
) AS c(id, amount)
