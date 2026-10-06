package com.smarthire;

import com.smarthire.repository.ApplicationRepository;
import com.smarthire.repository.InterviewRepository;
import com.smarthire.repository.JobRepository;
import com.smarthire.repository.StatusHistoryRepository;
import com.smarthire.repository.UserRepository;
import com.smarthire.service.ApplicationService;
import com.smarthire.service.AuthService;
import com.smarthire.service.DashboardService;
import com.smarthire.service.ExportService;
import com.smarthire.service.InterviewService;
import com.smarthire.service.JobService;
import com.smarthire.service.ScoringService;
import com.smarthire.util.AuditLogger;
import com.smarthire.util.CryptoUtil;

/**
 * Shared application wiring for the console and desktop GUI entry points.
 * Both interfaces use the same encrypted repositories and business services.
 */
public final class AppContext {
    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final StatusHistoryRepository statusHistoryRepository;
    private final AuthService authService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final InterviewService interviewService;
    private final DashboardService dashboardService;
    private final ExportService exportService;
    private final AuditLogger auditLogger;

    public AppContext(String dataDir) {
        CryptoUtil crypto = new CryptoUtil(dataDir);
        userRepository = new UserRepository(dataDir, crypto);
        jobRepository = new JobRepository(dataDir, crypto);
        applicationRepository = new ApplicationRepository(dataDir, crypto);
        interviewRepository = new InterviewRepository(dataDir, crypto);
        statusHistoryRepository = new StatusHistoryRepository(dataDir, crypto);

        ScoringService scoringService = new ScoringService();
        authService = new AuthService(userRepository);
        jobService = new JobService(jobRepository);
        applicationService = new ApplicationService(applicationRepository, jobRepository,
                statusHistoryRepository, scoringService);
        interviewService = new InterviewService(interviewRepository, applicationRepository,
                jobRepository, statusHistoryRepository);
        dashboardService = new DashboardService(jobRepository, applicationRepository,
                interviewRepository, statusHistoryRepository);
        exportService = new ExportService(dataDir);
        auditLogger = new AuditLogger(dataDir);
    }

    public UserRepository users() { return userRepository; }
    public JobRepository jobs() { return jobRepository; }
    public ApplicationRepository applications() { return applicationRepository; }
    public InterviewRepository interviews() { return interviewRepository; }
    public StatusHistoryRepository statusHistory() { return statusHistoryRepository; }
    public AuthService auth() { return authService; }
    public JobService jobService() { return jobService; }
    public ApplicationService applicationService() { return applicationService; }
    public InterviewService interviewService() { return interviewService; }
    public DashboardService dashboardService() { return dashboardService; }
    public ExportService exportService() { return exportService; }
    public AuditLogger auditLogger() { return auditLogger; }
}
