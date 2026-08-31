create index idx_users_profile
on app.users(profile_id)
engine_attribute = '{"merge_threshold": "40"}'
secondary_engine_attribute = '{"secondary": "on"}';
