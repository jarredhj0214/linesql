create index idx_user_tags
on app.user_events ((cast(payload->'$.tag_ids' as unsigned array)));
