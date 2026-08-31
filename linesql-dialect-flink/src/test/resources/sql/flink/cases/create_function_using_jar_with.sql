create function if not exists udf.normalize_phone
as 'com.example.udf.NormalizePhone'
language java
using jar 'file:///opt/flink/usrlib/phone-udf.jar'
with (
  'kind' = 'scalar'
);
