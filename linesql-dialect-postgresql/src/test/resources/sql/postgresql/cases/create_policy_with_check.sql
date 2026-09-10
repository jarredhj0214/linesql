create policy active_user_insert on mart.users
for insert
to app_writer
with check (tenant_id is not null and status <> 'DELETED');
