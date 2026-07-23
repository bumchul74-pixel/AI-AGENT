package com.hanwha.ai.securecoding.service;

import com.hanwha.ai.global.exception.BusinessException;
import com.hanwha.ai.securecoding.dto.SecureCodingResultRow;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class SecureCodingExcelService {
    private static final List<String> HEADERS = List.of(
            "문서 ID", "파일명", "파일 유형", "상태", "심각도", "규칙",
            "메시지", "시작 행", "시작 열", "종료 행", "종료 열");

    public byte[] export(List<SecureCodingResultRow> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new BusinessException("There are no secure coding results to export.");
        }
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Secure Coding");
            CellStyle headerStyle = headerStyle(workbook);
            Row header = sheet.createRow(0);
            for (int column = 0; column < HEADERS.size(); column++) {
                header.createCell(column).setCellValue(HEADERS.get(column));
                header.getCell(column).setCellStyle(headerStyle);
            }
            for (int index = 0; index < rows.size(); index++) writeRow(sheet.createRow(index + 1), rows.get(index));
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, rows.size(), 0, HEADERS.size() - 1));
            for (int column = 0; column < HEADERS.size(); column++) {
                sheet.autoSizeColumn(column);
                sheet.setColumnWidth(column, Math.min(sheet.getColumnWidth(column) + 768, 18000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("Failed to create the secure coding Excel file.", exception);
        }
    }

    private CellStyle headerStyle(Workbook workbook) {
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        CellStyle style = workbook.createCellStyle();
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private void writeRow(Row target, SecureCodingResultRow source) {
        set(target, 0, source.documentId());
        set(target, 1, source.fileName());
        set(target, 2, source.fileType());
        set(target, 3, source.status());
        set(target, 4, source.severity());
        set(target, 5, source.ruleId());
        set(target, 6, source.message());
        set(target, 7, source.startLine());
        set(target, 8, source.startColumn());
        set(target, 9, source.endLine());
        set(target, 10, source.endColumn());
    }

    private void set(Row row, int column, Object value) {
        if (value instanceof Number number) row.createCell(column).setCellValue(number.doubleValue());
        else row.createCell(column).setCellValue(value == null ? "" : String.valueOf(value));
    }
}
