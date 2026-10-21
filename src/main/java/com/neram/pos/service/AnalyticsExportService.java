package com.neram.pos.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.neram.pos.dto.ComprehensiveAnalyticsDTO;
import com.neram.pos.dto.ComprehensiveAnalyticsDTO.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsExportService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
    private static final DecimalFormat CURRENCY_FORMAT = new DecimalFormat("₹#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.0'%'");

    /**
     * Exports Comprehensive Analytics to a richly formatted Excel (.xlsx) workbook.
     */
    public byte[] exportToExcel(ComprehensiveAnalyticsDTO data) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            
            // Reusable Styles
            XSSFCellStyle headerStyle = createHeaderStyle(workbook, new java.awt.Color(30, 41, 59)); // Slate-800
            XSSFCellStyle subHeaderStyle = createHeaderStyle(workbook, new java.awt.Color(51, 65, 85)); // Slate-700
            XSSFCellStyle kpiHeaderStyle = createHeaderStyle(workbook, new java.awt.Color(217, 119, 6)); // Amber-600
            
            XSSFCellStyle titleStyle = workbook.createCellStyle();
            XSSFFont titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setColor(new XSSFColor(new java.awt.Color(245, 158, 11), new DefaultIndexedColorMap()));
            titleStyle.setFont(titleFont);

            XSSFCellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("₹#,##0.00"));
            currencyStyle.setAlignment(HorizontalAlignment.RIGHT);

            XSSFCellStyle percentStyle = workbook.createCellStyle();
            percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.0%"));
            percentStyle.setAlignment(HorizontalAlignment.RIGHT);

            XSSFCellStyle intStyle = workbook.createCellStyle();
            intStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0"));
            intStyle.setAlignment(HorizontalAlignment.RIGHT);

            XSSFCellStyle boldTotalStyle = workbook.createCellStyle();
            XSSFFont boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldTotalStyle.setFont(boldFont);
            boldTotalStyle.setBorderTop(BorderStyle.THIN);
            boldTotalStyle.setBorderBottom(BorderStyle.DOUBLE);

            XSSFCellStyle boldTotalCurrencyStyle = workbook.createCellStyle();
            boldTotalCurrencyStyle.cloneStyleFrom(boldTotalStyle);
            boldTotalCurrencyStyle.setDataFormat(workbook.createDataFormat().getFormat("₹#,##0.00"));
            boldTotalCurrencyStyle.setAlignment(HorizontalAlignment.RIGHT);

            // ==========================================
            // SHEET 1: Executive Summary & Financials
            // ==========================================
            XSSFSheet summarySheet = workbook.createSheet("Executive Summary");
            summarySheet.setDisplayGridlines(true);
            int rowIdx = 0;

            // Title
            Row titleRow = summarySheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("NERAM POS - Business Analytics & Financial Report");
            titleCell.setCellStyle(titleStyle);

            // Period & Timestamp
            String periodLabel = data.getPeriodInfo() != null ? data.getPeriodInfo().getPeriod() : "ALL";
            String dateRange = (data.getPeriodInfo() != null && data.getPeriodInfo().getStartDate() != null)
                    ? data.getPeriodInfo().getStartDate() + " to " + data.getPeriodInfo().getEndDate()
                    : "Full History";
            Row metaRow1 = summarySheet.createRow(rowIdx++);
            metaRow1.createCell(0).setCellValue("Report Period: " + periodLabel + " (" + dateRange + ")");
            Row metaRow2 = summarySheet.createRow(rowIdx++);
            metaRow2.createCell(0).setCellValue("Generated At: " + LocalDateTime.now().format(DATE_TIME_FORMATTER));
            rowIdx++; // blank line

            // KPI Summary Table
            Row kpiHeadRow = summarySheet.createRow(rowIdx++);
            kpiHeadRow.createCell(0).setCellValue("Key Performance Indicator (KPI)");
            kpiHeadRow.createCell(1).setCellValue("Recorded Value");
            kpiHeadRow.createCell(2).setCellValue("Operational Context");
            for (int i = 0; i < 3; i++) kpiHeadRow.getCell(i).setCellStyle(kpiHeaderStyle);

            KpiSummary kpi = data.getKpis();
            if (kpi != null) {
                addKpiRow(summarySheet, rowIdx++, "Total Sales / Revenue", kpi.getTotalSales(), currencyStyle, "Gross sales before expenses");
                addKpiRow(summarySheet, rowIdx++, "Total Orders Placed", kpi.getTotalOrders() != null ? kpi.getTotalOrders().doubleValue() : 0.0, intStyle, "Total completed orders");
                addKpiRow(summarySheet, rowIdx++, "Average Order Value (AOV)", kpi.getAvgOrderValue(), currencyStyle, "Average ticket size");
                addKpiRow(summarySheet, rowIdx++, "Total Outgoings", kpi.getTotalExpenses(), currencyStyle, "OpEx + Purchases + Payroll");
                addKpiRow(summarySheet, rowIdx++, "Operating Expenses (OpEx)", kpi.getTotalOperatingExpenses(), currencyStyle, "Direct cafe utilities & expenses");
                addKpiRow(summarySheet, rowIdx++, "Supplier Purchases", kpi.getTotalSupplierPurchases(), currencyStyle, "Ingredients and inventory procured");
                addKpiRow(summarySheet, rowIdx++, "Salaries Paid", kpi.getTotalSalariesPaid(), currencyStyle, "Payroll disbursements");
                addKpiRow(summarySheet, rowIdx++, "Gross Profit (Recorded)", kpi.getGrossProfit(), currencyStyle, "Sales minus Purchases/COGS");
                addKpiRow(summarySheet, rowIdx++, "Net Profit (Recorded)", kpi.getNetProfit(), currencyStyle, "Sales minus all recorded outgoings");
                addKpiRow(summarySheet, rowIdx++, "Inventory Asset Valuation", kpi.getInventoryValuation(), currencyStyle, "Current stock holding value");
                addKpiRow(summarySheet, rowIdx++, "Low / Out of Stock Items", (kpi.getLowStockCount() != null ? kpi.getLowStockCount().doubleValue() : 0.0), intStyle, "Items at or below reorder threshold");
            }
            rowIdx += 2;

            // Expense Breakdown Table
            Row expHeadRow = summarySheet.createRow(rowIdx++);
            expHeadRow.createCell(0).setCellValue("Expense Category");
            expHeadRow.createCell(1).setCellValue("Amount");
            expHeadRow.createCell(2).setCellValue("% of Total Outgoings");
            for (int i = 0; i < 3; i++) expHeadRow.getCell(i).setCellStyle(headerStyle);

            if (data.getExpenseBreakdown() != null && !data.getExpenseBreakdown().isEmpty()) {
                double totalExpSum = 0;
                for (CategoryBreakdown cb : data.getExpenseBreakdown()) {
                    Row r = summarySheet.createRow(rowIdx++);
                    r.createCell(0).setCellValue(cb.getCategory());
                    Cell cAmt = r.createCell(1);
                    cAmt.setCellValue(cb.getAmount() != null ? cb.getAmount() : 0.0);
                    cAmt.setCellStyle(currencyStyle);
                    Cell cPct = r.createCell(2);
                    cPct.setCellValue(cb.getPercentage() != null ? cb.getPercentage() / 100.0 : 0.0);
                    cPct.setCellStyle(percentStyle);
                    totalExpSum += (cb.getAmount() != null ? cb.getAmount() : 0.0);
                }
                Row totRow = summarySheet.createRow(rowIdx++);
                totRow.createCell(0).setCellValue("Total Categorized Expenses");
                totRow.getCell(0).setCellStyle(boldTotalStyle);
                Cell cTot = totRow.createCell(1);
                cTot.setCellValue(totalExpSum);
                cTot.setCellStyle(boldTotalCurrencyStyle);
                Cell cTotPct = totRow.createCell(2);
                cTotPct.setCellValue(1.0);
                cTotPct.setCellStyle(boldTotalStyle);
            } else {
                Row r = summarySheet.createRow(rowIdx++);
                r.createCell(0).setCellValue("No expense records in selected period");
            }

            // ==========================================
            // SHEET 2: Sales Trend & Top Dishes
            // ==========================================
            XSSFSheet salesSheet = workbook.createSheet("Sales & Dish Performance");
            salesSheet.setDisplayGridlines(true);
            int sRow = 0;

            Row sTitle = salesSheet.createRow(sRow++);
            sTitle.createCell(0).setCellValue("Sales Trend & Top Selling Menu Items");
            sTitle.getCell(0).setCellStyle(titleStyle);
            sRow++;

            // Sales Trend
            Row stHead = salesSheet.createRow(sRow++);
            stHead.createCell(0).setCellValue("Date / Period");
            stHead.createCell(1).setCellValue("Orders");
            stHead.createCell(2).setCellValue("Total Revenue");
            stHead.createCell(3).setCellValue("Avg Order Value");
            for (int i = 0; i < 4; i++) stHead.getCell(i).setCellStyle(headerStyle);

            if (data.getSalesTrend() != null && !data.getSalesTrend().isEmpty()) {
                for (TimePoint tp : data.getSalesTrend()) {
                    Row r = salesSheet.createRow(sRow++);
                    r.createCell(0).setCellValue(tp.getLabel());
                    Cell cOrd = r.createCell(1);
                    cOrd.setCellValue(tp.getOrders() != null ? tp.getOrders() : 0);
                    cOrd.setCellStyle(intStyle);
                    Cell cSal = r.createCell(2);
                    cSal.setCellValue(tp.getSales() != null ? tp.getSales() : 0.0);
                    cSal.setCellStyle(currencyStyle);
                    Cell cAvg = r.createCell(3);
                    cAvg.setCellValue(tp.getAvgOrder() != null ? tp.getAvgOrder() : 0.0);
                    cAvg.setCellStyle(currencyStyle);
                }
            } else {
                Row r = salesSheet.createRow(sRow++);
                r.createCell(0).setCellValue("No sales recorded in selected period");
            }
            sRow += 2;

            // Top Dishes Leaderboard
            Row dishHead = salesSheet.createRow(sRow++);
            dishHead.createCell(0).setCellValue("Rank");
            dishHead.createCell(1).setCellValue("Menu Item Dish");
            dishHead.createCell(2).setCellValue("Quantity Sold");
            dishHead.createCell(3).setCellValue("Revenue Generated");
            dishHead.createCell(4).setCellValue("% Share of Sales");
            for (int i = 0; i < 5; i++) dishHead.getCell(i).setCellStyle(subHeaderStyle);

            if (data.getTopSellingDishes() != null && !data.getTopSellingDishes().isEmpty()) {
                int rank = 1;
                for (DishPerformance dp : data.getTopSellingDishes()) {
                    Row r = salesSheet.createRow(sRow++);
                    Cell cRank = r.createCell(0);
                    cRank.setCellValue(rank++);
                    cRank.setCellStyle(intStyle);
                    r.createCell(1).setCellValue(dp.getDishName());
                    Cell cQty = r.createCell(2);
                    cQty.setCellValue(dp.getQuantity() != null ? dp.getQuantity() : 0);
                    cQty.setCellStyle(intStyle);
                    Cell cRev = r.createCell(3);
                    cRev.setCellValue(dp.getRevenue() != null ? dp.getRevenue() : 0.0);
                    cRev.setCellStyle(currencyStyle);
                    Cell cPct = r.createCell(4);
                    cPct.setCellValue(dp.getPercentageOfSales() != null ? dp.getPercentageOfSales() / 100.0 : 0.0);
                    cPct.setCellStyle(percentStyle);
                }
            } else {
                Row r = salesSheet.createRow(sRow++);
                r.createCell(0).setCellValue("No dish sales in selected period");
            }

            // ==========================================
            // SHEET 3: Procurement & Inventory
            // ==========================================
            XSSFSheet purchaseSheet = workbook.createSheet("Procurement & Inventory");
            purchaseSheet.setDisplayGridlines(true);
            int pRow = 0;

            Row pTitle = purchaseSheet.createRow(pRow++);
            pTitle.createCell(0).setCellValue("Supplier Purchasing & Inventory Health");
            pTitle.getCell(0).setCellStyle(titleStyle);
            pRow++;

            // Supplier Spend
            Row suppHead = purchaseSheet.createRow(pRow++);
            suppHead.createCell(0).setCellValue("Supplier Name");
            suppHead.createCell(1).setCellValue("Invoices Count");
            suppHead.createCell(2).setCellValue("Total Spend");
            suppHead.createCell(3).setCellValue("% of Purchases");
            for (int i = 0; i < 4; i++) suppHead.getCell(i).setCellStyle(headerStyle);

            if (data.getSupplierSpending() != null && !data.getSupplierSpending().isEmpty()) {
                for (SupplierSpend ss : data.getSupplierSpending()) {
                    Row r = purchaseSheet.createRow(pRow++);
                    r.createCell(0).setCellValue(ss.getSupplierName());
                    Cell cInv = r.createCell(1);
                    cInv.setCellValue(ss.getInvoiceCount() != null ? ss.getInvoiceCount() : 0);
                    cInv.setCellStyle(intStyle);
                    Cell cSpend = r.createCell(2);
                    cSpend.setCellValue(ss.getTotalSpend() != null ? ss.getTotalSpend() : 0.0);
                    cSpend.setCellStyle(currencyStyle);
                    Cell cPct = r.createCell(3);
                    cPct.setCellValue(ss.getPercentage() != null ? ss.getPercentage() / 100.0 : 0.0);
                    cPct.setCellStyle(percentStyle);
                }
            } else {
                Row r = purchaseSheet.createRow(pRow++);
                r.createCell(0).setCellValue("No supplier spending recorded in selected period");
            }
            pRow += 2;

            // Top Purchased Products / Ingredients
            Row ingHead = purchaseSheet.createRow(pRow++);
            ingHead.createCell(0).setCellValue("Purchased Product / Ingredient");
            ingHead.createCell(1).setCellValue("Quantity");
            ingHead.createCell(2).setCellValue("Unit");
            ingHead.createCell(3).setCellValue("Avg Unit Rate");
            ingHead.createCell(4).setCellValue("Total Spend");
            for (int i = 0; i < 5; i++) ingHead.getCell(i).setCellStyle(subHeaderStyle);

            if (data.getTopPurchasedProducts() != null && !data.getTopPurchasedProducts().isEmpty()) {
                for (PurchasedProductCost ppc : data.getTopPurchasedProducts()) {
                    Row r = purchaseSheet.createRow(pRow++);
                    r.createCell(0).setCellValue(ppc.getProductName());
                    Cell cQty = r.createCell(1);
                    cQty.setCellValue(ppc.getTotalQuantity() != null ? ppc.getTotalQuantity() : 0.0);
                    cQty.setCellStyle(currencyStyle);
                    r.createCell(2).setCellValue(ppc.getUnit() != null ? ppc.getUnit() : "-");
                    Cell cRate = r.createCell(3);
                    cRate.setCellValue(ppc.getAvgUnitRate() != null ? ppc.getAvgUnitRate() : 0.0);
                    cRate.setCellStyle(currencyStyle);
                    Cell cSpend = r.createCell(4);
                    cSpend.setCellValue(ppc.getTotalSpend() != null ? ppc.getTotalSpend() : 0.0);
                    cSpend.setCellStyle(currencyStyle);
                }
            } else {
                Row r = purchaseSheet.createRow(pRow++);
                r.createCell(0).setCellValue("No product invoice items found");
            }

            // ==========================================
            // SHEET 4: Drill-Down Source Records (Audit)
            // ==========================================
            XSSFSheet auditSheet = workbook.createSheet("Audit Source Records");
            auditSheet.setDisplayGridlines(true);
            int aRow = 0;

            Row aTitle = auditSheet.createRow(aRow++);
            aTitle.createCell(0).setCellValue("Traceable Source Records (Every Penny Audit)");
            aTitle.getCell(0).setCellStyle(titleStyle);
            aRow++;

            SourceRecords drill = data.getDrillDown();
            if (drill != null) {
                // Section 1: Sales Bills
                Row billTitle = auditSheet.createRow(aRow++);
                billTitle.createCell(0).setCellValue("1. Sales Bills (" + (drill.getSales() != null ? drill.getSales().size() : 0) + " records)");
                billTitle.getCell(0).setCellStyle(subHeaderStyle);

                Row bHead = auditSheet.createRow(aRow++);
                bHead.createCell(0).setCellValue("Bill Number");
                bHead.createCell(1).setCellValue("Date & Time");
                bHead.createCell(2).setCellValue("Table");
                bHead.createCell(3).setCellValue("Customer");
                bHead.createCell(4).setCellValue("Payment Mode");
                bHead.createCell(5).setCellValue("Net Amount");
                for (int i = 0; i < 6; i++) bHead.getCell(i).setCellStyle(headerStyle);

                if (drill.getSales() != null && !drill.getSales().isEmpty()) {
                    for (BillRecord br : drill.getSales()) {
                        Row r = auditSheet.createRow(aRow++);
                        r.createCell(0).setCellValue(br.getBillNumber());
                        r.createCell(1).setCellValue(br.getCreatedAt());
                        r.createCell(2).setCellValue(br.getTableNumber() != null ? br.getTableNumber() : "-");
                        r.createCell(3).setCellValue(br.getCustomerName() != null ? br.getCustomerName() : "Walk-in");
                        r.createCell(4).setCellValue(br.getPaymentMode() != null ? br.getPaymentMode() : "-");
                        Cell cAmt = r.createCell(5);
                        cAmt.setCellValue(br.getNetAmount() != null ? br.getNetAmount() : 0.0);
                        cAmt.setCellStyle(currencyStyle);
                    }
                }
                aRow += 2;

                // Section 2: Operating Expenses
                Row expTitle = auditSheet.createRow(aRow++);
                expTitle.createCell(0).setCellValue("2. Operating Expenses (" + (drill.getExpenses() != null ? drill.getExpenses().size() : 0) + " records)");
                expTitle.getCell(0).setCellStyle(subHeaderStyle);

                Row eHead = auditSheet.createRow(aRow++);
                eHead.createCell(0).setCellValue("Category");
                eHead.createCell(1).setCellValue("Description");
                eHead.createCell(2).setCellValue("Date");
                eHead.createCell(3).setCellValue("Paid By");
                eHead.createCell(4).setCellValue("Receipt / Ref");
                eHead.createCell(5).setCellValue("Amount");
                for (int i = 0; i < 6; i++) eHead.getCell(i).setCellStyle(headerStyle);

                if (drill.getExpenses() != null && !drill.getExpenses().isEmpty()) {
                    for (ExpenseRecord er : drill.getExpenses()) {
                        Row r = auditSheet.createRow(aRow++);
                        r.createCell(0).setCellValue(er.getType() != null ? er.getType() : "GENERAL");
                        r.createCell(1).setCellValue(er.getDescription() != null ? er.getDescription() : "-");
                        r.createCell(2).setCellValue(er.getExpenseDate() != null ? er.getExpenseDate() : "-");
                        r.createCell(3).setCellValue(er.getPaidBy() != null ? er.getPaidBy() : "-");
                        r.createCell(4).setCellValue(er.getReceiptRef() != null ? er.getReceiptRef() : "-");
                        Cell cAmt = r.createCell(5);
                        cAmt.setCellValue(er.getAmount() != null ? er.getAmount() : 0.0);
                        cAmt.setCellStyle(currencyStyle);
                    }
                }
                aRow += 2;

                // Section 3: Supplier Invoices
                Row invTitle = auditSheet.createRow(aRow++);
                invTitle.createCell(0).setCellValue("3. Supplier Invoices (" + (drill.getPurchases() != null ? drill.getPurchases().size() : 0) + " records)");
                invTitle.getCell(0).setCellStyle(subHeaderStyle);

                Row iHead = auditSheet.createRow(aRow++);
                iHead.createCell(0).setCellValue("Invoice #");
                iHead.createCell(1).setCellValue("Supplier");
                iHead.createCell(2).setCellValue("Date");
                iHead.createCell(3).setCellValue("Subtotal");
                iHead.createCell(4).setCellValue("GST");
                iHead.createCell(5).setCellValue("Total");
                iHead.createCell(6).setCellValue("Status");
                for (int i = 0; i < 7; i++) iHead.getCell(i).setCellStyle(headerStyle);

                if (drill.getPurchases() != null && !drill.getPurchases().isEmpty()) {
                    for (SupplierInvoiceRecord sir : drill.getPurchases()) {
                        Row r = auditSheet.createRow(aRow++);
                        r.createCell(0).setCellValue(sir.getInvoiceNumber());
                        r.createCell(1).setCellValue(sir.getSupplierName());
                        r.createCell(2).setCellValue(sir.getInvoiceDate() != null ? sir.getInvoiceDate() : "-");
                        Cell cSub = r.createCell(3);
                        cSub.setCellValue(sir.getSubtotal() != null ? sir.getSubtotal() : 0.0);
                        cSub.setCellStyle(currencyStyle);
                        Cell cGst = r.createCell(4);
                        cGst.setCellValue(sir.getGstAmount() != null ? sir.getGstAmount() : 0.0);
                        cGst.setCellStyle(currencyStyle);
                        Cell cTot = r.createCell(5);
                        cTot.setCellValue(sir.getTotalAmount() != null ? sir.getTotalAmount() : 0.0);
                        cTot.setCellStyle(currencyStyle);
                        r.createCell(6).setCellValue(sir.getStatus() != null ? sir.getStatus() : "-");
                    }
                }
            }

            // Auto-size columns across all sheets
            for (int s = 0; s < workbook.getNumberOfSheets(); s++) {
                XSSFSheet sheet = workbook.getSheetAt(s);
                for (int c = 0; c < 8; c++) {
                    sheet.autoSizeColumn(c);
                    // Add small padding
                    sheet.setColumnWidth(c, Math.max(sheet.getColumnWidth(c) + 1200, 3500));
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Failed to generate Excel analytics export: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate Excel export", e);
        }
    }

    /**
     * Exports Comprehensive Analytics to an executive-ready PDF report.
     */
    public byte[] exportToPdf(ComprehensiveAnalyticsDTO data) {
        Document document = new Document(PageSize.A4, 36, 36, 40, 40);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setPageEvent(new PdfFooterEvent());

            document.open();

            // Document Header Banner
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{65f, 35f});

            PdfPCell leftHeader = new PdfPCell();
            leftHeader.setBorder(Rectangle.NO_BORDER);
            Paragraph brandP = new Paragraph("NERAM POS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(217, 119, 6)));
            Paragraph subP = new Paragraph("Business Analytics & Financial Report", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, new Color(30, 41, 59)));
            leftHeader.addElement(brandP);
            leftHeader.addElement(subP);
            headerTable.addCell(leftHeader);

            PdfPCell rightHeader = new PdfPCell();
            rightHeader.setBorder(Rectangle.NO_BORDER);
            rightHeader.setHorizontalAlignment(Element.ALIGN_RIGHT);
            String periodStr = data.getPeriodInfo() != null ? data.getPeriodInfo().getPeriod() : "ALL";
            Paragraph pLabel = new Paragraph("Period: " + periodStr, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK));
            pLabel.setAlignment(Element.ALIGN_RIGHT);
            String datesStr = (data.getPeriodInfo() != null && data.getPeriodInfo().getStartDate() != null)
                    ? data.getPeriodInfo().getStartDate() + " to " + data.getPeriodInfo().getEndDate()
                    : "Full History";
            Paragraph dLabel = new Paragraph(datesStr, FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
            dLabel.setAlignment(Element.ALIGN_RIGHT);
            Paragraph secLabel = new Paragraph("CONFIDENTIAL • ADMIN / MANAGER ONLY", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, new Color(185, 28, 28)));
            secLabel.setAlignment(Element.ALIGN_RIGHT);
            rightHeader.addElement(pLabel);
            rightHeader.addElement(dLabel);
            rightHeader.addElement(secLabel);
            headerTable.addCell(rightHeader);

            document.add(headerTable);
            document.add(new Paragraph(" ")); // Spacer

            // Section 1: Executive KPI Cards (4x2 Grid)
            document.add(createSectionTitle("1. Executive Performance Summary"));
            PdfPTable kpiTable = new PdfPTable(4);
            kpiTable.setWidthPercentage(100);
            kpiTable.setSpacingBefore(6f);
            kpiTable.setSpacingAfter(12f);

            KpiSummary k = data.getKpis();
            if (k != null) {
                addPdfKpiCard(kpiTable, "Total Sales", formatCurrency(k.getTotalSales()), "Gross Revenue", new Color(16, 185, 129));
                addPdfKpiCard(kpiTable, "Total Outgoings", formatCurrency(k.getTotalExpenses()), "OpEx + Purchases + Payroll", new Color(239, 68, 68));
                addPdfKpiCard(kpiTable, "Gross Profit", formatCurrency(k.getGrossProfit()), "Sales − Purchases", new Color(245, 158, 11));
                addPdfKpiCard(kpiTable, "Net Profit", formatCurrency(k.getNetProfit()), "Recorded Margin", new Color(59, 130, 246));

                addPdfKpiCard(kpiTable, "Total Orders", String.valueOf(k.getTotalOrders() != null ? k.getTotalOrders() : 0), "Volume", new Color(99, 102, 241));
                addPdfKpiCard(kpiTable, "Average Ticket", formatCurrency(k.getAvgOrderValue()), "Per Order", new Color(139, 92, 246));
                addPdfKpiCard(kpiTable, "Inventory Value", formatCurrency(k.getInventoryValuation()), "Asset Holding", new Color(20, 184, 166));
                addPdfKpiCard(kpiTable, "Low Stock Alerts", String.valueOf(k.getLowStockCount() != null ? k.getLowStockCount() : 0), "Items to Restock", new Color(244, 63, 94));
            }
            document.add(kpiTable);

            // Section 2: P&L Operating Statement
            document.add(createSectionTitle("2. Operating Profit & Loss Breakdown"));
            PdfPTable pnlTable = new PdfPTable(3);
            pnlTable.setWidthPercentage(100);
            pnlTable.setWidths(new float[]{45f, 25f, 30f});
            pnlTable.setSpacingBefore(6f);
            pnlTable.setSpacingAfter(12f);

            addPdfTableHeader(pnlTable, new String[]{"Accounting Measure", "Recorded Amount", "Notes & Proportions"});
            if (k != null) {
                addPdfTableRow(pnlTable, "1. Net Sales / Revenue", formatCurrency(k.getTotalSales()), "Total customer billings");
                addPdfTableRow(pnlTable, "2. Supplier Purchases (COGS)", formatCurrency(k.getTotalSupplierPurchases()), "Ingredient & inventory costs");
                addPdfTableRow(pnlTable, "3. Gross Profit", formatCurrency(k.getGrossProfit()), "Sales minus direct product procurement");
                addPdfTableRow(pnlTable, "4. Operating Overheads (OpEx)", formatCurrency(k.getTotalOperatingExpenses()), "Utilities, rent, maintenance, consumables");
                addPdfTableRow(pnlTable, "5. Payroll Accrual (Salaries)", formatCurrency(k.getTotalSalariesPaid()), "Disbursed staff salaries");
                addPdfTableRow(pnlTable, "6. Net Operating Profit", formatCurrency(k.getNetProfit()), "Gross Profit − OpEx − Payroll");
            }
            document.add(pnlTable);

            // Section 3: Top Selling Dishes
            document.add(createSectionTitle("3. Top Selling Menu Items (Ranked by Sales)"));
            PdfPTable dishTable = new PdfPTable(5);
            dishTable.setWidthPercentage(100);
            dishTable.setWidths(new float[]{10f, 40f, 15f, 20f, 15f});
            dishTable.setSpacingBefore(6f);
            dishTable.setSpacingAfter(12f);

            addPdfTableHeader(dishTable, new String[]{"Rank", "Dish Name", "Quantity", "Revenue", "Share %"});
            if (data.getTopSellingDishes() != null && !data.getTopSellingDishes().isEmpty()) {
                int rk = 1;
                for (DishPerformance dp : data.getTopSellingDishes()) {
                    addPdfTableRow(dishTable,
                            String.valueOf(rk++),
                            dp.getDishName(),
                            String.valueOf(dp.getQuantity() != null ? dp.getQuantity() : 0),
                            formatCurrency(dp.getRevenue()),
                            formatPercent(dp.getPercentageOfSales()));
                    if (rk > 10) break; // Limit top 10 on PDF for clean layout
                }
            } else {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No dish transactions in selected period", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY)));
                emptyCell.setColspan(5);
                emptyCell.setPadding(6f);
                dishTable.addCell(emptyCell);
            }
            document.add(dishTable);

            // Section 4: Expense Categories Distribution
            document.add(createSectionTitle("4. Expense Distribution by Category"));
            PdfPTable expTable = new PdfPTable(3);
            expTable.setWidthPercentage(100);
            expTable.setWidths(new float[]{50f, 25f, 25f});
            expTable.setSpacingBefore(6f);
            expTable.setSpacingAfter(12f);

            addPdfTableHeader(expTable, new String[]{"Expense Category", "Recorded Spend", "Percentage"});
            if (data.getExpenseBreakdown() != null && !data.getExpenseBreakdown().isEmpty()) {
                for (CategoryBreakdown cb : data.getExpenseBreakdown()) {
                    addPdfTableRow(expTable,
                            cb.getCategory(),
                            formatCurrency(cb.getAmount()),
                            formatPercent(cb.getPercentage()));
                }
            } else {
                PdfPCell emptyCell = new PdfPCell(new Phrase("No expenses in selected period", FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.GRAY)));
                emptyCell.setColspan(3);
                emptyCell.setPadding(6f);
                expTable.addCell(emptyCell);
            }
            document.add(expTable);

            // Section 5: Audit Traceability Summary
            document.add(createSectionTitle("5. Audit & Source Records Traceability"));
            PdfPTable auditTable = new PdfPTable(3);
            auditTable.setWidthPercentage(100);
            auditTable.setWidths(new float[]{40f, 25f, 35f});
            auditTable.setSpacingBefore(6f);
            auditTable.setSpacingAfter(8f);

            addPdfTableHeader(auditTable, new String[]{"Transaction Type", "Count of Records", "Integrity Check"});
            SourceRecords drill = data.getDrillDown();
            int salesCount = (drill != null && drill.getSales() != null) ? drill.getSales().size() : 0;
            int expCount = (drill != null && drill.getExpenses() != null) ? drill.getExpenses().size() : 0;
            int purCount = (drill != null && drill.getPurchases() != null) ? drill.getPurchases().size() : 0;
            int salCount = (drill != null && drill.getSalaries() != null) ? drill.getSalaries().size() : 0;

            addPdfTableRow(auditTable, "Customer Sales Bills", salesCount + " Bills", "Authoritative bills ledger");
            addPdfTableRow(auditTable, "Operating Expenses", expCount + " Entries", "Operational receipts & vouchers");
            addPdfTableRow(auditTable, "Supplier Procurement Invoices", purCount + " Invoices", "Verified vendor bills");
            addPdfTableRow(auditTable, "Staff Salary Payments", salCount + " Disbursements", "Monthly payroll ledger");
            document.add(auditTable);

            document.close();
            return out.toByteArray();
        } catch (DocumentException | IOException e) {
            log.error("Failed to generate PDF analytics export: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF export", e);
        }
    }

    // ──────────────── Helper Methods for POI Excel ────────────────

    private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb, java.awt.Color bgColor) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(new XSSFColor(bgColor, new DefaultIndexedColorMap()));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_40_PERCENT.getIndex());
        return style;
    }

    private void addKpiRow(XSSFSheet sheet, int rowIdx, String label, Double value, CellStyle style, String notes) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        Cell cVal = row.createCell(1);
        if (value != null) {
            cVal.setCellValue(value);
        } else {
            cVal.setCellValue(0.0);
        }
        cVal.setCellStyle(style);
        row.createCell(2).setCellValue(notes);
    }

    // ──────────────── Helper Methods for OpenPDF ────────────────

    private Paragraph createSectionTitle(String title) {
        Paragraph p = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(30, 41, 59)));
        p.setSpacingBefore(8f);
        p.setSpacingAfter(4f);
        return p;
    }

    private void addPdfKpiCard(PdfPTable table, String title, String value, String subtitle, Color accentColor) {
        PdfPCell cell = new PdfPCell();
        cell.setPadding(8f);
        cell.setBackgroundColor(new Color(248, 250, 252));
        cell.setBorderColor(new Color(226, 232, 240));
        cell.setBorderWidth(1f);

        Paragraph titleP = new Paragraph(title, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(100, 116, 139)));
        Paragraph valP = new Paragraph(value, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, accentColor));
        Paragraph subP = new Paragraph(subtitle, FontFactory.getFont(FontFactory.HELVETICA, 7, new Color(148, 163, 184)));

        cell.addElement(titleP);
        cell.addElement(valP);
        cell.addElement(subP);
        table.addCell(cell);
    }

    private void addPdfTableHeader(PdfPTable table, String[] headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            cell.setBackgroundColor(new Color(30, 41, 59));
            cell.setPadding(6f);
            cell.setBorder(Rectangle.NO_BORDER);
            table.addCell(cell);
        }
    }

    private void addPdfTableRow(PdfPTable table, String... values) {
        for (String v : values) {
            PdfPCell cell = new PdfPCell(new Phrase(v != null ? v : "-", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY)));
            cell.setPadding(5f);
            cell.setBorderColor(new Color(226, 232, 240));
            table.addCell(cell);
        }
    }

    private String formatCurrency(Double val) {
        return CURRENCY_FORMAT.format(val != null ? val : 0.0);
    }

    private String formatPercent(Double val) {
        return PERCENT_FORMAT.format(val != null ? val : 0.0);
    }

    /**
     * Page event listener for adding Header and Footer to OpenPDF documents.
     */
    private static class PdfFooterEvent extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Phrase footer = new Phrase(
                    "Page " + writer.getPageNumber() + " • NERAM POS Business Analytics • Generated on " + LocalDateTime.now().format(DATE_TIME_FORMATTER),
                    FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    footer,
                    (document.right() - document.left()) / 2 + document.leftMargin(),
                    document.bottom() - 15, 0);
        }
    }
}
