package com.oshmarket.service;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.oshmarket.entity.Payment;
import com.oshmarket.entity.RentContract;
import com.oshmarket.exception.ApiException;
import com.oshmarket.repository.PaymentRepository;
import com.oshmarket.repository.RentContractRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final RentContractRepository contractRepository;
    private final PaymentRepository paymentRepository;

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    public String generateFilename(String type) {
        return "report_" + LocalDateTime.now().format(DT_FMT) + "." + type;
    }

    public byte[] generateExcel() {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet tenantsSheet = workbook.createSheet("Арендаторы");
            writeTenantsSheet(workbook, tenantsSheet);

            Sheet paymentsSheet = workbook.createSheet("Платежи");
            writePaymentsSheet(workbook, paymentsSheet);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка генерации Excel: " + e.getMessage(), e);
        }
    }

    public byte[] generatePdf() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD);
            Font cellFont = new Font(Font.HELVETICA, 9);

            document.add(new Paragraph("Отчёт по аренде — Ошский рынок", titleFont));
            document.add(new Paragraph("Дата: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))));
            document.add(Chunk.NEWLINE);

            document.add(new Paragraph("Арендаторы", headerFont));
            document.add(Chunk.NEWLINE);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            addPdfHeaderCell(table, "ИНН", headerFont);
            addPdfHeaderCell(table, "ФИО", headerFont);
            addPdfHeaderCell(table, "Место", headerFont);
            addPdfHeaderCell(table, "Аренда (сом)", headerFont);
            addPdfHeaderCell(table, "Задолженность (сом)", headerFont);

            for (RentContract c : contractRepository.findAllByActiveTrueOrderByCreatedAtDesc()) {
                addPdfCell(table, c.getTenant().getInn(), cellFont);
                addPdfCell(table, c.getTenant().getFullName(), cellFont);
                addPdfCell(table, c.getPlace().getPlaceNumber(), cellFont);
                addPdfCell(table, c.getPlace().getMonthlyRent().toPlainString(), cellFont);
                addPdfCell(table, c.getDebt().toPlainString(), cellFont);
            }
            document.add(table);
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка генерации PDF: " + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    public byte[] generateTxt() {
        StringBuilder sb = new StringBuilder();
        sb.append("ОТЧЁТ ПО АРЕНДЕ — ОШСКИЙ РЫНОК\n");
        sb.append("Дата: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))).append("\n");
        sb.append("=".repeat(80)).append("\n\n");

        sb.append("АРЕНДАТОРЫ:\n");
        sb.append(String.format("%-20s %-30s %-10s %-15s %-15s%n",
                "ИНН", "ФИО", "Место", "Аренда", "Задолженность"));
        sb.append("-".repeat(90)).append("\n");

        for (RentContract c : contractRepository.findAllByActiveTrueOrderByCreatedAtDesc()) {
            sb.append(String.format("%-20s %-30s %-10s %-15s %-15s%n",
                    c.getTenant().getInn(),
                    c.getTenant().getFullName(),
                    c.getPlace().getPlaceNumber(),
                    c.getPlace().getMonthlyRent() + " сом",
                    c.getDebt() + " сом"));
        }

        sb.append("\nПЛАТЕЖИ:\n");
        sb.append(String.format("%-20s %-30s %-12s %-15s%n",
                "ИНН", "ФИО", "Дата", "Сумма"));
        sb.append("-".repeat(77)).append("\n");

        for (Payment p : paymentRepository.findAllForReport()) {
            sb.append(String.format("%-20s %-30s %-12s %-15s%n",
                    p.getContract().getTenant().getInn(),
                    p.getContract().getTenant().getFullName(),
                    p.getPaymentDate().toString(),
                    p.getAmount() + " сом"));
        }

        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeTenantsSheet(XSSFWorkbook workbook, Sheet sheet) {
        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        Row header = sheet.createRow(0);
        String[] cols = {"ИНН", "ФИО", "Телефон", "Место", "Проход", "Отдел", "Аренда (сом)", "Задолженность (сом)"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (RentContract c : contractRepository.findAllByActiveTrueOrderByCreatedAtDesc()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(c.getTenant().getInn());
            row.createCell(1).setCellValue(c.getTenant().getFullName());
            row.createCell(2).setCellValue(c.getTenant().getPhone() != null ? c.getTenant().getPhone() : "");
            row.createCell(3).setCellValue(c.getPlace().getPlaceNumber());
            row.createCell(4).setCellValue(c.getPlace().getAisle() != null ? c.getPlace().getAisle() : "");
            row.createCell(5).setCellValue(c.getPlace().getDepartment() != null ? c.getPlace().getDepartment() : "");
            row.createCell(6).setCellValue(c.getPlace().getMonthlyRent().doubleValue());
            row.createCell(7).setCellValue(c.getDebt().doubleValue());
        }
        for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
    }

    private void writePaymentsSheet(XSSFWorkbook workbook, Sheet sheet) {
        Row header = sheet.createRow(0);
        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        headerStyle.setFont(font);

        String[] cols = {"ИНН", "ФИО", "Место", "Дата платежа", "Сумма (сом)", "Описание"};
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowNum = 1;
        for (Payment p : paymentRepository.findAllForReport()) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(p.getContract().getTenant().getInn());
            row.createCell(1).setCellValue(p.getContract().getTenant().getFullName());
            row.createCell(2).setCellValue(p.getContract().getPlace().getPlaceNumber());
            row.createCell(3).setCellValue(p.getPaymentDate().toString());
            row.createCell(4).setCellValue(p.getAmount().doubleValue());
            row.createCell(5).setCellValue(p.getDescription() != null ? p.getDescription() : "");
        }
        for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);
    }

    private void addPdfHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new java.awt.Color(200, 200, 200));
        table.addCell(cell);
    }

    private void addPdfCell(PdfPTable table, String text, Font font) {
        table.addCell(new PdfPCell(new Phrase(text != null ? text : "", font)));
    }
}
