cache lazy table cached_active_users
options ('storageLevel' = 'MEMORY_AND_DISK')
as select id, name from ods.users where status = 'active';
