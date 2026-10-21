-- Flyway Migration V10: Alter attendance check_in and check_out to second-level precision
-- to avoid Hibernate 6 negative nanosecond calculation bug in timezones with positive UTC offset during early morning hours.
ALTER TABLE attendance ALTER COLUMN check_in TYPE time(0) without time zone;
ALTER TABLE attendance ALTER COLUMN check_out TYPE time(0) without time zone;
