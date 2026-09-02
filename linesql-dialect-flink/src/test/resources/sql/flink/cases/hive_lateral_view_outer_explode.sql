SELECT o.user_id, tag_lv.tag
FROM dwd.user_events o
LATERAL VIEW OUTER explode(o.tags) tag_lv AS tag
WHERE o.event_type = 'click'
