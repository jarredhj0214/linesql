SELECT bin_start, bin_end, bin_distribute_ratio, energy
FROM ods.readings BIN BY (
    RANGE reading_start TO reading_end
    BIN WIDTH INTERVAL 15 MINUTES
    DISTRIBUTE UNIFORM (energy)
)
