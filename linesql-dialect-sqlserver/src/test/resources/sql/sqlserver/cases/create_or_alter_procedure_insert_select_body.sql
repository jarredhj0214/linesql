create or alter procedure dbo.refresh_user_summary
as
begin
    insert into mart.user_summary(user_id, user_name)
    select id, name
    from ods.users
    where status = 'ACTIVE';
end
