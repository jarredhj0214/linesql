create table mart.user_profiles (
  user_id bigint,
  tags array<varchar(20)> default ['vip', 'active'],
  attrs map<varchar(20), int> default map{'age': 25, 'score': 100},
  profile struct<name varchar(20), age int> default row('John', 30)
)
duplicate key(user_id)
distributed by hash(user_id)
