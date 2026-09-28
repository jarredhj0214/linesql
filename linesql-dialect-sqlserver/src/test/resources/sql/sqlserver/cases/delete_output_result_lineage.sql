delete from dbo.users
output deleted.id as deleted_id,
       deleted.display_name as deleted_name
where status = 'INACTIVE';
