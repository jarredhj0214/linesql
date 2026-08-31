alter table mart.generated_users add column city string as json_string(json_query(data_json, "$.city")) comment 'generated city'
