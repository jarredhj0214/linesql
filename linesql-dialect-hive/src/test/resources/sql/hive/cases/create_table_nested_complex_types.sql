create table ods.complex_events (
  event_id bigint,
  profile struct<
    name:string,
    address:struct<city:string, zip:string>
  >,
  tags array<string>,
  metrics map<string, decimal(18, 2)>,
  choice uniontype<string, bigint>
)
stored as parquet
