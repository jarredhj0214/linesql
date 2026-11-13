SELECT CAST(e.observed_at AS TIMESTAMP(3) WITH TIME ZONE) AS observed_at,
       e.ratio::DOUBLE PRECISION AS ratio,
       CAST(e.label AS CHARACTER VARYING(40)) AS label
FROM ods.events AS e;
