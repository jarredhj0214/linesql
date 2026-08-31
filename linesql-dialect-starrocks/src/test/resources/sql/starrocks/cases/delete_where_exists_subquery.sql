delete from ads.employees e
where exists (
  select 1
  from ods.employee_changes c
  where c.employee_id = e.employee_id
    and c.approved = 0
);
