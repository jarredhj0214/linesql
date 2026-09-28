create table mart.skewed_events (
  event_type string,
  region string,
  payload string
)
skewed by (event_type, region)
on (('click', 'cn'), ('purchase', 'us'))
stored as directories
stored as orc
