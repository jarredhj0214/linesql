load xml infile '/tmp/events.xml'
replace into table ods.events
character set utf8mb4
rows identified by '<event>'
(event_id, @raw_time)
set event_time = str_to_date(@raw_time, '%Y-%m-%d %H:%i:%s')
