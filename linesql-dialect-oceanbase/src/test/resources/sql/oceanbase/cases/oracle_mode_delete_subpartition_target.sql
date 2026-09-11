delete from mart.user_summary subpartition (sp20260910) t
where t.status = 'EXPIRED';
