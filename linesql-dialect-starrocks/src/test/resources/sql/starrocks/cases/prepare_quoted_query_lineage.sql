PREPARE lookup_source FROM 'SELECT s.user_id, s.amount FROM ods.sales s WHERE s.bucket = ? AND s.status = ''ready''';
