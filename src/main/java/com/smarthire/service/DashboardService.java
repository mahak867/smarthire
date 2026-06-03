// ── SmartHire · src/main/java/com/smarthire/service/DashboardService.java ──
package com.smarthire.service;

import com.smarthire.api.v1.dto.response.*;
import com.smarthire.domain.enums.*;
import com.smarthire.domain.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JobRepository         jobRepository;
    private final ApplicationRepository applicationRepository;

    @org.springframework.cache.annotation.Cacheable(value = "dashboard-stats", key = "'global'")
    @Transactional(readOnly = true)
    public DashboardStatsResponse getStats() {
        long openJobs         = jobRepository.countByStatus(JobStatus.OPEN);
        long appsThisWeek     = applicationRepository.countByAppliedAtAfter(
            OffsetDateTime.now().minusWeeks(1));
        long totalApps        = applicationRepository.count();

        // Shortlist rate = SHORTLISTED+ / total
        Map<String, Long> pipeline = getPipelineBreakdown();
        long shortlisted = pipeline.getOrDefault("SHORTLISTED", 0L)
            + pipeline.getOrDefault("INTERVIEW", 0L)
            + pipeline.getOrDefault("OFFERED", 0L);
        double shortlistRate = totalApps > 0
            ? round1((double) shortlisted / totalApps * 100) : 0.0;

        Double rawAvg = applicationRepository.avgDaysToHire();
        double avgTimeToHire = rawAvg != null ? round1(rawAvg) : 0.0;
        return new DashboardStatsResponse(openJobs, appsThisWeek, shortlistRate, avgTimeToHire, totalApps);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getPipelineBreakdown() {
        List<Object[]> rows = applicationRepository.countGroupByStatus();
        Map<String, Long> result = new LinkedHashMap<>();
        // Ordered pipeline stages
        for (ApplicationStatus s : ApplicationStatus.values()) result.put(s.name(), 0L);
        rows.forEach(r -> result.put(((ApplicationStatus) r[0]).name(), (Long) r[1]));
        return result;
    }

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getTopCandidates(int limit) {
        return applicationRepository.findTopCandidates(PageRequest.of(0, limit))
            .stream()
            .map(a -> new ApplicationResponse(
                a.getId(), a.getJob().getId(), a.getJob().getTitle(),
                a.getCandidate().getId(), a.getCandidate().getFullName(), a.getCandidate().getEmail(),
                a.getResumeUrl(), null, a.getStatus().name(),
                a.getAiScore(), a.getAiSummary(), a.getSkillMatchPct(),
                a.getKeywordMatches(), a.isScoringComplete(),
                null, a.getAppliedAt(), a.getUpdatedAt()))
            .collect(Collectors.toList());
    }

    @org.springframework.cache.annotation.Cacheable(value = "hiring-funnel", key = "'global'")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHiringFunnel() {
        Map<String, Long> breakdown = getPipelineBreakdown();
        long total = breakdown.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> funnel = new ArrayList<>();
        List<String> stages = List.of("APPLIED","SCREENING","SHORTLISTED","INTERVIEW","OFFERED");
        long running = total;
        for (String stage : stages) {
            long count = breakdown.getOrDefault(stage, 0L);
            double pct = total > 0 ? round1((double) count / total * 100) : 0.0;
            funnel.add(Map.of(
                "stage", stage,
                "count", count,
                "pct_of_total", pct,
                "conversion_from_prev", running > 0 ? round1((double) count / Math.max(running, 1) * 100) : 0.0
            ));
            running = count;
        }
        return funnel;
    }

    private double round1(double v) {
        return BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
