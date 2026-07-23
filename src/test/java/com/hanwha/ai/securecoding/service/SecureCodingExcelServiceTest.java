package com.hanwha.ai.securecoding.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hanwha.ai.securecoding.dto.SecureCodingResultRow;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SecureCodingExcelServiceTest {

    @Test
    void exportsGridRowsAsXlsxWorkbook() throws Exception {
        var row = new SecureCodingResultRow(
                7L, "UserMapper.xml", "MYBATIS_XML", "FINDING", "ERROR",
                "sql-security.mybatis-raw-substitution", "Use parameter binding",
                15, 9, 15, 14);

        byte[] content = new SecureCodingExcelService().export(List.of(row));

        assertThat(content).startsWith((byte) 'P', (byte) 'K');
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            var sheet = workbook.getSheet("Secure Coding");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("파일명");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("UserMapper.xml");
            assertThat(sheet.getRow(1).getCell(5).getStringCellValue())
                    .isEqualTo("sql-security.mybatis-raw-substitution");
        }
    }
}
