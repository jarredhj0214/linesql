begin try
  begin transaction load_orders
  exec dbo.load_orders
  commit transaction load_orders
end try
begin catch
  rollback transaction load_orders
  throw
end catch
