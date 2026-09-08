create external table ext.order_payloads (
  id bigint,
  payload varchar(4000)
)
with (
  location = '/orders/',
  data_source = ext_storage,
  file_format = csv_format
);
