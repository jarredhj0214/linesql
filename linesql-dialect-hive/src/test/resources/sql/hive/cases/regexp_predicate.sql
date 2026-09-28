select e.id
from ods.events e
where e.payload regexp '"type"[ ]*:'
