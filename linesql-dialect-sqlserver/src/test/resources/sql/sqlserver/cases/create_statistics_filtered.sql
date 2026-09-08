create statistics stat_users_name
on dbo.users (name)
where status = 'ACTIVE';
