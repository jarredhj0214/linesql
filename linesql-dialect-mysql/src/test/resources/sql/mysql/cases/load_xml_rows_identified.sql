load xml local infile '/tmp/events.xml'
into table ods.events
rows identified by '<event>'
(event_id, user_id, event_time)
