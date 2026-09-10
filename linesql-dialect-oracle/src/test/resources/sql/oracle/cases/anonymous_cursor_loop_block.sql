begin
  for r in (select id from ods.orders) loop
    null;
  end loop;
end
