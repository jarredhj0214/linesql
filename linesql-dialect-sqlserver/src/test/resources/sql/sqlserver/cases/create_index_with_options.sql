create nonclustered index IX_users_status
on dbo.users(status)
with (online = on, data_compression = page);
