delete low_priority quick ignore from mart.users
where id in (
  select user_id
  from staging.deleted_users
);
