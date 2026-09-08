LOAD XML LOCAL INFILE '/tmp/events.xml'
INTO TABLE ods.events
ROWS IDENTIFIED BY '<event>'
(event_id, user_id, event_time);
