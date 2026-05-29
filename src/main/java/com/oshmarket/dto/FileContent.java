package com.oshmarket.dto;

/**
 * Содержимое файла, отдаваемого клиенту (QR-код способа оплаты, чек об оплате).
 */
public record FileContent(byte[] bytes, String contentType, String filename) {
}
