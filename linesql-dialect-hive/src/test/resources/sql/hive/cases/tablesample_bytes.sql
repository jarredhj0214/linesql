select e.id
from ods.events tablesample(100M) e
