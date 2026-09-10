select id, status
from mart.jobs
where status = 'READY'
order by id
limit 10
for update of mart.jobs skip locked;
