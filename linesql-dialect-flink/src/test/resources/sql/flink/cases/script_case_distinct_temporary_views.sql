CREATE TEMPORARY VIEW lake.ops.Rates AS
SELECT amount FROM ods.LiveRates;
CREATE TEMPORARY VIEW lake.ops.rates AS
SELECT amount FROM ods.ArchivedRates;
SELECT lake.ops.Rates.amount AS current_amount, lake.ops.rates.amount AS historical_amount
FROM lake.ops.Rates JOIN lake.ops.rates
ON lake.ops.Rates.amount = lake.ops.rates.amount;
DROP TEMPORARY VIEW lake.ops.Rates;
SELECT amount FROM lake.ops.rates;
