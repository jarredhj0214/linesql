CREATE UNLOGGED TABLE IF NOT EXISTS mart.orders (
  id bigint,
  user_id bigint,
  created_at timestamp
)
PARTITION BY RANGE (created_at);
