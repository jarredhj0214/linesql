SELECT pg_catalog.make_date(year => e.event_year, month := e.event_month, day => e.event_day) AS event_date
FROM ods.events AS e;
