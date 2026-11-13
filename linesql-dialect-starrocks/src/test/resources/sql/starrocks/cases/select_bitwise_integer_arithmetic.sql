SELECT o.id,
       (o.flags & ~o.mask) | o.extra_flags AS flags,
       o.total DIV o.unit_size MOD o.buckets AS bucket
FROM dwd.orders AS o
WHERE (o.flags ^ o.previous_flags) <> 0;
