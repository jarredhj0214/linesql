select dt, count(*) as request_cnt
from ods.object_access_log
where file_key = "\"bucket/path/video_2026-08-11.mp4\""
  and method = 'GetObject'
group by dt
order by dt asc
