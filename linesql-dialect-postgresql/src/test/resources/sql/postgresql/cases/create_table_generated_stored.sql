create table mart.user_names (
  first_name text,
  last_name text,
  full_name text generated always as (first_name || ' ' || last_name) stored
)
