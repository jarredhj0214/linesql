PREPARE write_snapshot FROM 'INSERT INTO mart.snapshot (id, amount) SELECT o.id, o.amount + ? FROM ods.orders o WHERE o.status = ''paid'' AND o.id = ?';
