alter function dbo.active_user_count()
returns int
as
begin
    declare @cnt int;
    select @cnt = count(*)
    from ods.users
    where status = 'ACTIVE';
    return @cnt;
end
