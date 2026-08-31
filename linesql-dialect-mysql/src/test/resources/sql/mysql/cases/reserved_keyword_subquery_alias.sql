select
  delete.open_id,
  delete.delete_time,
  info.*
from (
  select open_id, device_id, delete_time
  from idaas_dw.dwd_ueba_delete_device_day
  where dt = '${yyyy-MM-dd}'
) delete
left join (
  select device_id, device.app_id as client_id
  from idaas_ods.idaas_ods_t_device_di device
) info
  on delete.device_id = info.device_id
