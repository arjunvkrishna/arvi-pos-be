-- V8: Set ON DELETE SET NULL for user references across tables so user deactivation/deletion preserves history without FK violation

ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS fk_audit_user;
ALTER TABLE audit_logs ADD CONSTRAINT fk_audit_user FOREIGN KEY (performed_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE attendance DROP CONSTRAINT IF EXISTS fk_att_marked_by;
ALTER TABLE attendance ADD CONSTRAINT fk_att_marked_by FOREIGN KEY (marked_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE cafe_expenses DROP CONSTRAINT IF EXISTS fk_expense_created_by;
ALTER TABLE cafe_expenses ADD CONSTRAINT fk_expense_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE supplier_invoices DROP CONSTRAINT IF EXISTS fk_inv_created_by;
ALTER TABLE supplier_invoices ADD CONSTRAINT fk_inv_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE leave_requests DROP CONSTRAINT IF EXISTS fk_leave_reviewed_by;
ALTER TABLE leave_requests ADD CONSTRAINT fk_leave_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE supplier_payments DROP CONSTRAINT IF EXISTS fk_pay_created_by;
ALTER TABLE supplier_payments ADD CONSTRAINT fk_pay_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE bills DROP CONSTRAINT IF EXISTS fk_bill_created_by;
ALTER TABLE bills ADD CONSTRAINT fk_bill_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE SET NULL;

ALTER TABLE bills DROP CONSTRAINT IF EXISTS fk_bill_printed_by;
ALTER TABLE bills ADD CONSTRAINT fk_bill_printed_by FOREIGN KEY (printed_by) REFERENCES users(id) ON DELETE SET NULL;
