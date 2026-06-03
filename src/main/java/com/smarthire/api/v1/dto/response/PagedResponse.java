// ── SmartHire · dto/response/PagedResponse.java ──
package com.smarthire.api.v1.dto.response;
import java.util.List;
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {}
