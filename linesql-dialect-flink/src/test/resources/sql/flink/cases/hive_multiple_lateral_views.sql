SELECT lv1.myc1, lv2.myc2
FROM dwd.arrays t
LATERAL VIEW explode(t.c1) lv1 AS myc1
LATERAL VIEW explode(lv1.myc1) lv2 AS myc2
