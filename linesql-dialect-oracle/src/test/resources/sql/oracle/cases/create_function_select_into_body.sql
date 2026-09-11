create or replace function mart.latest_order_amount(p_user_id number) return number as
  v_amount number;
begin
  select amount
  into v_amount
  from ods.orders
  where user_id = p_user_id;
  return v_amount;
end;
