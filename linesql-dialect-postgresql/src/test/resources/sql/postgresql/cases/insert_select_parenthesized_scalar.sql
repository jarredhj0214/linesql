INSERT INTO mart.account_rollup (account_id, latest_payment)
(SELECT a.id,
       (SELECT max(p.amount) FROM sales.payments AS p)
 FROM ods.accounts AS a);
