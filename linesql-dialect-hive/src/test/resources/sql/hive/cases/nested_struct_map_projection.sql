select u.profile.address.city as city,
       u.profile.metrics['score'] as score
from ods.users u
where u.profile.flags['active'] = true;
