package com.oshmarket.controller;

import com.oshmarket.dto.FileContent;
import com.oshmarket.dto.admin.*;
import com.oshmarket.security.CustomUserDetails;
import com.oshmarket.service.AdminService;
import com.oshmarket.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final ReportService reportService;

    // Dashboard
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardDto> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
    }

    // Analytics
    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsDto> getAnalytics() {
        return ResponseEntity.ok(adminService.getAnalytics());
    }

    // Tenants
    @GetMapping("/tenants")
    public ResponseEntity<List<TenantListItemDto>> getAllTenants() {
        return ResponseEntity.ok(adminService.getAllTenants());
    }

    @PostMapping("/tenants")
    public ResponseEntity<TenantDetailDto> createTenant(@Valid @RequestBody CreateTenantRequest request) {
        return ResponseEntity.ok(adminService.createTenantWithPlace(request));
    }

    @GetMapping("/tenants/{id}")
    public ResponseEntity<TenantDetailDto> getTenant(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getTenantDetail(id));
    }

    @PutMapping("/tenants/{id}")
    public ResponseEntity<TenantDetailDto> updateTenant(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateTenantRequest request) {
        return ResponseEntity.ok(adminService.updateTenant(id, request));
    }

    @DeleteMapping("/tenants/{id}")
    public ResponseEntity<Map<String, String>> deleteTenant(@PathVariable Long id) {
        adminService.deleteTenant(id);
        return ResponseEntity.ok(Map.of("message", "Арендатор удалён"));
    }

    @PostMapping("/tenants/{id}/payment")
    public ResponseEntity<Map<String, String>> recordPayment(@PathVariable Long id,
                                                              @Valid @RequestBody RecordPaymentRequest request) {
        adminService.recordPayment(id, request);
        return ResponseEntity.ok(Map.of("message", "Платёж внесён"));
    }

    // ===== Модерация онлайн-платежей =====
    @GetMapping("/payments/pending")
    public ResponseEntity<List<PendingPaymentDto>> getPendingPayments() {
        return ResponseEntity.ok(adminService.getPendingPayments());
    }

    @GetMapping("/payments/{id}/receipt")
    public ResponseEntity<byte[]> getReceipt(@PathVariable Long id) {
        FileContent receipt = adminService.getReceipt(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + receipt.filename() + "\"")
                .contentType(MediaType.parseMediaType(receipt.contentType()))
                .body(receipt.bytes());
    }

    @PostMapping("/payments/{id}/approve")
    public ResponseEntity<Map<String, String>> approvePayment(@PathVariable Long id,
                                                              @AuthenticationPrincipal CustomUserDetails admin) {
        adminService.approvePayment(id, admin.getId());
        return ResponseEntity.ok(Map.of("message", "Платёж подтверждён"));
    }

    @PostMapping("/payments/{id}/reject")
    public ResponseEntity<Map<String, String>> rejectPayment(@PathVariable Long id,
                                                             @AuthenticationPrincipal CustomUserDetails admin,
                                                             @Valid @RequestBody RejectPaymentRequest request) {
        adminService.rejectPayment(id, admin.getId(), request.getReason());
        return ResponseEntity.ok(Map.of("message", "Платёж отклонён"));
    }

    // ===== Способы оплаты (QR) =====
    @GetMapping("/payment-methods")
    public ResponseEntity<List<PaymentMethodAdminDto>> getPaymentMethods() {
        return ResponseEntity.ok(adminService.getPaymentMethods());
    }

    @PostMapping("/payment-methods")
    public ResponseEntity<PaymentMethodAdminDto> createPaymentMethod(
            @Valid @RequestBody SavePaymentMethodRequest request) {
        return ResponseEntity.ok(adminService.createPaymentMethod(request));
    }

    @PutMapping("/payment-methods/{id}")
    public ResponseEntity<PaymentMethodAdminDto> updatePaymentMethod(
            @PathVariable Long id, @Valid @RequestBody SavePaymentMethodRequest request) {
        return ResponseEntity.ok(adminService.updatePaymentMethod(id, request));
    }

    @PostMapping(value = "/payment-methods/{id}/qr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaymentMethodAdminDto> uploadQr(@PathVariable Long id,
                                                          @RequestPart("qr") MultipartFile qr) {
        return ResponseEntity.ok(adminService.uploadQr(id, qr));
    }

    // Debtors
    @GetMapping("/debtors")
    public ResponseEntity<List<DebtorDto>> getDebtors() {
        return ResponseEntity.ok(adminService.getDebtors());
    }

    // Places CRUD
    @GetMapping("/places")
    public ResponseEntity<List<PlaceDto>> getAllPlaces() {
        return ResponseEntity.ok(adminService.getAllPlaces());
    }

    @GetMapping("/places/free")
    public ResponseEntity<List<PlaceDto>> getFreePlaces() {
        return ResponseEntity.ok(adminService.getFreePlaces());
    }

    @GetMapping("/places/occupied")
    public ResponseEntity<List<PlaceDto>> getOccupiedPlaces() {
        return ResponseEntity.ok(adminService.getOccupiedPlaces());
    }

    @PostMapping("/places")
    public ResponseEntity<PlaceDto> createPlace(@Valid @RequestBody CreatePlaceRequest request) {
        return ResponseEntity.ok(adminService.createPlace(request));
    }

    @PutMapping("/places/{id}")
    public ResponseEntity<PlaceDto> updatePlace(@PathVariable Long id,
                                                 @Valid @RequestBody CreatePlaceRequest request) {
        return ResponseEntity.ok(adminService.updatePlace(id, request));
    }

    @DeleteMapping("/places/{id}")
    public ResponseEntity<Map<String, String>> deletePlace(@PathVariable Long id) {
        adminService.deletePlace(id);
        return ResponseEntity.ok(Map.of("message", "Место удалено"));
    }

    @PostMapping("/places/{id}/release")
    public ResponseEntity<Map<String, String>> releasePlace(@PathVariable Long id) {
        adminService.releasePlace(id);
        return ResponseEntity.ok(Map.of("message", "Место освобождено"));
    }

    // Reports
    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> exportReport(@RequestParam(defaultValue = "excel") String type) {
        byte[] data;
        String contentType;
        String filename = reportService.generateFilename(
                "excel".equals(type) ? "xlsx" : "pdf".equals(type) ? "pdf" : "txt");

        switch (type) {
            case "excel" -> {
                data = reportService.generateExcel();
                contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                filename = reportService.generateFilename("xlsx");
            }
            case "pdf" -> {
                data = reportService.generatePdf();
                contentType = "application/pdf";
                filename = reportService.generateFilename("pdf");
            }
            default -> {
                data = reportService.generateTxt();
                contentType = "text/plain;charset=UTF-8";
                filename = reportService.generateFilename("txt");
            }
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(data);
    }
}
