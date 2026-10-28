package com.gis.servelq.services;

import com.gis.servelq.dto.TabularReportDTO;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Writes reports to an .xlsx file, one sheet per report (for example one per
 * branch): title, period, headline figures, then each table and the notes.
 */
@Component
public class ReportExcelWriter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public byte[] write(List<TabularReportDTO> reports) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle bold = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            bold.setFont(boldFont);

            CellStyle title = wb.createCellStyle();
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            title.setFont(titleFont);

            CellStyle header = wb.createCellStyle();
            header.cloneStyleFrom(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Set<String> usedNames = new HashSet<>();
            for (TabularReportDTO report : reports) {
                Sheet sheet = wb.createSheet(sheetName(report, usedNames));
                int r = 0;
                int widest = 2;

                cell(sheet, r++, 0, report.getTitle(), title);
                if (report.getBranchName() != null) {
                    cell(sheet, r++, 0, "Branch: " + report.getBranchName(), null);
                }
                if (report.getFrom() != null && report.getTo() != null) {
                    cell(sheet, r++, 0, "Period: " + DATE.format(report.getFrom())
                            + " to " + DATE.format(report.getTo()), null);
                }
                r++;

                if (report.getSummary() != null && !report.getSummary().isEmpty()) {
                    cell(sheet, r++, 0, "Summary", bold);
                    for (TabularReportDTO.Stat stat : report.getSummary()) {
                        Row row = sheet.createRow(r++);
                        row.createCell(0).setCellValue(stat.getLabel());
                        row.createCell(1).setCellValue(stat.getValue());
                    }
                    r++;
                }

                if (report.getSections() != null) {
                    for (TabularReportDTO.Section section : report.getSections()) {
                        cell(sheet, r++, 0, section.getTitle(), bold);
                        List<String> columns = section.getColumns();
                        if (columns != null) {
                            Row row = sheet.createRow(r++);
                            for (int c = 0; c < columns.size(); c++) {
                                Cell cell = row.createCell(c);
                                cell.setCellValue(columns.get(c));
                                cell.setCellStyle(header);
                            }
                            widest = Math.max(widest, columns.size());
                        }
                        List<List<Object>> rows = section.getRows();
                        if (rows == null || rows.isEmpty()) {
                            cell(sheet, r++, 0, "No data", null);
                        } else {
                            for (List<Object> values : rows) {
                                Row row = sheet.createRow(r++);
                                for (int c = 0; c < values.size(); c++) {
                                    setValue(row.createCell(c), values.get(c));
                                }
                                widest = Math.max(widest, values.size());
                            }
                        }
                        r++;
                    }
                }

                if (report.getNotes() != null && !report.getNotes().isEmpty()) {
                    cell(sheet, r++, 0, "Notes", bold);
                    for (String note : report.getNotes()) {
                        cell(sheet, r++, 0, note, null);
                    }
                }

                // First column also holds the summary labels, so it is wider
                sheet.setColumnWidth(0, 40 * 256);
                for (int c = 1; c < widest; c++) {
                    sheet.setColumnWidth(c, 22 * 256);
                }
            }

            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void cell(Sheet sheet, int rowIndex, int col, String value, CellStyle style) {
        Row row = sheet.getRow(rowIndex);
        if (row == null) row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) cell.setCellStyle(style);
    }

    private static void setValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else if (value instanceof Boolean b) {
            cell.setCellValue(b);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    /** Branch name (or title), made safe and unique for Excel's 31-character sheet names. */
    private static String sheetName(TabularReportDTO report, Set<String> used) {
        String base = report.getBranchName() != null ? report.getBranchName() : report.getTitle();
        base = WorkbookUtil.createSafeSheetName(base == null || base.isBlank() ? "Report" : base);
        if (base.length() > 28) base = base.substring(0, 28);
        String name = base;
        for (int i = 2; used.contains(name.toLowerCase()); i++) {
            name = base + " " + i;
        }
        used.add(name.toLowerCase());
        return name;
    }
}
