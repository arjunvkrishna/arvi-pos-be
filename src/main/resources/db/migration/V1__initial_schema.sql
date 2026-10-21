--
-- PostgreSQL database dump
--


-- Dumped from database version 18.6
-- Dumped by pg_dump version 18.6




--
-- Name: app_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.app_settings (
    setting_key character varying(64) NOT NULL,
    setting_value text NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(64) DEFAULT 'SYSTEM'::character varying
);


--
-- Name: attendance; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.attendance (
    id bigint NOT NULL,
    employee_id bigint NOT NULL,
    date date NOT NULL,
    check_in time(0) without time zone,
    check_out time(0) without time zone,
    status character varying(50) DEFAULT 'PRESENT'::character varying NOT NULL,
    notes text,
    marked_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: attendance_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.attendance_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: attendance_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.attendance_id_seq OWNED BY public.attendance.id;


--
-- Name: audit_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id bigint NOT NULL,
    action character varying(100) NOT NULL,
    entity_type character varying(100) NOT NULL,
    entity_id bigint,
    old_value text,
    new_value text,
    performed_by bigint,
    performed_by_name character varying(100),
    ip_address character varying(100),
    performed_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: audit_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.audit_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: audit_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.audit_logs_id_seq OWNED BY public.audit_logs.id;


--
-- Name: bill_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.bill_items (
    id bigint NOT NULL,
    bill_id bigint NOT NULL,
    menu_item_id bigint,
    item_name character varying(255) NOT NULL,
    quantity integer DEFAULT 1 NOT NULL,
    price numeric(12,2) NOT NULL,
    subtotal numeric(12,2) NOT NULL,
    printed_quantity integer DEFAULT 0 NOT NULL,
    customization character varying(500),
    is_takeaway boolean DEFAULT false NOT NULL
);


--
-- Name: bill_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.bill_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bill_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bill_items_id_seq OWNED BY public.bill_items.id;


--
-- Name: bill_refunds; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.bill_refunds (
    id bigint NOT NULL,
    bill_id bigint NOT NULL,
    amount numeric(12,2) NOT NULL,
    payment_mode character varying(50) DEFAULT 'CASH'::character varying NOT NULL,
    reason text NOT NULL,
    refunded_by bigint,
    refunded_by_name character varying(100),
    refunded_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    status character varying(50) DEFAULT 'SUCCESS'::character varying NOT NULL,
    idempotency_key character varying(150),
    external_reference character varying(150),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    shift_id bigint
);


--
-- Name: bill_refunds_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.bill_refunds_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bill_refunds_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bill_refunds_id_seq OWNED BY public.bill_refunds.id;


--
-- Name: bills; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.bills (
    id bigint NOT NULL,
    bill_number character varying(100) NOT NULL,
    table_number character varying(100),
    customer_name character varying(255),
    subtotal numeric(12,2) DEFAULT 0.0 NOT NULL,
    discount_percent double precision DEFAULT 0.0 NOT NULL,
    discount_amount numeric(12,2) DEFAULT 0.0 NOT NULL,
    tax_percent double precision DEFAULT 0.0 NOT NULL,
    tax_amount numeric(12,2) DEFAULT 0.0 NOT NULL,
    net_amount numeric(12,2) DEFAULT 0.0 NOT NULL,
    payment_mode character varying(50) DEFAULT 'CASH'::character varying NOT NULL,
    status character varying(50) DEFAULT 'PAID'::character varying NOT NULL,
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    printed_at timestamp without time zone,
    kot_printed_at timestamp without time zone,
    kot_number character varying(100),
    printed_by bigint,
    order_type character varying(20) DEFAULT 'TABLE'::character varying NOT NULL,
    outdoor_token character varying(20),
    gst_enabled boolean DEFAULT false,
    gstin character varying(50),
    cgst_percent numeric(5,2) DEFAULT 0.0,
    sgst_percent numeric(5,2) DEFAULT 0.0,
    cgst_amount numeric(12,2) DEFAULT 0.0,
    sgst_amount numeric(12,2) DEFAULT 0.0,
    round_off numeric(12,2) DEFAULT 0.0,
    voided_by bigint,
    voided_by_name character varying(100),
    voided_at timestamp without time zone,
    void_reason text,
    shift_id bigint,
    kot_update_count integer DEFAULT 0 NOT NULL,
    kot_updated_at timestamp without time zone,
    kot_last_changes text,
    kot_cancelled_at timestamp without time zone,
    promotion_id bigint,
    promotion_name character varying(150),
    promotion_percentage double precision,
    promotion_discount_amount numeric(12,2) DEFAULT 0.00,
    takeaway_token character varying(50),
    parcel_charge numeric(12,2) DEFAULT 0.00 NOT NULL,
    tenant_id bigint DEFAULT 1,
    outlet_id bigint DEFAULT 1
);


--
-- Name: bills_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.bills_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: bills_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.bills_id_seq OWNED BY public.bills.id;


--
-- Name: cafe_expenses; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.cafe_expenses (
    id bigint NOT NULL,
    type character varying(50) NOT NULL,
    description text NOT NULL,
    amount numeric(12,2) NOT NULL,
    expense_date date NOT NULL,
    month integer,
    year integer,
    paid_by character varying(100),
    receipt_ref character varying(100),
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    vendor_or_payee character varying(255),
    payment_method character varying(100),
    status character varying(50) DEFAULT 'PAID'::character varying NOT NULL,
    due_date date,
    template_id bigint,
    is_recurring boolean DEFAULT false NOT NULL,
    tenant_id bigint DEFAULT 1,
    outlet_id bigint DEFAULT 1
);


--
-- Name: cafe_expenses_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.cafe_expenses_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cafe_expenses_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cafe_expenses_id_seq OWNED BY public.cafe_expenses.id;


--
-- Name: cash_movements; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.cash_movements (
    id bigint NOT NULL,
    shift_id bigint NOT NULL,
    movement_type character varying(50) NOT NULL,
    amount numeric(12,2) NOT NULL,
    reason text NOT NULL,
    user_id bigint,
    user_name character varying(100),
    approved_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    transfer_type character varying(50) DEFAULT 'MANUAL'::character varying,
    destination_account character varying(150),
    reference_number character varying(150)
);


--
-- Name: cash_movements_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.cash_movements_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cash_movements_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cash_movements_id_seq OWNED BY public.cash_movements.id;


--
-- Name: cashier_shifts; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.cashier_shifts (
    id bigint NOT NULL,
    register_id character varying(100) DEFAULT 'REG-01'::character varying NOT NULL,
    cashier_id bigint NOT NULL,
    cashier_name character varying(100) NOT NULL,
    opened_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    opening_float numeric(12,2) DEFAULT 0.00 NOT NULL,
    status character varying(50) DEFAULT 'OPEN'::character varying NOT NULL,
    closed_at timestamp without time zone,
    closing_user_id bigint,
    closing_user_name character varying(100),
    counted_cash numeric(12,2),
    expected_cash numeric(12,2),
    variance numeric(12,2),
    notes text,
    cash_sales numeric(12,2) DEFAULT 0.00,
    upi_sales numeric(12,2) DEFAULT 0.00,
    card_sales numeric(12,2) DEFAULT 0.00,
    cash_refunds numeric(12,2) DEFAULT 0.00,
    upi_refunds numeric(12,2) DEFAULT 0.00,
    card_refunds numeric(12,2) DEFAULT 0.00,
    cash_in_total numeric(12,2) DEFAULT 0.00,
    cash_out_total numeric(12,2) DEFAULT 0.00,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    transfers_out_total numeric(12,2) DEFAULT 0.00,
    transfers_in_total numeric(12,2) DEFAULT 0.00,
    previous_shift_id bigint,
    carry_forward_ref character varying(150),
    is_corrected boolean DEFAULT false,
    corrected_counted_cash numeric(12,2),
    corrected_variance numeric(12,2),
    correction_reason text,
    corrected_by_user_id bigint,
    corrected_by_user_name character varying(100),
    corrected_at timestamp without time zone,
    tenant_id bigint DEFAULT 1,
    outlet_id bigint DEFAULT 1
);


--
-- Name: cashier_shifts_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.cashier_shifts_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: cashier_shifts_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.cashier_shifts_id_seq OWNED BY public.cashier_shifts.id;


--
-- Name: categories; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.categories (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    icon character varying(100) DEFAULT 'fa-utensils'::character varying,
    sort_order integer DEFAULT 0,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    tenant_id bigint DEFAULT 1
);


--
-- Name: categories_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.categories_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: categories_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.categories_id_seq OWNED BY public.categories.id;


--
-- Name: dining_tables; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.dining_tables (
    id bigint NOT NULL,
    name character varying(50) NOT NULL,
    active boolean DEFAULT true NOT NULL,
    display_order integer DEFAULT 0 NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    tenant_id bigint DEFAULT 1,
    outlet_id bigint DEFAULT 1
);


--
-- Name: dining_tables_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.dining_tables_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: dining_tables_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.dining_tables_id_seq OWNED BY public.dining_tables.id;


--
-- Name: employees; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.employees (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    phone character varying(50),
    email character varying(100),
    role character varying(50) NOT NULL,
    department character varying(100),
    designation character varying(100),
    basic_salary double precision DEFAULT 0.0 NOT NULL,
    joined_date date,
    address text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    paid_leaves_quota integer DEFAULT 12,
    casual_leaves_quota integer DEFAULT 12,
    bank_name character varying(150),
    bank_account_number character varying(100),
    bank_ifsc character varying(50),
    bank_branch character varying(150),
    bank_account_holder_name character varying(255),
    upi_id character varying(100),
    tenant_id bigint DEFAULT 1,
    outlet_id bigint DEFAULT 1
);


--
-- Name: employees_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.employees_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: employees_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.employees_id_seq OWNED BY public.employees.id;


--
-- Name: expense_templates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.expense_templates (
    id bigint NOT NULL,
    title character varying(255) NOT NULL,
    type character varying(50) NOT NULL,
    amount numeric(12,2) NOT NULL,
    frequency character varying(50) DEFAULT 'MONTHLY'::character varying NOT NULL,
    due_day integer DEFAULT 1 NOT NULL,
    vendor_or_payee character varying(255),
    payment_method character varying(100),
    notes text,
    is_active boolean DEFAULT true NOT NULL,
    last_generated_date date,
    created_by bigint,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL
);


--
-- Name: expense_templates_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.expense_templates_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: expense_templates_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.expense_templates_id_seq OWNED BY public.expense_templates.id;


--
-- Name: gst_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.gst_settings (
    id bigint NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    gstin character varying(50) DEFAULT '32CQRPS5214H3ZK'::character varying NOT NULL,
    cgst_percent numeric(5,2) DEFAULT 2.50 NOT NULL,
    sgst_percent numeric(5,2) DEFAULT 2.50 NOT NULL,
    round_off_enabled boolean DEFAULT false NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_by bigint
);


--
-- Name: inventory_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.inventory_items (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    unit character varying(50) DEFAULT 'pcs'::character varying NOT NULL,
    quantity double precision DEFAULT 0.0 NOT NULL,
    min_quantity double precision DEFAULT 0.0 NOT NULL,
    cost_per_unit numeric(12,2) DEFAULT 0.0 NOT NULL,
    supplier character varying(255),
    active boolean DEFAULT true NOT NULL,
    last_updated timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    unit_volume double precision DEFAULT 1.0,
    volume_unit character varying(50),
    total_volume double precision DEFAULT 0.0,
    cost_per_volume numeric(12,2) DEFAULT 0.0,
    item_category character varying(50) DEFAULT 'Stock'::character varying NOT NULL,
    tenant_id bigint DEFAULT 1,
    outlet_id bigint DEFAULT 1
);


--
-- Name: inventory_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.inventory_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_items_id_seq OWNED BY public.inventory_items.id;


--
-- Name: inventory_transactions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.inventory_transactions (
    id bigint NOT NULL,
    item_id bigint NOT NULL,
    transaction_type character varying(50) NOT NULL,
    quantity double precision NOT NULL,
    previous_stock double precision NOT NULL,
    new_stock double precision NOT NULL,
    user_id bigint,
    user_name character varying(100),
    user_role character varying(50),
    supplier_id bigint,
    supplier_name character varying(255),
    invoice_id bigint,
    invoice_reference character varying(150),
    unit_price numeric(12,2),
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    bill_id bigint,
    bill_item_id bigint,
    recipe_id bigint,
    unit character varying(50) DEFAULT 'pcs'::character varying,
    unit_volume double precision DEFAULT 1.0,
    volume_unit character varying(50),
    total_volume double precision DEFAULT 0.0,
    cost_per_volume numeric(12,2) DEFAULT 0.0,
    item_category character varying(50)
);


--
-- Name: inventory_transactions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.inventory_transactions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: inventory_transactions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.inventory_transactions_id_seq OWNED BY public.inventory_transactions.id;


--
-- Name: leave_requests; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.leave_requests (
    id bigint NOT NULL,
    employee_id bigint NOT NULL,
    from_date date NOT NULL,
    to_date date NOT NULL,
    leave_type character varying(50) DEFAULT 'CASUAL'::character varying NOT NULL,
    reason text NOT NULL,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    reviewed_by bigint,
    reviewed_at timestamp without time zone,
    review_notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: leave_requests_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.leave_requests_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: leave_requests_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.leave_requests_id_seq OWNED BY public.leave_requests.id;


--
-- Name: menu_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.menu_items (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    category_id bigint NOT NULL,
    price numeric(12,2) NOT NULL,
    description text,
    image_url character varying(500),
    is_veg boolean DEFAULT true NOT NULL,
    is_available boolean DEFAULT true NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    tenant_id bigint DEFAULT 1,
    outlet_id bigint
);


--
-- Name: menu_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.menu_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: menu_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.menu_items_id_seq OWNED BY public.menu_items.id;


--
-- Name: outlets; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.outlets (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(50) NOT NULL,
    address text,
    phone character varying(50),
    gst_number character varying(50),
    is_active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: outlets_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.outlets_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: outlets_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.outlets_id_seq OWNED BY public.outlets.id;


--
-- Name: print_jobs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.print_jobs (
    id bigint NOT NULL,
    job_id character varying(100) NOT NULL,
    bill_id bigint,
    bill_number character varying(100),
    job_type character varying(50) NOT NULL,
    target_printer_host character varying(150) NOT NULL,
    target_printer_port integer NOT NULL,
    payload bytea,
    status character varying(50) DEFAULT 'PENDING'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_eligible_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    claimed_at timestamp without time zone,
    sent_at timestamp without time zone,
    completed_at timestamp without time zone,
    lease_expires_at timestamp without time zone,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    claimed_by_bridge_id character varying(100),
    last_error_message text,
    idempotency_key character varying(150),
    bytes_printed integer DEFAULT 0
);


--
-- Name: print_jobs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.print_jobs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: print_jobs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.print_jobs_id_seq OWNED BY public.print_jobs.id;


--
-- Name: printer_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.printer_settings (
    id bigint NOT NULL,
    host character varying(100) DEFAULT '192.168.1.47'::character varying NOT NULL,
    port integer DEFAULT 9100 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    auto_cut boolean DEFAULT true NOT NULL,
    paper_width_mm integer DEFAULT 80 NOT NULL,
    header_title character varying(100) DEFAULT 'NERAM'::character varying NOT NULL,
    subheader character varying(500) DEFAULT 'Where time tastes better'::character varying NOT NULL,
    footer_message character varying(500) DEFAULT 'THANK YOU! VISIT AGAIN'::character varying NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    logo_enabled boolean DEFAULT true NOT NULL,
    subheader2 character varying(500) DEFAULT 'Infopark Expy, Rajagiri Valley, Chittethukara, Kakkanad, Kochi, Keralam 682037'::character varying,
    kot_header_title character varying(150) DEFAULT '*** KITCHEN ORDER ***'::character varying,
    kot_subheader character varying(500) DEFAULT ''::character varying,
    kot_footer_message character varying(150) DEFAULT '--- END OF KOT ---'::character varying,
    bill_host character varying(100) DEFAULT '192.168.1.37'::character varying NOT NULL,
    bill_port integer DEFAULT 9100 NOT NULL,
    bill_enabled boolean DEFAULT true NOT NULL,
    kot_host character varying(100) DEFAULT '192.168.1.47'::character varying NOT NULL,
    kot_port integer DEFAULT 9100 NOT NULL,
    kot_enabled boolean DEFAULT true NOT NULL,
    logo_size integer DEFAULT 130 NOT NULL,
    logo_gap integer DEFAULT 2 NOT NULL,
    line_spacing integer DEFAULT 24 NOT NULL,
    header_spacing integer DEFAULT 1 NOT NULL,
    footer_spacing integer DEFAULT 1 NOT NULL,
    font_size character varying(50) DEFAULT 'DOUBLE_HEIGHT'::character varying NOT NULL,
    print_style character varying(50) DEFAULT 'CRISP_DARK'::character varying NOT NULL,
    juice_kot_host character varying(100) DEFAULT '192.168.1.42'::character varying NOT NULL,
    juice_kot_port integer DEFAULT 9100 NOT NULL,
    juice_kot_enabled boolean DEFAULT true NOT NULL,
    juice_kot_header_title character varying(150) DEFAULT '*** JUICE ORDER ***'::character varying NOT NULL,
    juice_kot_subheader character varying(500) DEFAULT 'NERAM - JUICE BAR'::character varying NOT NULL,
    juice_kot_footer_message character varying(150) DEFAULT '--- END OF JUICE KOT ---'::character varying NOT NULL,
    juice_kot_category_ids character varying(1000) DEFAULT ''::character varying NOT NULL,
    tenant_id bigint DEFAULT 1,
    outlet_id bigint DEFAULT 1
);


--
-- Name: promotions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.promotions (
    id bigint NOT NULL,
    name character varying(150) NOT NULL,
    discount_percentage double precision NOT NULL,
    start_date_time timestamp without time zone NOT NULL,
    end_date_time timestamp without time zone NOT NULL,
    is_active boolean DEFAULT true NOT NULL,
    description text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    tenant_id bigint DEFAULT 1,
    outlet_id bigint
);


--
-- Name: promotions_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.promotions_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: promotions_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.promotions_id_seq OWNED BY public.promotions.id;


--
-- Name: recipes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.recipes (
    id bigint NOT NULL,
    menu_item_id bigint NOT NULL,
    inventory_item_id bigint NOT NULL,
    quantity_per_serving numeric(10,3) NOT NULL,
    unit character varying(50) DEFAULT 'g'::character varying NOT NULL,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    tenant_id bigint DEFAULT 1
);


--
-- Name: recipes_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.recipes_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: recipes_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.recipes_id_seq OWNED BY public.recipes.id;


--
-- Name: refresh_tokens; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.refresh_tokens (
    id bigint NOT NULL,
    token character varying(255) NOT NULL,
    user_id bigint NOT NULL,
    expiry_date timestamp without time zone NOT NULL,
    revoked boolean DEFAULT false NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.refresh_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.refresh_tokens_id_seq OWNED BY public.refresh_tokens.id;


--
-- Name: salaries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.salaries (
    id bigint NOT NULL,
    employee_id bigint NOT NULL,
    month integer NOT NULL,
    year integer NOT NULL,
    basic numeric(12,2) DEFAULT 0.0 NOT NULL,
    allowances numeric(12,2) DEFAULT 0.0 NOT NULL,
    deductions numeric(12,2) DEFAULT 0.0 NOT NULL,
    net_salary numeric(12,2) DEFAULT 0.0 NOT NULL,
    paid boolean DEFAULT false NOT NULL,
    paid_date date,
    payment_mode character varying(50) DEFAULT 'CASH'::character varying,
    notes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: salaries_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.salaries_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: salaries_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.salaries_id_seq OWNED BY public.salaries.id;


--
-- Name: shift_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.shift_settings (
    id bigint DEFAULT 1 NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by bigint,
    updated_by_name character varying(100),
    variance_threshold numeric(12,2) DEFAULT 500.00,
    large_movement_threshold numeric(12,2) DEFAULT 5000.00,
    flag_missing_count boolean DEFAULT true,
    flag_transfers_without_ref boolean DEFAULT true
);


--
-- Name: supplier_invoice_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.supplier_invoice_items (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    inventory_item_id bigint,
    product_name character varying(255) NOT NULL,
    quantity double precision NOT NULL,
    unit_price numeric(12,2) NOT NULL,
    total_price numeric(12,2) NOT NULL,
    unit character varying(50) DEFAULT 'pcs'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    unit_volume double precision DEFAULT 1.0,
    volume_unit character varying(50),
    total_volume double precision DEFAULT 0.0,
    cost_per_volume numeric(12,2) DEFAULT 0.0,
    item_category character varying(50) DEFAULT 'Stock'::character varying NOT NULL,
    gst_rate double precision DEFAULT 0.0,
    gst_amount numeric(12,2) DEFAULT 0.0
);


--
-- Name: supplier_invoice_items_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.supplier_invoice_items_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: supplier_invoice_items_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.supplier_invoice_items_id_seq OWNED BY public.supplier_invoice_items.id;


--
-- Name: supplier_invoices; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.supplier_invoices (
    id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    invoice_number character varying(150) NOT NULL,
    invoice_date date NOT NULL,
    due_date date,
    total_amount numeric(12,2) DEFAULT 0.0 NOT NULL,
    paid_amount numeric(12,2) DEFAULT 0.0 NOT NULL,
    outstanding_amount numeric(12,2) DEFAULT 0.0 NOT NULL,
    status character varying(50) DEFAULT 'UNPAID'::character varying NOT NULL,
    description text,
    items_received text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    subtotal double precision DEFAULT 0.0,
    gst_rate double precision DEFAULT 0.0,
    gst_amount double precision DEFAULT 0.0,
    other_charges double precision DEFAULT 0.0,
    discount double precision DEFAULT 0.0
);


--
-- Name: supplier_invoices_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.supplier_invoices_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: supplier_invoices_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.supplier_invoices_id_seq OWNED BY public.supplier_invoices.id;


--
-- Name: supplier_payments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.supplier_payments (
    id bigint NOT NULL,
    invoice_id bigint NOT NULL,
    supplier_id bigint NOT NULL,
    payment_date date NOT NULL,
    amount numeric(12,2) NOT NULL,
    payment_mode character varying(50) DEFAULT 'CASH'::character varying NOT NULL,
    reference_number character varying(150),
    notes text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: supplier_payments_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.supplier_payments_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: supplier_payments_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.supplier_payments_id_seq OWNED BY public.supplier_payments.id;


--
-- Name: suppliers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.suppliers (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    contact_person character varying(255),
    phone character varying(50),
    email character varying(150),
    address text,
    gst_number character varying(50),
    payment_terms integer DEFAULT 30 NOT NULL,
    category character varying(100),
    bank_name character varying(150),
    bank_account character varying(100),
    bank_ifsc character varying(20),
    upi_id character varying(150),
    notes text,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    tenant_id bigint DEFAULT 1
);


--
-- Name: suppliers_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.suppliers_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: suppliers_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.suppliers_id_seq OWNED BY public.suppliers.id;


--
-- Name: system_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.system_notifications (
    id bigint NOT NULL,
    title character varying(255) NOT NULL,
    message text NOT NULL,
    type character varying(50) NOT NULL,
    reference_id bigint,
    status character varying(50) DEFAULT 'UNREAD'::character varying NOT NULL,
    created_by character varying(100),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: system_notifications_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.system_notifications_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: system_notifications_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.system_notifications_id_seq OWNED BY public.system_notifications.id;


--
-- Name: telegram_daily_send_locks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.telegram_daily_send_locks (
    report_date date NOT NULL,
    status character varying(20) DEFAULT 'IN_PROGRESS'::character varying NOT NULL,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp without time zone
);


--
-- Name: telegram_delivery_logs; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.telegram_delivery_logs (
    id bigint NOT NULL,
    report_date date NOT NULL,
    recipient_id bigint,
    user_id bigint,
    manager_name character varying(100) NOT NULL,
    chat_id_masked character varying(50),
    status character varying(20) NOT NULL,
    error_description character varying(500),
    items_count integer DEFAULT 0 NOT NULL,
    delivery_type character varying(20) DEFAULT 'DAILY_REPORT'::character varying NOT NULL,
    sent_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: telegram_delivery_logs_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.telegram_delivery_logs_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: telegram_delivery_logs_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.telegram_delivery_logs_id_seq OWNED BY public.telegram_delivery_logs.id;


--
-- Name: telegram_recipients; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.telegram_recipients (
    id bigint NOT NULL,
    user_id bigint NOT NULL,
    telegram_chat_id character varying(100),
    telegram_username character varying(100),
    telegram_first_name character varying(100),
    status character varying(30) DEFAULT 'NOT_CONFIGURED'::character varying NOT NULL,
    selected boolean DEFAULT true NOT NULL,
    link_code character varying(64),
    link_code_expires_at timestamp without time zone,
    verified_at timestamp without time zone,
    last_test_sent_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: telegram_recipients_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.telegram_recipients_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: telegram_recipients_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.telegram_recipients_id_seq OWNED BY public.telegram_recipients.id;


--
-- Name: telegram_settings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.telegram_settings (
    id bigint NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    daily_send_time character varying(8) DEFAULT '09:00'::character varying NOT NULL,
    timezone character varying(50) DEFAULT 'Asia/Kolkata'::character varying NOT NULL,
    last_run_date date,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: telegram_settings_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.telegram_settings_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: telegram_settings_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.telegram_settings_id_seq OWNED BY public.telegram_settings.id;


--
-- Name: tenants; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.tenants (
    id bigint NOT NULL,
    name character varying(255) NOT NULL,
    code character varying(100) NOT NULL,
    contact_email character varying(255),
    contact_phone character varying(50),
    address text,
    subscription_plan character varying(50) DEFAULT 'PRO'::character varying NOT NULL,
    status character varying(50) DEFAULT 'ACTIVE'::character varying NOT NULL,
    max_outlets integer DEFAULT 5 NOT NULL,
    currency character varying(10) DEFAULT 'INR'::character varying NOT NULL,
    currency_symbol character varying(10) DEFAULT '₹'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    logo_url text,
    gst_number character varying(64)
);


--
-- Name: tenants_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.tenants_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: tenants_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.tenants_id_seq OWNED BY public.tenants.id;


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE IF NOT EXISTS public.users (
    id bigint NOT NULL,
    username character varying(100) NOT NULL,
    password_hash character varying(255) NOT NULL,
    role character varying(50) NOT NULL,
    employee_id bigint,
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    can_take_orders boolean DEFAULT true NOT NULL,
    can_manage_inventory boolean DEFAULT false NOT NULL,
    must_change_password boolean DEFAULT false NOT NULL,
    tenant_id bigint,
    outlet_id bigint,
    can_bill boolean DEFAULT true NOT NULL,
    can_manage_expenses boolean DEFAULT false NOT NULL,
    can_view_reports boolean DEFAULT false NOT NULL
);


--
-- Name: users_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE IF NOT EXISTS public.users_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: users_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_id_seq OWNED BY public.users.id;


--
-- Name: attendance id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance ALTER COLUMN id SET DEFAULT nextval('public.attendance_id_seq'::regclass);


--
-- Name: audit_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs ALTER COLUMN id SET DEFAULT nextval('public.audit_logs_id_seq'::regclass);


--
-- Name: bill_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_items ALTER COLUMN id SET DEFAULT nextval('public.bill_items_id_seq'::regclass);


--
-- Name: bill_refunds id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_refunds ALTER COLUMN id SET DEFAULT nextval('public.bill_refunds_id_seq'::regclass);


--
-- Name: bills id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills ALTER COLUMN id SET DEFAULT nextval('public.bills_id_seq'::regclass);


--
-- Name: cafe_expenses id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cafe_expenses ALTER COLUMN id SET DEFAULT nextval('public.cafe_expenses_id_seq'::regclass);


--
-- Name: cash_movements id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_movements ALTER COLUMN id SET DEFAULT nextval('public.cash_movements_id_seq'::regclass);


--
-- Name: cashier_shifts id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cashier_shifts ALTER COLUMN id SET DEFAULT nextval('public.cashier_shifts_id_seq'::regclass);


--
-- Name: categories id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories ALTER COLUMN id SET DEFAULT nextval('public.categories_id_seq'::regclass);


--
-- Name: dining_tables id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_tables ALTER COLUMN id SET DEFAULT nextval('public.dining_tables_id_seq'::regclass);


--
-- Name: employees id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees ALTER COLUMN id SET DEFAULT nextval('public.employees_id_seq'::regclass);


--
-- Name: expense_templates id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_templates ALTER COLUMN id SET DEFAULT nextval('public.expense_templates_id_seq'::regclass);


--
-- Name: inventory_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items ALTER COLUMN id SET DEFAULT nextval('public.inventory_items_id_seq'::regclass);


--
-- Name: inventory_transactions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions ALTER COLUMN id SET DEFAULT nextval('public.inventory_transactions_id_seq'::regclass);


--
-- Name: leave_requests id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests ALTER COLUMN id SET DEFAULT nextval('public.leave_requests_id_seq'::regclass);


--
-- Name: menu_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_items ALTER COLUMN id SET DEFAULT nextval('public.menu_items_id_seq'::regclass);


--
-- Name: outlets id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outlets ALTER COLUMN id SET DEFAULT nextval('public.outlets_id_seq'::regclass);


--
-- Name: print_jobs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.print_jobs ALTER COLUMN id SET DEFAULT nextval('public.print_jobs_id_seq'::regclass);


--
-- Name: promotions id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions ALTER COLUMN id SET DEFAULT nextval('public.promotions_id_seq'::regclass);


--
-- Name: recipes id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipes ALTER COLUMN id SET DEFAULT nextval('public.recipes_id_seq'::regclass);


--
-- Name: refresh_tokens id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens ALTER COLUMN id SET DEFAULT nextval('public.refresh_tokens_id_seq'::regclass);


--
-- Name: salaries id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salaries ALTER COLUMN id SET DEFAULT nextval('public.salaries_id_seq'::regclass);


--
-- Name: supplier_invoice_items id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoice_items ALTER COLUMN id SET DEFAULT nextval('public.supplier_invoice_items_id_seq'::regclass);


--
-- Name: supplier_invoices id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoices ALTER COLUMN id SET DEFAULT nextval('public.supplier_invoices_id_seq'::regclass);


--
-- Name: supplier_payments id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_payments ALTER COLUMN id SET DEFAULT nextval('public.supplier_payments_id_seq'::regclass);


--
-- Name: suppliers id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers ALTER COLUMN id SET DEFAULT nextval('public.suppliers_id_seq'::regclass);


--
-- Name: system_notifications id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notifications ALTER COLUMN id SET DEFAULT nextval('public.system_notifications_id_seq'::regclass);


--
-- Name: telegram_delivery_logs id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_delivery_logs ALTER COLUMN id SET DEFAULT nextval('public.telegram_delivery_logs_id_seq'::regclass);


--
-- Name: telegram_recipients id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_recipients ALTER COLUMN id SET DEFAULT nextval('public.telegram_recipients_id_seq'::regclass);


--
-- Name: telegram_settings id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_settings ALTER COLUMN id SET DEFAULT nextval('public.telegram_settings_id_seq'::regclass);


--
-- Name: tenants id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants ALTER COLUMN id SET DEFAULT nextval('public.tenants_id_seq'::regclass);


--
-- Name: users id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN id SET DEFAULT nextval('public.users_id_seq'::regclass);


--
-- Name: app_settings app_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_settings
    ADD CONSTRAINT app_settings_pkey PRIMARY KEY (setting_key);


--
-- Name: attendance attendance_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT attendance_pkey PRIMARY KEY (id);


--
-- Name: audit_logs audit_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT audit_logs_pkey PRIMARY KEY (id);


--
-- Name: bill_items bill_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_items
    ADD CONSTRAINT bill_items_pkey PRIMARY KEY (id);


--
-- Name: bill_refunds bill_refunds_idempotency_key_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_refunds
    ADD CONSTRAINT bill_refunds_idempotency_key_key UNIQUE (idempotency_key);


--
-- Name: bill_refunds bill_refunds_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_refunds
    ADD CONSTRAINT bill_refunds_pkey PRIMARY KEY (id);


--
-- Name: bills bills_bill_number_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT bills_bill_number_key UNIQUE (bill_number);


--
-- Name: bills bills_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT bills_pkey PRIMARY KEY (id);


--
-- Name: cafe_expenses cafe_expenses_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cafe_expenses
    ADD CONSTRAINT cafe_expenses_pkey PRIMARY KEY (id);


--
-- Name: cash_movements cash_movements_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_movements
    ADD CONSTRAINT cash_movements_pkey PRIMARY KEY (id);


--
-- Name: cashier_shifts cashier_shifts_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cashier_shifts
    ADD CONSTRAINT cashier_shifts_pkey PRIMARY KEY (id);


--
-- Name: categories categories_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_name_key UNIQUE (name);


--
-- Name: categories categories_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_pkey PRIMARY KEY (id);


--
-- Name: dining_tables dining_tables_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_tables
    ADD CONSTRAINT dining_tables_name_key UNIQUE (name);


--
-- Name: dining_tables dining_tables_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_tables
    ADD CONSTRAINT dining_tables_pkey PRIMARY KEY (id);


--
-- Name: employees employees_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_pkey PRIMARY KEY (id);


--
-- Name: expense_templates expense_templates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.expense_templates
    ADD CONSTRAINT expense_templates_pkey PRIMARY KEY (id);


--
-- Name: gst_settings gst_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gst_settings
    ADD CONSTRAINT gst_settings_pkey PRIMARY KEY (id);


--
-- Name: inventory_items inventory_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT inventory_items_pkey PRIMARY KEY (id);


--
-- Name: inventory_transactions inventory_transactions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_pkey PRIMARY KEY (id);


--
-- Name: leave_requests leave_requests_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT leave_requests_pkey PRIMARY KEY (id);


--
-- Name: menu_items menu_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_items
    ADD CONSTRAINT menu_items_pkey PRIMARY KEY (id);


--
-- Name: outlets outlets_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outlets
    ADD CONSTRAINT outlets_pkey PRIMARY KEY (id);


--
-- Name: print_jobs print_jobs_job_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.print_jobs
    ADD CONSTRAINT print_jobs_job_id_key UNIQUE (job_id);


--
-- Name: print_jobs print_jobs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.print_jobs
    ADD CONSTRAINT print_jobs_pkey PRIMARY KEY (id);


--
-- Name: printer_settings printer_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_settings
    ADD CONSTRAINT printer_settings_pkey PRIMARY KEY (id);


--
-- Name: promotions promotions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions
    ADD CONSTRAINT promotions_pkey PRIMARY KEY (id);


--
-- Name: recipes recipes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT recipes_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_key UNIQUE (token);


--
-- Name: salaries salaries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salaries
    ADD CONSTRAINT salaries_pkey PRIMARY KEY (id);


--
-- Name: shift_settings shift_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shift_settings
    ADD CONSTRAINT shift_settings_pkey PRIMARY KEY (id);


--
-- Name: supplier_invoice_items supplier_invoice_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoice_items
    ADD CONSTRAINT supplier_invoice_items_pkey PRIMARY KEY (id);


--
-- Name: supplier_invoices supplier_invoices_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoices
    ADD CONSTRAINT supplier_invoices_pkey PRIMARY KEY (id);


--
-- Name: supplier_payments supplier_payments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_payments
    ADD CONSTRAINT supplier_payments_pkey PRIMARY KEY (id);


--
-- Name: suppliers suppliers_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_pkey PRIMARY KEY (id);


--
-- Name: system_notifications system_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.system_notifications
    ADD CONSTRAINT system_notifications_pkey PRIMARY KEY (id);


--
-- Name: telegram_daily_send_locks telegram_daily_send_locks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_daily_send_locks
    ADD CONSTRAINT telegram_daily_send_locks_pkey PRIMARY KEY (report_date);


--
-- Name: telegram_delivery_logs telegram_delivery_logs_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_delivery_logs
    ADD CONSTRAINT telegram_delivery_logs_pkey PRIMARY KEY (id);


--
-- Name: telegram_recipients telegram_recipients_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_recipients
    ADD CONSTRAINT telegram_recipients_pkey PRIMARY KEY (id);


--
-- Name: telegram_recipients telegram_recipients_user_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_recipients
    ADD CONSTRAINT telegram_recipients_user_id_key UNIQUE (user_id);


--
-- Name: telegram_settings telegram_settings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_settings
    ADD CONSTRAINT telegram_settings_pkey PRIMARY KEY (id);


--
-- Name: tenants tenants_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_code_key UNIQUE (code);


--
-- Name: tenants tenants_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tenants
    ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);


--
-- Name: recipes uk_recipe_menu_inventory; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT uk_recipe_menu_inventory UNIQUE (menu_item_id, inventory_item_id);


--
-- Name: salaries uk_salary_emp_month_year; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salaries
    ADD CONSTRAINT uk_salary_emp_month_year UNIQUE (employee_id, month, year);


--
-- Name: supplier_invoices uk_supplier_invoice; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoices
    ADD CONSTRAINT uk_supplier_invoice UNIQUE (supplier_id, invoice_number);


--
-- Name: outlets uk_tenant_outlet_code; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outlets
    ADD CONSTRAINT uk_tenant_outlet_code UNIQUE (tenant_id, code);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: idx_att_emp_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_att_emp_date ON public.attendance USING btree (employee_id, date);


--
-- Name: idx_audit_logs_action_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_action_date ON public.audit_logs USING btree (action, performed_at DESC);


--
-- Name: idx_audit_logs_perf_id_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_perf_id_date ON public.audit_logs USING btree (performed_by, performed_at DESC);


--
-- Name: idx_audit_logs_perf_user_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_audit_logs_perf_user_date ON public.audit_logs USING btree (performed_by_name, performed_at DESC);


--
-- Name: idx_bill_items_bill_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bill_items_bill_id ON public.bill_items USING btree (bill_id);


--
-- Name: idx_bill_shift; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bill_shift ON public.bills USING btree (shift_id);


--
-- Name: idx_bills_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bills_created_at ON public.bills USING btree (created_at DESC);


--
-- Name: idx_bills_status_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bills_status_created_at ON public.bills USING btree (status, created_at);


--
-- Name: idx_bills_takeaway_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bills_takeaway_token ON public.bills USING btree (takeaway_token);


--
-- Name: idx_bills_tenant_outlet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_bills_tenant_outlet ON public.bills USING btree (tenant_id, outlet_id, created_at DESC);


--
-- Name: idx_cafe_expenses_month_year; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cafe_expenses_month_year ON public.cafe_expenses USING btree (year, month);


--
-- Name: idx_cafe_expenses_template_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_cafe_expenses_template_id ON public.cafe_expenses USING btree (template_id);


--
-- Name: idx_categories_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_categories_tenant ON public.categories USING btree (tenant_id, sort_order);


--
-- Name: idx_dining_tables_tenant_outlet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_dining_tables_tenant_outlet ON public.dining_tables USING btree (tenant_id, outlet_id);


--
-- Name: idx_expense_templates_is_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expense_templates_is_active ON public.expense_templates USING btree (is_active);


--
-- Name: idx_expenses_tenant_outlet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_expenses_tenant_outlet ON public.cafe_expenses USING btree (tenant_id, outlet_id, expense_date DESC);


--
-- Name: idx_inv_item_inventory; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_item_inventory ON public.supplier_invoice_items USING btree (inventory_item_id);


--
-- Name: idx_inv_item_invoice; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inv_item_invoice ON public.supplier_invoice_items USING btree (invoice_id);


--
-- Name: idx_inventory_items_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_items_category ON public.inventory_items USING btree (item_category);


--
-- Name: idx_inventory_tenant_outlet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_inventory_tenant_outlet ON public.inventory_items USING btree (tenant_id, outlet_id);


--
-- Name: idx_menu_items_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_menu_items_tenant ON public.menu_items USING btree (tenant_id, category_id);


--
-- Name: idx_movement_shift; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movement_shift ON public.cash_movements USING btree (shift_id);


--
-- Name: idx_movement_transfer_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movement_transfer_type ON public.cash_movements USING btree (transfer_type);


--
-- Name: idx_movement_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_movement_type ON public.cash_movements USING btree (movement_type);


--
-- Name: idx_outlets_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_outlets_tenant ON public.outlets USING btree (tenant_id, is_active);


--
-- Name: idx_print_jobs_bill_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_print_jobs_bill_id ON public.print_jobs USING btree (bill_id);


--
-- Name: idx_print_jobs_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_print_jobs_created_at ON public.print_jobs USING btree (created_at);


--
-- Name: idx_print_jobs_idempotency_key; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_print_jobs_idempotency_key ON public.print_jobs USING btree (idempotency_key);


--
-- Name: idx_print_jobs_lease_expires; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_print_jobs_lease_expires ON public.print_jobs USING btree (status, lease_expires_at);


--
-- Name: idx_print_jobs_status_eligible; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_print_jobs_status_eligible ON public.print_jobs USING btree (status, next_eligible_at);


--
-- Name: idx_promotions_active_dates; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_promotions_active_dates ON public.promotions USING btree (is_active, start_date_time, end_date_time);


--
-- Name: idx_recipe_inventory_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_recipe_inventory_item ON public.recipes USING btree (inventory_item_id);


--
-- Name: idx_recipe_menu_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_recipe_menu_item ON public.recipes USING btree (menu_item_id);


--
-- Name: idx_refresh_tokens_expiry_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_expiry_date ON public.refresh_tokens USING btree (expiry_date);


--
-- Name: idx_refresh_tokens_token; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_token ON public.refresh_tokens USING btree (token);


--
-- Name: idx_refresh_tokens_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refresh_tokens_user_id ON public.refresh_tokens USING btree (user_id);


--
-- Name: idx_refund_bill_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refund_bill_id ON public.bill_refunds USING btree (bill_id);


--
-- Name: idx_refund_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refund_created_at ON public.bill_refunds USING btree (created_at);


--
-- Name: idx_refund_idempotency; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refund_idempotency ON public.bill_refunds USING btree (idempotency_key);


--
-- Name: idx_refund_shift; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_refund_shift ON public.bill_refunds USING btree (shift_id);


--
-- Name: idx_shift_cashier_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_cashier_id ON public.cashier_shifts USING btree (cashier_id);


--
-- Name: idx_shift_closed_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_closed_at ON public.cashier_shifts USING btree (closed_at);


--
-- Name: idx_shift_opened_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_opened_at ON public.cashier_shifts USING btree (opened_at);


--
-- Name: idx_shift_prev_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_prev_id ON public.cashier_shifts USING btree (previous_shift_id);


--
-- Name: idx_shift_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shift_status ON public.cashier_shifts USING btree (status);


--
-- Name: idx_shifts_tenant_outlet; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_shifts_tenant_outlet ON public.cashier_shifts USING btree (tenant_id, outlet_id, status);


--
-- Name: idx_supplier_invoice_items_category; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_supplier_invoice_items_category ON public.supplier_invoice_items USING btree (item_category);


--
-- Name: idx_system_notifications_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_notifications_created_at ON public.system_notifications USING btree (created_at DESC);


--
-- Name: idx_system_notifications_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_system_notifications_status ON public.system_notifications USING btree (status);


--
-- Name: idx_telegram_delivery_logs_report_date; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_telegram_delivery_logs_report_date ON public.telegram_delivery_logs USING btree (report_date);


--
-- Name: idx_telegram_delivery_logs_sent_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_telegram_delivery_logs_sent_at ON public.telegram_delivery_logs USING btree (sent_at);


--
-- Name: idx_telegram_recipients_link_code; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_telegram_recipients_link_code ON public.telegram_recipients USING btree (link_code);


--
-- Name: idx_telegram_recipients_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_telegram_recipients_status ON public.telegram_recipients USING btree (status);


--
-- Name: idx_telegram_recipients_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_telegram_recipients_user_id ON public.telegram_recipients USING btree (user_id);


--
-- Name: idx_txn_created_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_txn_created_at ON public.inventory_transactions USING btree (created_at);


--
-- Name: idx_txn_item; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_txn_item ON public.inventory_transactions USING btree (item_id);


--
-- Name: idx_txn_supplier; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_txn_supplier ON public.inventory_transactions USING btree (supplier_id);


--
-- Name: idx_txn_type; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_txn_type ON public.inventory_transactions USING btree (transaction_type);


--
-- Name: idx_txn_user; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_txn_user ON public.inventory_transactions USING btree (user_id);


--
-- Name: idx_users_tenant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_tenant ON public.users USING btree (tenant_id, role);


--
-- Name: uq_one_open_shift_per_register; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_one_open_shift_per_register ON public.cashier_shifts USING btree (register_id) WHERE ((status)::text = 'OPEN'::text);


--
-- Name: uq_sale_consumption_per_item; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_sale_consumption_per_item ON public.inventory_transactions USING btree (bill_item_id, transaction_type) WHERE (((transaction_type)::text = 'SALE_CONSUMPTION'::text) AND (bill_item_id IS NOT NULL));


--
-- Name: uq_single_open_cashier_shift; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX uq_single_open_cashier_shift ON public.cashier_shifts USING btree (status) WHERE ((status)::text = 'OPEN'::text);


--
-- Name: bill_refunds bill_refunds_bill_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_refunds
    ADD CONSTRAINT bill_refunds_bill_id_fkey FOREIGN KEY (bill_id) REFERENCES public.bills(id) ON DELETE RESTRICT;


--
-- Name: bill_refunds bill_refunds_refunded_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_refunds
    ADD CONSTRAINT bill_refunds_refunded_by_fkey FOREIGN KEY (refunded_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: bill_refunds bill_refunds_shift_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_refunds
    ADD CONSTRAINT bill_refunds_shift_id_fkey FOREIGN KEY (shift_id) REFERENCES public.cashier_shifts(id) ON DELETE SET NULL;


--
-- Name: bills bills_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT bills_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: bills bills_shift_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT bills_shift_id_fkey FOREIGN KEY (shift_id) REFERENCES public.cashier_shifts(id) ON DELETE SET NULL;


--
-- Name: bills bills_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT bills_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: bills bills_voided_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT bills_voided_by_fkey FOREIGN KEY (voided_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: cafe_expenses cafe_expenses_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cafe_expenses
    ADD CONSTRAINT cafe_expenses_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: cafe_expenses cafe_expenses_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cafe_expenses
    ADD CONSTRAINT cafe_expenses_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: cash_movements cash_movements_shift_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_movements
    ADD CONSTRAINT cash_movements_shift_id_fkey FOREIGN KEY (shift_id) REFERENCES public.cashier_shifts(id) ON DELETE RESTRICT;


--
-- Name: cash_movements cash_movements_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cash_movements
    ADD CONSTRAINT cash_movements_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: cashier_shifts cashier_shifts_cashier_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cashier_shifts
    ADD CONSTRAINT cashier_shifts_cashier_id_fkey FOREIGN KEY (cashier_id) REFERENCES public.users(id) ON DELETE RESTRICT;


--
-- Name: cashier_shifts cashier_shifts_closing_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cashier_shifts
    ADD CONSTRAINT cashier_shifts_closing_user_id_fkey FOREIGN KEY (closing_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: cashier_shifts cashier_shifts_corrected_by_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cashier_shifts
    ADD CONSTRAINT cashier_shifts_corrected_by_user_id_fkey FOREIGN KEY (corrected_by_user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: cashier_shifts cashier_shifts_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cashier_shifts
    ADD CONSTRAINT cashier_shifts_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: cashier_shifts cashier_shifts_previous_shift_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cashier_shifts
    ADD CONSTRAINT cashier_shifts_previous_shift_id_fkey FOREIGN KEY (previous_shift_id) REFERENCES public.cashier_shifts(id) ON DELETE SET NULL;


--
-- Name: cashier_shifts cashier_shifts_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cashier_shifts
    ADD CONSTRAINT cashier_shifts_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: categories categories_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.categories
    ADD CONSTRAINT categories_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: dining_tables dining_tables_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_tables
    ADD CONSTRAINT dining_tables_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: dining_tables dining_tables_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.dining_tables
    ADD CONSTRAINT dining_tables_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: employees employees_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: employees employees_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.employees
    ADD CONSTRAINT employees_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: attendance fk_att_emp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT fk_att_emp FOREIGN KEY (employee_id) REFERENCES public.employees(id);


--
-- Name: attendance fk_att_marked_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.attendance
    ADD CONSTRAINT fk_att_marked_by FOREIGN KEY (marked_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: audit_logs fk_audit_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.audit_logs
    ADD CONSTRAINT fk_audit_user FOREIGN KEY (performed_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: bills fk_bill_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fk_bill_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: bill_items fk_bill_items_bill; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_items
    ADD CONSTRAINT fk_bill_items_bill FOREIGN KEY (bill_id) REFERENCES public.bills(id) ON DELETE CASCADE;


--
-- Name: bill_items fk_bill_items_menu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bill_items
    ADD CONSTRAINT fk_bill_items_menu FOREIGN KEY (menu_item_id) REFERENCES public.menu_items(id) ON DELETE SET NULL;


--
-- Name: bills fk_bill_printed_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fk_bill_printed_by FOREIGN KEY (printed_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: bills fk_bills_promotion; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.bills
    ADD CONSTRAINT fk_bills_promotion FOREIGN KEY (promotion_id) REFERENCES public.promotions(id) ON DELETE SET NULL;


--
-- Name: cafe_expenses fk_expense_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.cafe_expenses
    ADD CONSTRAINT fk_expense_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: supplier_invoices fk_inv_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoices
    ADD CONSTRAINT fk_inv_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: supplier_invoice_items fk_inv_item_inventory; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoice_items
    ADD CONSTRAINT fk_inv_item_inventory FOREIGN KEY (inventory_item_id) REFERENCES public.inventory_items(id) ON DELETE SET NULL;


--
-- Name: supplier_invoice_items fk_inv_item_invoice; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoice_items
    ADD CONSTRAINT fk_inv_item_invoice FOREIGN KEY (invoice_id) REFERENCES public.supplier_invoices(id) ON DELETE CASCADE;


--
-- Name: supplier_invoices fk_inv_supplier; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_invoices
    ADD CONSTRAINT fk_inv_supplier FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: leave_requests fk_leave_emp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT fk_leave_emp FOREIGN KEY (employee_id) REFERENCES public.employees(id);


--
-- Name: leave_requests fk_leave_reviewed_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.leave_requests
    ADD CONSTRAINT fk_leave_reviewed_by FOREIGN KEY (reviewed_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: menu_items fk_menu_category; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_items
    ADD CONSTRAINT fk_menu_category FOREIGN KEY (category_id) REFERENCES public.categories(id);


--
-- Name: supplier_payments fk_pay_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_payments
    ADD CONSTRAINT fk_pay_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: supplier_payments fk_pay_invoice; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_payments
    ADD CONSTRAINT fk_pay_invoice FOREIGN KEY (invoice_id) REFERENCES public.supplier_invoices(id) ON DELETE CASCADE;


--
-- Name: supplier_payments fk_pay_supplier; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.supplier_payments
    ADD CONSTRAINT fk_pay_supplier FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id);


--
-- Name: promotions fk_promotions_created_by; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions
    ADD CONSTRAINT fk_promotions_created_by FOREIGN KEY (created_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: salaries fk_salary_emp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.salaries
    ADD CONSTRAINT fk_salary_emp FOREIGN KEY (employee_id) REFERENCES public.employees(id);


--
-- Name: inventory_transactions fk_txn_invoice; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT fk_txn_invoice FOREIGN KEY (invoice_id) REFERENCES public.supplier_invoices(id) ON DELETE SET NULL;


--
-- Name: inventory_transactions fk_txn_item; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT fk_txn_item FOREIGN KEY (item_id) REFERENCES public.inventory_items(id) ON DELETE CASCADE;


--
-- Name: inventory_transactions fk_txn_supplier; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT fk_txn_supplier FOREIGN KEY (supplier_id) REFERENCES public.suppliers(id) ON DELETE SET NULL;


--
-- Name: inventory_transactions fk_txn_user; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT fk_txn_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: gst_settings gst_settings_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.gst_settings
    ADD CONSTRAINT gst_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: inventory_items inventory_items_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT inventory_items_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: inventory_items inventory_items_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_items
    ADD CONSTRAINT inventory_items_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: inventory_transactions inventory_transactions_bill_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_bill_id_fkey FOREIGN KEY (bill_id) REFERENCES public.bills(id) ON DELETE SET NULL;


--
-- Name: inventory_transactions inventory_transactions_bill_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_bill_item_id_fkey FOREIGN KEY (bill_item_id) REFERENCES public.bill_items(id) ON DELETE SET NULL;


--
-- Name: inventory_transactions inventory_transactions_recipe_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.inventory_transactions
    ADD CONSTRAINT inventory_transactions_recipe_id_fkey FOREIGN KEY (recipe_id) REFERENCES public.recipes(id) ON DELETE SET NULL;


--
-- Name: menu_items menu_items_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_items
    ADD CONSTRAINT menu_items_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: menu_items menu_items_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.menu_items
    ADD CONSTRAINT menu_items_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: outlets outlets_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.outlets
    ADD CONSTRAINT outlets_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: printer_settings printer_settings_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_settings
    ADD CONSTRAINT printer_settings_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: printer_settings printer_settings_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.printer_settings
    ADD CONSTRAINT printer_settings_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: promotions promotions_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions
    ADD CONSTRAINT promotions_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: promotions promotions_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.promotions
    ADD CONSTRAINT promotions_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: recipes recipes_inventory_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT recipes_inventory_item_id_fkey FOREIGN KEY (inventory_item_id) REFERENCES public.inventory_items(id) ON DELETE RESTRICT;


--
-- Name: recipes recipes_menu_item_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT recipes_menu_item_id_fkey FOREIGN KEY (menu_item_id) REFERENCES public.menu_items(id) ON DELETE CASCADE;


--
-- Name: recipes recipes_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.recipes
    ADD CONSTRAINT recipes_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: shift_settings shift_settings_updated_by_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.shift_settings
    ADD CONSTRAINT shift_settings_updated_by_fkey FOREIGN KEY (updated_by) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: suppliers suppliers_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.suppliers
    ADD CONSTRAINT suppliers_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE;


--
-- Name: telegram_delivery_logs telegram_delivery_logs_recipient_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_delivery_logs
    ADD CONSTRAINT telegram_delivery_logs_recipient_id_fkey FOREIGN KEY (recipient_id) REFERENCES public.telegram_recipients(id) ON DELETE SET NULL;


--
-- Name: telegram_delivery_logs telegram_delivery_logs_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_delivery_logs
    ADD CONSTRAINT telegram_delivery_logs_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL;


--
-- Name: telegram_recipients telegram_recipients_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.telegram_recipients
    ADD CONSTRAINT telegram_recipients_user_id_fkey FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE CASCADE;


--
-- Name: users users_outlet_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_outlet_id_fkey FOREIGN KEY (outlet_id) REFERENCES public.outlets(id) ON DELETE SET NULL;


--
-- Name: users users_tenant_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_tenant_id_fkey FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE SET NULL;


--
-- PostgreSQL database dump complete
--




--
-- Baseline Demo Records for Franchise System
--

INSERT INTO public.tenants (id, name, code, contact_email, contact_phone, address, subscription_plan, status, max_outlets, currency, currency_symbol)
VALUES (1, 'Main Cafe', 'main-cafe', 'contact@cafe.internal', '+91 9876543210', 'Main Street, Central Branch', 'ENTERPRISE', 'ACTIVE', 10, 'INR', '₹')
ON CONFLICT (id) DO NOTHING;
SELECT setval('public.tenants_id_seq', (SELECT GREATEST(MAX(id), 1) FROM public.tenants));

INSERT INTO public.outlets (id, tenant_id, name, code, address, phone, is_active)
VALUES (1, 1, 'Main Branch', 'MAIN-01', 'Main Street, Central Branch', '+91 9876543210', true)
ON CONFLICT (id) DO NOTHING;
SELECT setval('public.outlets_id_seq', (SELECT GREATEST(MAX(id), 1) FROM public.outlets));

INSERT INTO public.printer_settings (id, host, port, enabled, auto_cut, paper_width_mm, header_title, subheader, footer_message, bill_host, bill_port, bill_enabled, kot_host, kot_port, kot_enabled, juice_kot_host, juice_kot_port, juice_kot_enabled)
VALUES (1, '192.168.1.47', 9100, true, true, 80, 'NERAM', 'Where time tastes better', 'THANK YOU! VISIT AGAIN', '192.168.1.37', 9100, true, '192.168.1.47', 9100, true, '192.168.1.42', 9100, true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO public.gst_settings (id, enabled, gstin, cgst_percent, sgst_percent, round_off_enabled)
VALUES (1, true, '32CQRPS5214H3ZK', 2.50, 2.50, false)
ON CONFLICT (id) DO NOTHING;

