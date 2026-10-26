-- Employees may print the customer bill only when an admin or manager has switched this on for them.
ALTER TABLE users ADD COLUMN IF NOT EXISTS can_print_bill BOOLEAN NOT NULL DEFAULT FALSE;
