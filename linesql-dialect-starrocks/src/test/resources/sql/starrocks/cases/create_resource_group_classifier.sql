create resource group rg_etl
to
  (user = 'etl_user', query_type in ('insert'), source_ip = '10.0.0.0/24'),
  (db = 'mart')
with (
  'cpu_weight' = '10',
  'mem_limit' = '20%',
  'big_query_scan_rows_limit' = '100000'
);
