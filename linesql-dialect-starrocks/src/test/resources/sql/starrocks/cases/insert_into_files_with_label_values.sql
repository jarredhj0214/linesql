insert into files(
  "path" = "s3://bucket/export/user_events/",
  "format" = "parquet"
)
with label export_user_events
values (1, 'u001', '2026-08-31 10:00:00')
