-- ============================================================
-- V88: "can cancel order" staff permission; the unused "can bill / expenses / reports" toggles go away
--
-- Until now anyone who could take orders could also cancel and void them. Cancelling is its own permission now. People who
-- could cancel before keep that ability (so nothing changes on the day this is deployed); a manager can switch it off
-- per person. New staff start without it. Owners and managers always have it. Paid bills still need the refund workflow.
--
-- can_bill, can_manage_expenses and can_view_reports were never checked by any endpoint and are no longer mapped. The
-- columns stay (NOT NULL with defaults) so an older app version still runs against this schema.
-- ============================================================
ALTER TABLE users ADD COLUMN IF NOT EXISTS can_cancel_order BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET can_cancel_order = TRUE WHERE can_take_orders = TRUE;
