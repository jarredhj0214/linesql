create or alter function dbo.latest_order_amount(@user_id bigint)
returns decimal(18,2)
as
begin
    declare @amount decimal(18,2);
    select @amount = amount
    from ods.orders
    where user_id = @user_id;
    return @amount;
end
