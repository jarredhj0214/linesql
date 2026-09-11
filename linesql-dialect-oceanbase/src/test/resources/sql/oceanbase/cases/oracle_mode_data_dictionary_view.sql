select owner, table_name, tablespace_name
from sys.dba_tables
where owner = 'APP'
  and temporary = 'N';
