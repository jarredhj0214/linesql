create or replace trigger mart.trg_orders_bi
before insert on mart.orders
for each row
begin
  :new.created_at := sysdate;
end
