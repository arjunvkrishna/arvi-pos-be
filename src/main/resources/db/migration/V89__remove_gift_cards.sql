-- Gift cards are gone: the cafe takes cash, UPI, card and loyalty points only.
-- A bill, a split part or a refund that still says GIFT_CARD would no longer load, so it is read as cash.
-- (No gift card was ever issued on the staging or production databases; the rows this touches are test data.)
UPDATE bills SET payment_mode = 'CASH' WHERE payment_mode = 'GIFT_CARD';
UPDATE bill_payments SET mode = 'CASH' WHERE mode = 'GIFT_CARD';
UPDATE bill_refunds SET payment_mode = 'CASH' WHERE payment_mode = 'GIFT_CARD';

DROP TABLE IF EXISTS gift_card_transactions;
DROP TABLE IF EXISTS gift_cards;
ALTER TABLE cashier_shifts DROP COLUMN IF EXISTS gift_card_cash_sales;
