// ── SmartHire · dto/response/ApplicationResponse.java ──
package com.smarthire.api.v1.dto.response;
import java.math.BigDecimal; import java.time.OffsetDateTime; import java.util.Map; import java.util.UUID;
public record ApplicationResponse(UUID id, UUID jobId, String jobTitle, UUID candidateId, String candidateName, String candidateEmail, String resumeUrl, String coverLetter, String status, BigDecimal aiScore, String aiSummary, BigDecimal skillMatchPct, Map<String,Object> keywordMatches, boolean scoringComplete, String notes, OffsetDateTime appliedAt, OffsetDateTime updatedAt) {}
