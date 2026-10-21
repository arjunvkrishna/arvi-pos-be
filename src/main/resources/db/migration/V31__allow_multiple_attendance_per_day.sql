-- Allow multiple check-in/check-out entries per employee per day
ALTER TABLE attendance DROP CONSTRAINT IF EXISTS uk_att_emp_date;
CREATE INDEX IF NOT EXISTS idx_att_emp_date ON attendance(employee_id, date);
