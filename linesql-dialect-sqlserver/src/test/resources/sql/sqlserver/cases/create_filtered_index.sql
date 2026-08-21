create unique nonclustered index IX_users_active_name
on dbo.users (name asc, created_at desc)
include (email)
where status = 'ACTIVE';
