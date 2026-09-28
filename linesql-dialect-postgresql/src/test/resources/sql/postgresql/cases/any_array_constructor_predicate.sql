select j.id
from mart.jobs j
where j.status = any(array['READY', 'RUNNING'])
