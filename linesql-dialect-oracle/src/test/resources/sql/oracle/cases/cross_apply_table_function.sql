select u.id, f.value
from ods.users u
cross apply table(app.split_tags(u.tags)) f
where u.active = 1;
