ALTER TABLE mart.events SET PARTITION SPEC (
    YEAR(event_time), MONTH(event_time), HOUR(event_time), TRUNCATE(4, region)
);
