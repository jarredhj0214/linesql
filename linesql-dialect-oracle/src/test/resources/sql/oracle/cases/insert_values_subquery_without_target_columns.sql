insert into mart.rollup values ((select max(s.amount) from ods.sales s));
