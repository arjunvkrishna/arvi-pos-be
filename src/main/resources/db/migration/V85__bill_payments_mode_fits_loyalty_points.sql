-- A split payment can include LOYALTY_POINTS (14 characters), which did not fit the VARCHAR(10) mode column,
-- so paying part of a bill with points failed with a data conflict.
ALTER TABLE bill_payments ALTER COLUMN mode TYPE VARCHAR(20);
