create unique index concurrently if not exists idx_users_active_name
on mart.users using btree (name asc, created_at desc)
where status = 'ACTIVE';
