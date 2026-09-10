create table mart.users_identity (
  id bigint generated always as identity primary key,
  name text not null
)
