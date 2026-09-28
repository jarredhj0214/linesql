select e.created_at at time zone e.timezone_name as local_created_at
from mart.events e
