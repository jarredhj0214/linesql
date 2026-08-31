select
  array_avg(data_array) as newcol1,
  json_string(json_query(data_json, "$.a")) as newcol2
from mart.generated_users;
