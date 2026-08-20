select
  t1.`department_id`,
  t2.`department_name`
from eps_ods.ods_coa_staff_df t1
left join eps_dim.dim_coa_departments_wide_df t2
  on t1.department_id = t2.department_id
