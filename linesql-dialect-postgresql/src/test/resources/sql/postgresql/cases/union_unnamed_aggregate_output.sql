(select max(a.amount), a.region from ods.orders a group by a.region)
union all
select max(b.amount), b.region from ods.archive b group by b.region
order by max;
