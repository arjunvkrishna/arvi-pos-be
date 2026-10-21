-- V49: Add parcel_charge column to bills table
ALTER TABLE bills ADD COLUMN IF NOT EXISTS parcel_charge NUMERIC(12, 2) NOT NULL DEFAULT 0.00;
ALTER TABLE "bills" ADD COLUMN IF NOT EXISTS "parcel_charge" NUMERIC(12, 2) NOT NULL DEFAULT 0.00;
