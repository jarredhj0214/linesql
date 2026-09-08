load data concurrent local infile '/data/events.csv'
ignore into table ods.events
fields terminated by ','
lines terminated by '\n'
(event_id, user_id, event_time);
