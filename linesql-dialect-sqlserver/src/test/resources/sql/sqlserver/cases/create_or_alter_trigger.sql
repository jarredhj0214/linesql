create or alter trigger dbo.trg_users_ai
on dbo.users
after insert
as
begin
  set nocount on;
end;
