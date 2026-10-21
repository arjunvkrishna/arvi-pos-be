package com.neram.pos.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Defensive schema ensurer to guarantee all critical printer_settings and bills
 * columns exist on application boot even if Flyway migrations were previously marked
 * or baselined in legacy/cloned cloud environments.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class DatabaseSchemaEnsurer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running application runner database schema verification on boot...");
        ensureSchema();
    }

    public synchronized void ensureSchema() {
        log.info("Ensuring database schema integrity for printer settings and KOT columns...");

        // Ensure printer_settings table exists
        executeSqlSafe("CREATE TABLE IF NOT EXISTS printer_settings (" +
                "id BIGINT PRIMARY KEY, " +
                "host VARCHAR(100) NOT NULL DEFAULT '192.168.1.47', " +
                "port INT NOT NULL DEFAULT 9100, " +
                "enabled BOOLEAN NOT NULL DEFAULT TRUE, " +
                "auto_cut BOOLEAN NOT NULL DEFAULT TRUE, " +
                "paper_width_mm INT NOT NULL DEFAULT 80, " +
                "header_title VARCHAR(100) NOT NULL DEFAULT 'NERAM', " +
                "subheader VARCHAR(150) NOT NULL DEFAULT 'Where time tastes better', " +
                "footer_message VARCHAR(150) NOT NULL DEFAULT 'THANK YOU! VISIT AGAIN', " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW())");

        // Unquoted columns for printer_settings
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_host VARCHAR(100) NOT NULL DEFAULT '192.168.1.37'");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_port INT NOT NULL DEFAULT 9100");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS bill_enabled BOOLEAN NOT NULL DEFAULT TRUE");

        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_host VARCHAR(100) NOT NULL DEFAULT '192.168.1.47'");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_port INT NOT NULL DEFAULT 9100");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS kot_enabled BOOLEAN NOT NULL DEFAULT TRUE");

        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_host VARCHAR(100) NOT NULL DEFAULT '192.168.1.42'");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_port INT NOT NULL DEFAULT 9100");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_enabled BOOLEAN NOT NULL DEFAULT TRUE");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_header_title VARCHAR(150) NOT NULL DEFAULT '*** JUICE ORDER ***'");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_subheader VARCHAR(500) NOT NULL DEFAULT 'NERAM - JUICE BAR'");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_footer_message VARCHAR(150) NOT NULL DEFAULT '--- END OF JUICE KOT ---'");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS juice_kot_category_ids VARCHAR(1000) NOT NULL DEFAULT ''");

        // Quoted columns for printer_settings in case hibernate quotes identifiers
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"bill_host\" VARCHAR(100) NOT NULL DEFAULT '192.168.1.37'");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"bill_port\" INT NOT NULL DEFAULT 9100");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"bill_enabled\" BOOLEAN NOT NULL DEFAULT TRUE");

        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"kot_host\" VARCHAR(100) NOT NULL DEFAULT '192.168.1.47'");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"kot_port\" INT NOT NULL DEFAULT 9100");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"kot_enabled\" BOOLEAN NOT NULL DEFAULT TRUE");

        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"juice_kot_host\" VARCHAR(100) NOT NULL DEFAULT '192.168.1.42'");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"juice_kot_port\" INT NOT NULL DEFAULT 9100");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"juice_kot_enabled\" BOOLEAN NOT NULL DEFAULT TRUE");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"juice_kot_header_title\" VARCHAR(150) NOT NULL DEFAULT '*** JUICE ORDER ***'");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"juice_kot_subheader\" VARCHAR(500) NOT NULL DEFAULT 'NERAM - JUICE BAR'");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"juice_kot_footer_message\" VARCHAR(150) NOT NULL DEFAULT '--- END OF JUICE KOT ---'");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"juice_kot_category_ids\" VARCHAR(1000) NOT NULL DEFAULT ''");

        // Printer Spacing and Font Readability columns
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS logo_size INT NOT NULL DEFAULT 130");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS logo_gap INT NOT NULL DEFAULT 2");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS line_spacing INT NOT NULL DEFAULT 24");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS header_spacing INT NOT NULL DEFAULT 1");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS footer_spacing INT NOT NULL DEFAULT 1");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS font_size VARCHAR(50) NOT NULL DEFAULT 'DOUBLE_HEIGHT'");
        executeSqlSafe("ALTER TABLE printer_settings ADD COLUMN IF NOT EXISTS print_style VARCHAR(50) NOT NULL DEFAULT 'CRISP_DARK'");

        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"logo_size\" INT NOT NULL DEFAULT 130");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"logo_gap\" INT NOT NULL DEFAULT 2");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"line_spacing\" INT NOT NULL DEFAULT 24");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"header_spacing\" INT NOT NULL DEFAULT 1");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"footer_spacing\" INT NOT NULL DEFAULT 1");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"font_size\" VARCHAR(50) NOT NULL DEFAULT 'DOUBLE_HEIGHT'");
        executeSqlSafe("ALTER TABLE \"printer_settings\" ADD COLUMN IF NOT EXISTS \"print_style\" VARCHAR(50) NOT NULL DEFAULT 'CRISP_DARK'");

        // Bills KOT tracking columns (unquoted & quoted)
        executeSqlSafe("ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_update_count INT NOT NULL DEFAULT 0");
        executeSqlSafe("ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_updated_at TIMESTAMP");
        executeSqlSafe("ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_last_changes TEXT");
        executeSqlSafe("ALTER TABLE bills ADD COLUMN IF NOT EXISTS kot_cancelled_at TIMESTAMP");

        executeSqlSafe("ALTER TABLE \"bills\" ADD COLUMN IF NOT EXISTS \"kot_update_count\" INT NOT NULL DEFAULT 0");
        executeSqlSafe("ALTER TABLE \"bills\" ADD COLUMN IF NOT EXISTS \"kot_updated_at\" TIMESTAMP");
        executeSqlSafe("ALTER TABLE \"bills\" ADD COLUMN IF NOT EXISTS \"kot_last_changes\" TEXT");
        executeSqlSafe("ALTER TABLE \"bills\" ADD COLUMN IF NOT EXISTS \"kot_cancelled_at\" TIMESTAMP");

        // Takeaway token and item-level parcel flag columns
        executeSqlSafe("ALTER TABLE bills ADD COLUMN IF NOT EXISTS takeaway_token VARCHAR(50)");
        executeSqlSafe("ALTER TABLE \"bills\" ADD COLUMN IF NOT EXISTS \"takeaway_token\" VARCHAR(50)");
        executeSqlSafe("CREATE INDEX IF NOT EXISTS idx_bills_takeaway_token ON bills(takeaway_token)");

        executeSqlSafe("ALTER TABLE bill_items ADD COLUMN IF NOT EXISTS is_takeaway BOOLEAN NOT NULL DEFAULT FALSE");
        executeSqlSafe("ALTER TABLE \"bill_items\" ADD COLUMN IF NOT EXISTS \"is_takeaway\" BOOLEAN NOT NULL DEFAULT FALSE");

        // Parcel charge column on bills
        executeSqlSafe("ALTER TABLE bills ADD COLUMN IF NOT EXISTS parcel_charge NUMERIC(12, 2) NOT NULL DEFAULT 0.00");
        executeSqlSafe("ALTER TABLE \"bills\" ADD COLUMN IF NOT EXISTS \"parcel_charge\" NUMERIC(12, 2) NOT NULL DEFAULT 0.00");

        // Seed default printer settings row 1 if missing
        executeSqlSafe("INSERT INTO printer_settings (id, host, port, enabled, auto_cut, paper_width_mm, header_title, subheader, footer_message, bill_host, bill_port, bill_enabled, kot_host, kot_port, kot_enabled, updated_at) " +
                "VALUES (1, '192.168.1.47', 9100, TRUE, TRUE, 80, 'NERAM', 'Where time tastes better', 'THANK YOU! VISIT AGAIN', '192.168.1.37', 9100, TRUE, '192.168.1.47', 9100, TRUE, NOW()) " +
                "ON CONFLICT (id) DO NOTHING");

        // Synchronize row 1 nulls
        executeSqlSafe("UPDATE printer_settings SET " +
                "bill_host = COALESCE(NULLIF(bill_host, ''), COALESCE(NULLIF(host, ''), '192.168.1.37')), " +
                "bill_port = COALESCE(bill_port, COALESCE(port, 9100)), " +
                "bill_enabled = COALESCE(bill_enabled, COALESCE(enabled, TRUE)), " +
                "kot_host = COALESCE(NULLIF(kot_host, ''), '192.168.1.47'), " +
                "kot_port = COALESCE(kot_port, 9100), " +
                "kot_enabled = COALESCE(kot_enabled, TRUE) " +
                "WHERE id = 1");

        // Synchronize KOT header title, subheader, and footer to match UI preview design
        executeSqlSafe("UPDATE printer_settings SET kot_header_title = '*** KITCHEN ORDER ***' " +
                "WHERE kot_header_title IS NULL OR kot_header_title = '' OR kot_header_title = 'KITCHEN ORDER TICKET' OR kot_header_title = '*** KITCHEN ORDER TICKET (KOT) ***'");
        executeSqlSafe("UPDATE printer_settings SET kot_subheader = 'NERAM - MAIN KITCHEN' " +
                "WHERE kot_subheader IS NULL OR kot_subheader = '' OR kot_subheader = 'NERAM'");
        executeSqlSafe("UPDATE printer_settings SET kot_footer_message = '--- END OF KOT ---' " +
                "WHERE kot_footer_message IS NULL OR kot_footer_message = '' OR kot_footer_message = '[ AUDIT MATCH: BILL # == KOT REF # ]'");

        // Also ensure user permission columns exist
        executeSqlSafe("ALTER TABLE users ADD COLUMN IF NOT EXISTS can_take_orders BOOLEAN NOT NULL DEFAULT TRUE");
        executeSqlSafe("ALTER TABLE users ADD COLUMN IF NOT EXISTS can_manage_inventory BOOLEAN NOT NULL DEFAULT FALSE");

        // Ensure employee bank details exist (from develop V34)
        executeSqlSafe("ALTER TABLE employees ADD COLUMN IF NOT EXISTS bank_name VARCHAR(150)");
        executeSqlSafe("ALTER TABLE employees ADD COLUMN IF NOT EXISTS bank_account_number VARCHAR(100)");
        executeSqlSafe("ALTER TABLE employees ADD COLUMN IF NOT EXISTS bank_ifsc VARCHAR(50)");
        executeSqlSafe("ALTER TABLE employees ADD COLUMN IF NOT EXISTS bank_branch VARCHAR(150)");
        executeSqlSafe("ALTER TABLE employees ADD COLUMN IF NOT EXISTS bank_account_holder_name VARCHAR(255)");
        executeSqlSafe("ALTER TABLE employees ADD COLUMN IF NOT EXISTS upi_id VARCHAR(100)");

        executeSqlSafe("ALTER TABLE \"employees\" ADD COLUMN IF NOT EXISTS \"bank_name\" VARCHAR(150)");
        executeSqlSafe("ALTER TABLE \"employees\" ADD COLUMN IF NOT EXISTS \"bank_account_number\" VARCHAR(100)");
        executeSqlSafe("ALTER TABLE \"employees\" ADD COLUMN IF NOT EXISTS \"bank_ifsc\" VARCHAR(50)");
        executeSqlSafe("ALTER TABLE \"employees\" ADD COLUMN IF NOT EXISTS \"bank_branch\" VARCHAR(150)");
        executeSqlSafe("ALTER TABLE \"employees\" ADD COLUMN IF NOT EXISTS \"bank_account_holder_name\" VARCHAR(255)");
        executeSqlSafe("ALTER TABLE \"employees\" ADD COLUMN IF NOT EXISTS \"upi_id\" VARCHAR(100)");

        // Ensure inventory and supplier invoice volume columns exist
        executeSqlSafe("ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS unit_volume DOUBLE PRECISION DEFAULT 1.0");
        executeSqlSafe("ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS volume_unit VARCHAR(50)");
        executeSqlSafe("ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS total_volume DOUBLE PRECISION DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS cost_per_volume NUMERIC(12,2) DEFAULT 0.0");

        executeSqlSafe("ALTER TABLE \"inventory_items\" ADD COLUMN IF NOT EXISTS \"unit_volume\" DOUBLE PRECISION DEFAULT 1.0");
        executeSqlSafe("ALTER TABLE \"inventory_items\" ADD COLUMN IF NOT EXISTS \"volume_unit\" VARCHAR(50)");
        executeSqlSafe("ALTER TABLE \"inventory_items\" ADD COLUMN IF NOT EXISTS \"total_volume\" DOUBLE PRECISION DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE \"inventory_items\" ADD COLUMN IF NOT EXISTS \"cost_per_volume\" NUMERIC(12,2) DEFAULT 0.0");

        executeSqlSafe("UPDATE inventory_items SET unit_volume = 1.0 WHERE unit_volume IS NULL");
        executeSqlSafe("UPDATE inventory_items SET total_volume = quantity * COALESCE(unit_volume, 1.0) WHERE total_volume IS NULL");
        executeSqlSafe("UPDATE inventory_items SET volume_unit = unit WHERE volume_unit IS NULL");
        executeSqlSafe("UPDATE inventory_items SET cost_per_volume = cost_per_unit WHERE cost_per_volume IS NULL");

        executeSqlSafe("ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS unit_volume DOUBLE PRECISION DEFAULT 1.0");
        executeSqlSafe("ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS volume_unit VARCHAR(50)");
        executeSqlSafe("ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS total_volume DOUBLE PRECISION DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS cost_per_volume NUMERIC(12,2) DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS gst_rate DOUBLE PRECISION DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS gst_amount NUMERIC(12,2) DEFAULT 0.0");

        executeSqlSafe("ALTER TABLE \"supplier_invoice_items\" ADD COLUMN IF NOT EXISTS \"unit_volume\" DOUBLE PRECISION DEFAULT 1.0");
        executeSqlSafe("ALTER TABLE \"supplier_invoice_items\" ADD COLUMN IF NOT EXISTS \"volume_unit\" VARCHAR(50)");
        executeSqlSafe("ALTER TABLE \"supplier_invoice_items\" ADD COLUMN IF NOT EXISTS \"total_volume\" DOUBLE PRECISION DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE \"supplier_invoice_items\" ADD COLUMN IF NOT EXISTS \"cost_per_volume\" NUMERIC(12,2) DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE \"supplier_invoice_items\" ADD COLUMN IF NOT EXISTS \"gst_rate\" DOUBLE PRECISION DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE \"supplier_invoice_items\" ADD COLUMN IF NOT EXISTS \"gst_amount\" NUMERIC(12,2) DEFAULT 0.0");

        executeSqlSafe("ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS unit_volume DOUBLE PRECISION DEFAULT 1.0");
        executeSqlSafe("ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS volume_unit VARCHAR(50)");
        executeSqlSafe("ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS total_volume DOUBLE PRECISION DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS cost_per_volume NUMERIC(12,2) DEFAULT 0.0");

        executeSqlSafe("ALTER TABLE \"inventory_transactions\" ADD COLUMN IF NOT EXISTS \"unit_volume\" DOUBLE PRECISION DEFAULT 1.0");
        executeSqlSafe("ALTER TABLE \"inventory_transactions\" ADD COLUMN IF NOT EXISTS \"volume_unit\" VARCHAR(50)");
        executeSqlSafe("ALTER TABLE \"inventory_transactions\" ADD COLUMN IF NOT EXISTS \"total_volume\" DOUBLE PRECISION DEFAULT 0.0");
        executeSqlSafe("ALTER TABLE \"inventory_transactions\" ADD COLUMN IF NOT EXISTS \"cost_per_volume\" NUMERIC(12,2) DEFAULT 0.0");

        // Ensure item_category columns exist
        executeSqlSafe("ALTER TABLE inventory_items ADD COLUMN IF NOT EXISTS item_category VARCHAR(50) NOT NULL DEFAULT 'Stock'");
        executeSqlSafe("ALTER TABLE \"inventory_items\" ADD COLUMN IF NOT EXISTS \"item_category\" VARCHAR(50) NOT NULL DEFAULT 'Stock'");
        executeSqlSafe("UPDATE inventory_items SET item_category = 'Stock' WHERE item_category IS NULL");

        executeSqlSafe("ALTER TABLE supplier_invoice_items ADD COLUMN IF NOT EXISTS item_category VARCHAR(50) NOT NULL DEFAULT 'Stock'");
        executeSqlSafe("ALTER TABLE \"supplier_invoice_items\" ADD COLUMN IF NOT EXISTS \"item_category\" VARCHAR(50) NOT NULL DEFAULT 'Stock'");
        executeSqlSafe("UPDATE supplier_invoice_items SET item_category = 'Stock' WHERE item_category IS NULL");

        executeSqlSafe("ALTER TABLE inventory_transactions ADD COLUMN IF NOT EXISTS item_category VARCHAR(50)");
        executeSqlSafe("ALTER TABLE \"inventory_transactions\" ADD COLUMN IF NOT EXISTS \"item_category\" VARCHAR(50)");

        // Ensure recurring expenses and templates exist
        executeSqlSafe("ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS vendor_or_payee VARCHAR(255)");
        executeSqlSafe("ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS payment_method VARCHAR(100)");
        executeSqlSafe("ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'PAID'");
        executeSqlSafe("ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS due_date DATE");
        executeSqlSafe("ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS template_id BIGINT");
        executeSqlSafe("ALTER TABLE cafe_expenses ADD COLUMN IF NOT EXISTS is_recurring BOOLEAN NOT NULL DEFAULT FALSE");

        executeSqlSafe("CREATE TABLE IF NOT EXISTS expense_templates (" +
                "id BIGSERIAL PRIMARY KEY, " +
                "title VARCHAR(255) NOT NULL, " +
                "type VARCHAR(50) NOT NULL, " +
                "amount NUMERIC(12, 2) NOT NULL, " +
                "frequency VARCHAR(50) NOT NULL DEFAULT 'MONTHLY', " +
                "due_day INT NOT NULL DEFAULT 1, " +
                "vendor_or_payee VARCHAR(255), " +
                "payment_method VARCHAR(100), " +
                "notes TEXT, " +
                "is_active BOOLEAN NOT NULL DEFAULT TRUE, " +
                "last_generated_date DATE, " +
                "created_by BIGINT, " +
                "created_at TIMESTAMP NOT NULL DEFAULT NOW(), " +
                "updated_at TIMESTAMP NOT NULL DEFAULT NOW())");

        log.info("Database schema verification and self-healing completed successfully.");
    }

    private void executeSqlSafe(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("Database schema self-healing notice [{}]: {}", sql, e.getMessage());
        }
    }
}
