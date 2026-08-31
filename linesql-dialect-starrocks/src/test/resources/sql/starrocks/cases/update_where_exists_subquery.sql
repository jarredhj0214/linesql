update ads.employees e
set salary = salary * 1.05
where exists (
  select 1
  from ods.employee_changes c
  where c.employee_id = e.employee_id
    and c.approved = 1
);
