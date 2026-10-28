package com.neram.pos.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportCsvEscapeTest {

    @Test
    void plainCellsAreLeftAlone() {
        assertEquals("Espresso", ReportGenerationService.escapeCsv("Espresso"));
        assertEquals("\"\"", ReportGenerationService.escapeCsv(null));
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"Tea, hot\"", ReportGenerationService.escapeCsv("Tea, hot"));
        assertEquals("\"say \"\"hi\"\"\"", ReportGenerationService.escapeCsv("say \"hi\""));
    }

    @Test
    void numbersIncludingNegativeOnesStayNumbers() {
        assertEquals("-120.50", ReportGenerationService.escapeCsv("-120.50"));
        assertEquals("+15", ReportGenerationService.escapeCsv("+15"));
        assertEquals("1200.00", ReportGenerationService.escapeCsv("1200.00"));
    }

    @Test
    void textThatLooksLikeAFormulaIsDefused() {
        assertEquals("'=HYPERLINK(\"http://x\")".replace("\"", "\"\""), ReportGenerationService.escapeCsv("=HYPERLINK(\"http://x\")").replaceAll("^\"|\"$", ""));
        assertEquals("'@SUM(A1)", ReportGenerationService.escapeCsv("@SUM(A1)"));
        assertEquals("'-cmd|' /C calc'!A0", ReportGenerationService.escapeCsv("-cmd|' /C calc'!A0"));
        assertEquals("'+1+1", ReportGenerationService.escapeCsv("+1+1"));
    }
}
