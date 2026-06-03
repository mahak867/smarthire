// ── SmartHire · dto/response/DashboardStatsResponse.java ──
package com.smarthire.api.v1.dto.response;
public record DashboardStatsResponse(long totalOpenJobs, long applicationsThisWeek, double shortlistRatePct, double avgTimeToHireDays, long totalApplications) {}
