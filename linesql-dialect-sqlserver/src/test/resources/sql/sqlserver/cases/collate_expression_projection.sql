select
  u.name collate Latin1_General_100_CI_AS as normalized_name
from dbo.users u
where u.name collate Latin1_General_CI_AS = 'Alice';
