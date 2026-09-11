select tenant_id, table_id, table_name
from oceanbase.__all_virtual_table
where tenant_id = 1001
  and table_type = 'USER TABLE';
