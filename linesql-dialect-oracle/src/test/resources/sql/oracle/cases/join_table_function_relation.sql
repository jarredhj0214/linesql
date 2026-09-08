select u.id, f.value
from ods.users u
cross join table(app.split_tags(u.tags)) f
where u.active = 1;
