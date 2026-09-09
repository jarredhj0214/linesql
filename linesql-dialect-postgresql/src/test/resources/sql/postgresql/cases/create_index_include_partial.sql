create index concurrently if not exists idx_users_active_name
on mart.users using btree (lower(name), created_at desc)
include (email, last_login_at)
where status = 'ACTIVE';
