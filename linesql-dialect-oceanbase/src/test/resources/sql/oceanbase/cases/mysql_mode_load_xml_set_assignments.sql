LOAD XML INFILE '/tmp/events.xml'
REPLACE INTO TABLE ods.events
CHARACTER SET utf8mb4
ROWS IDENTIFIED BY '<event>'
(event_id, @raw_time)
SET event_time = STR_TO_DATE(@raw_time, '%Y-%m-%d %H:%i:%s');
