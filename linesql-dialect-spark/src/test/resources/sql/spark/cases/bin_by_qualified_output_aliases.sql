SELECT bins.sensor_id, bins.bucket_start, bins.energy
FROM ods.readings BIN BY (
    RANGE reading_start TO reading_end
    BIN WIDTH INTERVAL 15 MINUTES
    DISTRIBUTE UNIFORM (energy)
    BIN_START AS bucket_start
    BIN_DISTRIBUTE_RATIO AS fraction
) AS bins
WHERE bins.fraction > 0.5
