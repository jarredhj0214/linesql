create policy active_user_select on mart.users
as permissive
for select
to app_reader
using (tenant_id = current_setting('app.tenant_id')::int and deleted_at is null);
