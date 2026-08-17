select order_id,
       concat('{', concat_ws(',', collect_set(concat('"', accounting_type, '":', accounting_content))), '}') as accounting_node
from (
  select order_id, accounting_type, accounting_content
  from ods.accounting_flow
  where dt = '#day#'
) a
group by order_id
