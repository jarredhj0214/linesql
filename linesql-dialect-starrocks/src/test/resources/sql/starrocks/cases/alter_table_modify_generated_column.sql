alter table mart.generated_users modify column city string as json_string(json_query(data_json, "$.home.city"))
