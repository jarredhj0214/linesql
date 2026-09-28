select e.display_name collate "C" as binary_name
from mart.events e
order by e.display_name collate "C"
