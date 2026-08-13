create flow mart.user_flow as
insert into mart.users
select id from ods.users
