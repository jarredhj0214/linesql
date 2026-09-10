create temporary function udf.parse_json
as 'com.example.udf.ParseJson'
language java
using artifact 'file:///opt/flink/artifacts/json-udf.jar';
