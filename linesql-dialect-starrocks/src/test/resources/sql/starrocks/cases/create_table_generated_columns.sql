create table mart.generated_users (
  id int not null,
  data_array array<int> not null,
  data_json json null,
  avg_score double as array_avg(data_array),
  city string as json_string(json_query(data_json, "$.city"))
)
primary key(id)
distributed by hash(id)
