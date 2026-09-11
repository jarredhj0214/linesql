select table_schema, table_name, table_rows
from information_schema.tables
where table_schema = 'app'
  and table_type = 'BASE TABLE';
