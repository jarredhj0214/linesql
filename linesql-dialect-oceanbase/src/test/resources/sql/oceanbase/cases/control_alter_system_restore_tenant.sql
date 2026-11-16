alter system restore tenant_restore
from 'file:///ob_backup/data,file:///ob_backup/archive'
until time = '2026-09-10 12:00:00'
with 'pool_list=pool_restore&locality=F@zone1';
