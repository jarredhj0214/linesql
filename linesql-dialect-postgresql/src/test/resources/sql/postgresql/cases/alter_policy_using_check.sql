alter policy active_user_select on mart.users
to app_role
using (tenant_id = current_setting('app.tenant_id')::int and deleted_at is null)
with check (status <> 'DELETED');
