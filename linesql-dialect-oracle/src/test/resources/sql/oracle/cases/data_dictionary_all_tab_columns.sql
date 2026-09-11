select owner, table_name, column_name, data_type
from sys.all_tab_columns
where owner = 'APP'
  and nullable = 'N';
