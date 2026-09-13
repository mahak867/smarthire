package com.smarthire;

import com.smarthire.model.*;
import com.smarthire.repository.*;
import com.smarthire.service.*;
import com.smarthire.search.SearchEngine;
import com.smarthire.session.Session;
import com.smarthire.util.AuditLogger;
import com.smarthire.util.ConsoleUI;
import com.smarthire.util.CryptoUtil;
import com.smarthire.util.TableRenderer;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

/**
 * SmartHire - Console Edition
 * ---------------------------
 * A terminal-based recruitment management system written in plain Java
 * (java.util, java.io, java.security, java.time only - no external
 * frameworks). Data is persisted to flat files under ./smarthire_data/.
 *
 * Run with:  java -cp out com.smarthire.Main
 */
public class Main {

    private static final String DATA_DIR = "smarthire_data";

    private static final Scanner sc = new Scanner(System.in);
    private static final Session session = new Session();

    private static UserRepository userRepository;
    private static JobRepository jobRepository;
    private static ApplicationRepository applicationRepository;
    private static InterviewRepository interviewRepository;
    private static StatusHistoryRepository statusHistoryRepository;

    private static AuthService authService;
    private static JobService jobService;
    private static ApplicationService applicationService;
    private static InterviewService interviewService;
    private static DashboardService dashboardService;
    private static ExportService exportService;
    private static AuditLogger auditLogger;
    private static ScoringService scoringService;

    public static void main(String[] args) {
        wireUp();
        seedAdminIfEmpty();

        ConsoleUI.banner("SmartHire - Console Edition");
        System.out.println("A terminal-based recruitment management system.");

        try {
            boolean running = true;
            while (running) {
                if (!session.isLoggedIn()) {
                    running = showAuthMenu();
                } else {
                    running = showRoleMenu();
                }
            }
        } catch (java.util.NoSuchElementException e) {
            // Input stream ended (e.g. piped input exhausted, or Ctrl+D at the terminal).
            System.out.println();
        } catch (RuntimeException e) {
            // Last-resort safety net: never show a raw stack trace to the user.
            ConsoleUI.error("Unexpected error: " + e.getMessage() + ". Exiting.");
        }

        System.out.println("\nGoodbye!");
    }

    private static void wireUp() {
        CryptoUtil crypto = new CryptoUtil(DATA_DIR);
        userRepository = new UserRepository(DATA_DIR, crypto);
        jobRepository = new JobRepository(DATA_DIR, crypto);
        applicationRepository = new ApplicationRepository(DATA_DIR, crypto);
        interviewRepository = new InterviewRepository(DATA_DIR, crypto);
        statusHistoryRepository = new StatusHistoryRepository(DATA_DIR, crypto);

        scoringService = new ScoringService();
        authService = new AuthService(userRepository);
        jobService = new JobService(jobRepository);
        applicationService = new ApplicationService(applicationRepository, jobRepository, statusHistoryRepository, scoringService);
        interviewService = new InterviewService(interviewRepository, applicationRepository, jobRepository, statusHistoryRepository);
        dashboardService = new DashboardService(jobRepository, applicationRepository, interviewRepository, statusHistoryRepository);
        exportService = new ExportService(DATA_DIR);
        auditLogger = new AuditLogger(DATA_DIR);
    }

    /** Creates a default admin account on first run so the app is usable immediately. */
    private static void seedAdminIfEmpty() {
        if (userRepository.isEmpty()) {
            authService.register("System Admin", "admin@smarthire.com", "Admin@123", UserRole.ADMIN);
            ConsoleUI.info("First run detected - seeded default admin account:");
            System.out.println("    email: admin@smarthire.com   password: Admin@123");
        }
    }

    // ----------------------------------------------------------------
    // Auth menu (logged out)
    // ----------------------------------------------------------------

    private static boolean showAuthMenu() {
        ConsoleUI.section("Welcome");
        System.out.println("1. Login");
        System.out.println("2. Register");
        System.out.println("0. Exit");
        String choice = ConsoleUI.prompt(sc, "Choose an option");

        switch (choice) {
            case "1": doLogin(); break;
            case "2": doRegister(); break;
            case "0": return false;
            default: ConsoleUI.error("Invalid option.");
        }
        return true;
    }

    private static void doLogin() {
        String email = ConsoleUI.prompt(sc, "Email");
        String password = ConsoleUI.prompt(sc, "Password");
        try {
            User user = authService.login(email, password);
            session.login(user);
            auditLogger.log(email, "LOGIN");
            ConsoleUI.success("Welcome back, " + user.getName() + "! (" + user.getRole() + ")");
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
    }

    private static void doRegister() {
        String name = ConsoleUI.prompt(sc, "Full name");
        String email = ConsoleUI.prompt(sc, "Email");
        String password = ConsoleUI.prompt(sc, "Password (min 6 chars)");
        System.out.println("Register as: 1) Candidate   2) Recruiter");
        String roleChoice = ConsoleUI.prompt(sc, "Choose");
        UserRole role = roleChoice.equals("2") ? UserRole.RECRUITER : UserRole.CANDIDATE;

        try {
            User user = authService.register(name, email, password, role);
            auditLogger.log(email, "REGISTER as " + role);
            ConsoleUI.success("Account created for " + user.getName() + ". You can now log in.");
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // Role-based menus (logged in)
    // ----------------------------------------------------------------

    private static boolean showRoleMenu() {
        User user = session.getCurrentUser();
        switch (user.getRole()) {
            case ADMIN: return adminMenu();
            case RECRUITER: return recruiterMenu();
            case CANDIDATE: return candidateMenu();
            default: return true;
        }
    }

    private static boolean adminMenu() {
        ConsoleUI.section("Admin Menu - " + session.getCurrentUser().getName());
        System.out.println("1. View all users");
        System.out.println("2. View all jobs");
        System.out.println("3. View all applications");
        System.out.println("4. View all interviews");
        System.out.println("5. System dashboard");
        System.out.println("6. Post a job (as admin)");
        System.out.println("7. Close / reopen a job");
        System.out.println("u. Edit a job posting");
        System.out.println("8. Export dashboard report to file");
        System.out.println("c. Change my password");
        System.out.println("9. Logout");
        System.out.println("0. Exit");
        String choice = ConsoleUI.prompt(sc, "Choose an option");

        switch (choice) {
            case "1": listAllUsers(); break;
            case "2": listAllJobs(); break;
            case "3": listAllApplications(); break;
            case "4": listAllInterviews(); break;
            case "5": showDashboard(-1); break;
            case "6": postJob(); break;
            case "7": toggleJobStatus(); break;
            case "u": editJobPosting(); break;
            case "8": exportDashboard(-1); break;
            case "c": changePassword(); break;
            case "9": session.logout(); ConsoleUI.info("Logged out."); break;
            case "0": return false;
            default: ConsoleUI.error("Invalid option.");
        }
        return true;
    }

    private static boolean recruiterMenu() {
        User user = session.getCurrentUser();
        ConsoleUI.section("Recruiter Menu - " + user.getName());
        System.out.println("1. Post a new job");
        System.out.println("2. View my jobs");
        System.out.println("3. View applications for one of my jobs");
        System.out.println("4. Shortlist top candidates for a job");
        System.out.println("5. Update an application's status");
        System.out.println("6. Schedule an interview");
        System.out.println("7. Complete an interview (record outcome)");
        System.out.println("8. My dashboard");
        System.out.println("u. Edit a job posting");
        System.out.println("e. Export applications for a job to file");
        System.out.println("f. Filter applications for a job by status");
        System.out.println("h. View status history for an application");
        System.out.println("c. Change my password");
        System.out.println("9. Logout");
        System.out.println("0. Exit");
        String choice = ConsoleUI.prompt(sc, "Choose an option");

        switch (choice) {
            case "1": postJob(); break;
            case "2": listMyJobs(); break;
            case "3": viewApplicationsForJob(); break;
            case "4": shortlistCandidates(); break;
            case "5": updateApplicationStatus(); break;
            case "6": scheduleInterview(); break;
            case "7": completeInterview(); break;
            case "8": showDashboard(user.getId()); break;
            case "u": editJobPosting(); break;
            case "e": exportApplicationsForJob(); break;
            case "f": filterApplicationsForJob(); break;
            case "h": viewStatusHistory(); break;
            case "c": changePassword(); break;
            case "9": session.logout(); ConsoleUI.info("Logged out."); break;
            case "0": return false;
            default: ConsoleUI.error("Invalid option.");
        }
        return true;
    }

    private static boolean candidateMenu() {
        User user = session.getCurrentUser();
        ConsoleUI.section("Candidate Menu - " + user.getName());
        System.out.println("1. Browse open jobs");
        System.out.println("2. Apply to a job");
        System.out.println("3. View my applications");
        System.out.println("4. View my interviews");
        System.out.println("5. Search jobs (substring match)");
        System.out.println("r. Ranked search (relevance-scored, TF-IDF)");
        System.out.println("g. Check my skill gap for a job");
        System.out.println("m. Jobs you might like (recommendations)");
        System.out.println("s. Manage my skill profile");
        System.out.println("c. Change my password");
        System.out.println("9. Logout");
        System.out.println("0. Exit");
        String choice = ConsoleUI.prompt(sc, "Choose an option");

        switch (choice) {
            case "1": listAllJobs(); break;
            case "2": applyToJob(); break;
            case "3": listMyApplications(); break;
            case "4": listMyInterviews(); break;
            case "5": searchJobs(); break;
            case "r": rankedSearchJobs(); break;
            case "g": checkSkillGap(); break;
            case "m": recommendJobs(); break;
            case "s": manageSkillProfile(); break;
            case "c": changePassword(); break;
            case "9": session.logout(); ConsoleUI.info("Logged out."); break;
            case "0": return false;
            default: ConsoleUI.error("Invalid option.");
        }
        return true;
    }

    // ----------------------------------------------------------------
    // Shared actions
    // ----------------------------------------------------------------

    private static void listAllUsers() {
        ConsoleUI.section("All Users");
        TableRenderer.printUsers(userRepository.findAll());
        ConsoleUI.pause(sc);
    }

    private static void listAllJobs() {
        ConsoleUI.section("Jobs");
        List<Job> jobs = jobRepository.findAll();
        TableRenderer.printJobs(jobs, applicantCounts(jobs));
        ConsoleUI.pause(sc);
    }

    private static void listMyJobs() {
        ConsoleUI.section("My Jobs");
        List<Job> jobs = jobService.listByRecruiter(session.getCurrentUser().getId());
        TableRenderer.printJobs(jobs, applicantCounts(jobs));
        ConsoleUI.pause(sc);
    }

    /** Builds a jobId -> applicant count map so listings don't force recruiters/admins to guess IDs blindly. */
    private static Map<Integer, Integer> applicantCounts(List<Job> jobs) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Job j : jobs) counts.put(j.getId(), applicationRepository.findByJob(j.getId()).size());
        return counts;
    }

    /** Builds a candidateId -> name map so application tables show who applied, not just a bare ID. */
    private static Map<Integer, String> candidateNames(List<JobApplication> apps) {
        Map<Integer, String> names = new HashMap<>();
        for (JobApplication a : apps) {
            if (!names.containsKey(a.getCandidateId())) {
                names.put(a.getCandidateId(), userRepository.findById(a.getCandidateId()).map(User::getName).orElse("Unknown"));
            }
        }
        return names;
    }

    private static void listAllApplications() {
        ConsoleUI.section("All Applications");
        List<JobApplication> apps = applicationService.listAll();
        TableRenderer.printApplications(apps, candidateNames(apps));
        ConsoleUI.pause(sc);
    }

    private static void listAllInterviews() {
        ConsoleUI.section("All Interviews");
        TableRenderer.printInterviews(interviewService.listAll());
        ConsoleUI.pause(sc);
    }

    private static void searchJobs() {
        ConsoleUI.section("Search Jobs");
        String keyword = ConsoleUI.prompt(sc, "Keyword (title, department, or skill)");
        List<Job> results = jobService.searchOpenJobs(keyword);
        TableRenderer.printJobs(results, applicantCounts(results));
        ConsoleUI.pause(sc);
    }

    private static void rankedSearchJobs() {
        ConsoleUI.section("Ranked Search (TF-IDF + cosine similarity)");
        String query = ConsoleUI.prompt(sc, "Search query (free text, e.g. \"java backend engineer\")");
        List<SearchEngine.SearchResult> results = jobService.rankedSearchOpenJobs(query);
        if (results.isEmpty()) {
            ConsoleUI.info("No relevant jobs found for that query.");
        } else {
            System.out.printf("%-4s %-8s %-24s %-14s%n", "ID", "Score", "Title", "Department");
            for (SearchEngine.SearchResult r : results) {
                Job j = r.getJob();
                System.out.printf("%-4d %-8s %-24s %-14s%n", j.getId(),
                        String.format("%.3f", r.getScore()), j.getTitle(), j.getDepartment());
            }
            ConsoleUI.info("Higher score = more relevant to your query (cosine similarity of TF-IDF vectors).");
        }
        ConsoleUI.pause(sc);
    }

    private static void recommendJobs() {
        ConsoleUI.section("Jobs You Might Like");
        String skillsRaw = ConsoleUI.prompt(sc, skillsPromptLabel("Your skills (comma separated)"));
        List<String> skills = resolveSkills(skillsRaw);
        List<Job> recommended = jobService.recommendJobs(skills, scoringService, 5);
        if (recommended.isEmpty()) {
            ConsoleUI.info("No open jobs to recommend right now.");
        } else {
            TableRenderer.printJobs(recommended, applicantCounts(recommended));
            ConsoleUI.info("Ranked by projected match score against your skills (merge sort, see util.Sorter).");
        }
        ConsoleUI.pause(sc);
    }

    private static void checkSkillGap() {
        ConsoleUI.section("Check My Skill Gap");
        int jobId = ConsoleUI.promptInt(sc, "Job ID");
        String skillsRaw = ConsoleUI.prompt(sc, skillsPromptLabel("Your current skills (comma separated)"));
        try {
            Job job = jobService.getById(jobId);
            List<String> mySkills = resolveSkills(skillsRaw);
            double score = scoringService.computeScore(mySkills, job.getRequiredSkills());
            List<String> missing = scoringService.missingSkills(mySkills, job.getRequiredSkills());
            System.out.println("Projected match score: " + String.format("%.1f%%", score));
            if (missing.isEmpty()) {
                ConsoleUI.success("You already cover every required skill for this job!");
            } else {
                ConsoleUI.info("Skills you're missing: " + String.join(", ", missing));
            }
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void viewStatusHistory() {
        ConsoleUI.section("Application Status History");
        int appId = ConsoleUI.promptInt(sc, "Application ID");
        List<StatusHistoryEntry> history = applicationService.getHistory(appId);
        if (history.isEmpty()) {
            ConsoleUI.info("No history found for that application.");
        } else {
            history.forEach(h -> System.out.println("  " + h));
        }
        ConsoleUI.pause(sc);
    }

    private static void showDashboard(int recruiterIdOrAll) {
        ConsoleUI.section("Dashboard");
        System.out.println(dashboardService.buildReport(recruiterIdOrAll));
        ConsoleUI.pause(sc);
    }

    private static void postJob() {
        ConsoleUI.section("Post a New Job");
        try {
            String title = ConsoleUI.prompt(sc, "Job title");
            String department = ConsoleUI.prompt(sc, "Department");
            String description = ConsoleUI.prompt(sc, "Description");
            String skillsRaw = ConsoleUI.prompt(sc, "Required skills (comma separated)");
            List<String> skills = splitCsv(skillsRaw);

            System.out.println("Employment type: 1) FULL_TIME 2) PART_TIME 3) INTERNSHIP 4) CONTRACT");
            String typeChoice = ConsoleUI.prompt(sc, "Choose");
            EmploymentType type = parseEmploymentType(typeChoice);

            Job job = jobService.postJob(session.getCurrentUser(), title, description, department, skills, type);
            auditLogger.log(session.getCurrentUser().getEmail(), "POST_JOB #" + job.getId() + " " + job.getTitle());
            ConsoleUI.success("Job posted with ID #" + job.getId());
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void toggleJobStatus() {
        ConsoleUI.section("Close / Reopen a Job");
        int jobId = ConsoleUI.promptInt(sc, "Job ID");
        System.out.println("1. Close job   2. Reopen job");
        String choice = ConsoleUI.prompt(sc, "Choose");
        try {
            if (choice.equals("1")) {
                jobService.closeJob(session.getCurrentUser(), jobId);
                ConsoleUI.success("Job #" + jobId + " closed.");
            } else {
                jobService.reopenJob(session.getCurrentUser(), jobId);
                ConsoleUI.success("Job #" + jobId + " reopened.");
            }
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void applyToJob() {
        ConsoleUI.section("Apply to a Job");
        List<Job> openJobs = jobService.listOpenJobs();
        if (openJobs.isEmpty()) {
            ConsoleUI.info("There are no open jobs right now.");
            ConsoleUI.pause(sc);
            return;
        }
        openJobs.forEach(j -> System.out.println(j + "\n"));

        int jobId = ConsoleUI.promptInt(sc, "Enter the Job ID to apply to");
        String skillsRaw = ConsoleUI.prompt(sc, skillsPromptLabel("Your skills (comma separated)"));
        List<String> skills = resolveSkills(skillsRaw);
        String coverLetter = ConsoleUI.prompt(sc, "Cover letter (one line)");

        try {
            JobApplication app = applicationService.apply(session.getCurrentUser(), jobId, skills, coverLetter);
            auditLogger.log(session.getCurrentUser().getEmail(), "APPLY job#" + jobId + " -> application#" + app.getId());
            ConsoleUI.success("Application submitted! ID #" + app.getId()
                    + " - Match score: " + String.format("%.1f%%", app.getScore()));
            // Only offer to save if they actually typed something (didn't just reuse the saved profile as-is).
            if (!skillsRaw.trim().isEmpty()) {
                String saveChoice = ConsoleUI.prompt(sc, "Save these as your skill profile for next time? (y/n)");
                if (saveChoice.trim().equalsIgnoreCase("y")) {
                    User refreshed = authService.updateSkills(session.getCurrentUser(), skills);
                    session.login(refreshed);
                    ConsoleUI.info("Skill profile updated.");
                }
            }
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void listMyApplications() {
        ConsoleUI.section("My Applications");
        List<JobApplication> apps = applicationService.listForCandidate(session.getCurrentUser().getId());
        if (apps.isEmpty()) ConsoleUI.info("You haven't applied to any jobs yet.");
        apps.forEach(System.out::println);
        ConsoleUI.pause(sc);
    }

    private static void listMyInterviews() {
        ConsoleUI.section("My Interviews");
        List<JobApplication> myApps = applicationService.listForCandidate(session.getCurrentUser().getId());
        List<Interview> mine = myApps.stream()
                .flatMap(a -> interviewService.listForApplication(a.getId()).stream())
                .collect(Collectors.toList());
        if (mine.isEmpty()) ConsoleUI.info("No interviews scheduled yet.");
        mine.forEach(System.out::println);
        ConsoleUI.pause(sc);
    }

    private static void viewApplicationsForJob() {
        ConsoleUI.section("Applications for a Job");
        int jobId = ConsoleUI.promptInt(sc, "Job ID");
        List<JobApplication> apps = applicationService.listForJob(jobId);
        TableRenderer.printApplications(apps, candidateNames(apps));
        ConsoleUI.pause(sc);
    }

    private static void filterApplicationsForJob() {
        ConsoleUI.section("Filter Applications by Status");
        int jobId = ConsoleUI.promptInt(sc, "Job ID");
        ApplicationStatus[] statuses = ApplicationStatus.values();
        for (int i = 0; i < statuses.length; i++) System.out.println((i + 1) + ". " + statuses[i]);
        int idx = ConsoleUI.promptInt(sc, "Choose a status") - 1;
        if (idx < 0 || idx >= statuses.length) {
            ConsoleUI.error("Invalid choice.");
        } else {
            List<JobApplication> filtered = applicationService.listForJobByStatus(jobId, statuses[idx]);
            TableRenderer.printApplications(filtered, candidateNames(filtered));
        }
        ConsoleUI.pause(sc);
    }

    private static void exportDashboard(int recruiterIdOrAll) {
        ConsoleUI.section("Export Dashboard Report");
        try {
            String report = dashboardService.buildReport(recruiterIdOrAll);
            String path = exportService.exportDashboard(report);
            auditLogger.log(session.getCurrentUser().getEmail(), "EXPORT dashboard -> " + path);
            ConsoleUI.success("Report written to: " + path);
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void exportApplicationsForJob() {
        ConsoleUI.section("Export Applications Report");
        int jobId = ConsoleUI.promptInt(sc, "Job ID");
        try {
            Job job = jobService.getById(jobId);
            List<JobApplication> apps = applicationService.listForJob(jobId);
            String path = exportService.exportApplicationsForJob(job, apps, candidateNames(apps));
            auditLogger.log(session.getCurrentUser().getEmail(), "EXPORT applications job#" + jobId + " -> " + path);
            ConsoleUI.success("Report written to: " + path);
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void changePassword() {
        ConsoleUI.section("Change Password");
        String oldPass = ConsoleUI.prompt(sc, "Current password");
        String newPass = ConsoleUI.prompt(sc, "New password (min 6 chars)");
        try {
            User updated = authService.changePassword(session.getCurrentUser(), oldPass, newPass);
            session.login(updated); // refresh in-memory session - it held the old hash/salt otherwise
            auditLogger.log(updated.getEmail(), "CHANGE_PASSWORD");
            ConsoleUI.success("Password updated.");
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void manageSkillProfile() {
        ConsoleUI.section("Manage My Skill Profile");
        User user = session.getCurrentUser();
        if (user.getSkills().isEmpty()) {
            ConsoleUI.info("You haven't saved any skills yet.");
        } else {
            System.out.println("Current skills: " + String.join(", ", user.getSkills()));
        }
        String raw = ConsoleUI.prompt(sc, "New skills, comma separated (leave blank to keep current)");
        if (!raw.trim().isEmpty()) {
            List<String> updatedSkills = splitCsv(raw);
            User refreshed = authService.updateSkills(user, updatedSkills);
            session.login(refreshed);
            auditLogger.log(refreshed.getEmail(), "UPDATE_SKILLS");
            ConsoleUI.success("Skill profile updated: " + String.join(", ", updatedSkills));
        } else {
            ConsoleUI.info("No changes made.");
        }
        ConsoleUI.pause(sc);
    }

    private static void editJobPosting() {
        ConsoleUI.section("Edit a Job Posting");
        int jobId = ConsoleUI.promptInt(sc, "Job ID");
        try {
            Job job = jobService.getById(jobId);
            System.out.println("Leave any field blank to keep its current value.");
            String title = ConsoleUI.prompt(sc, "Title [" + job.getTitle() + "]");
            String department = ConsoleUI.prompt(sc, "Department [" + job.getDepartment() + "]");
            String description = ConsoleUI.prompt(sc, "Description [" + job.getDescription() + "]");
            String skillsRaw = ConsoleUI.prompt(sc, "Required skills, comma separated [" + String.join(", ", job.getRequiredSkills()) + "]");
            System.out.println("Employment type: 1) FULL_TIME 2) PART_TIME 3) INTERNSHIP 4) CONTRACT (blank = keep " + job.getEmploymentType() + ")");
            String typeChoice = ConsoleUI.prompt(sc, "Choose");

            String finalTitle = title.trim().isEmpty() ? job.getTitle() : title;
            String finalDepartment = department.trim().isEmpty() ? job.getDepartment() : department;
            String finalDescription = description.trim().isEmpty() ? job.getDescription() : description;
            List<String> finalSkills = skillsRaw.trim().isEmpty() ? job.getRequiredSkills() : splitCsv(skillsRaw);
            EmploymentType finalType = typeChoice.trim().isEmpty() ? job.getEmploymentType() : parseEmploymentType(typeChoice);

            jobService.editJob(session.getCurrentUser(), jobId, finalTitle, finalDescription, finalDepartment, finalSkills, finalType);
            auditLogger.log(session.getCurrentUser().getEmail(), "EDIT_JOB #" + jobId);
            ConsoleUI.success("Job #" + jobId + " updated.");
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    /** Builds a skills prompt label that surfaces the candidate's saved profile, if any, as a default. */
    private static String skillsPromptLabel(String base) {
        List<String> saved = session.getCurrentUser().getSkills();
        if (saved != null && !saved.isEmpty()) {
            return base + " (Enter to use saved: " + String.join(", ", saved) + ")";
        }
        return base;
    }

    /** Falls back to the candidate's saved skill profile when they leave the prompt blank. */
    private static List<String> resolveSkills(String raw) {
        if (raw.trim().isEmpty()) {
            List<String> saved = session.getCurrentUser().getSkills();
            if (saved != null && !saved.isEmpty()) return saved;
        }
        return splitCsv(raw);
    }

    private static void shortlistCandidates() {
        ConsoleUI.section("Shortlist Top Candidates");
        int jobId = ConsoleUI.promptInt(sc, "Job ID");
        int n = ConsoleUI.promptInt(sc, "How many candidates to shortlist");
        try {
            List<JobApplication> shortlisted = applicationService.shortlistTop(session.getCurrentUser(), jobId, n);
            if (shortlisted.isEmpty()) {
                ConsoleUI.info("No applications found for this job.");
            } else {
                ConsoleUI.success("Shortlisted " + shortlisted.size() + " candidate(s):");
                TableRenderer.printApplications(shortlisted, candidateNames(shortlisted));
            }
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void updateApplicationStatus() {
        ConsoleUI.section("Update Application Status");
        int appId = ConsoleUI.promptInt(sc, "Application ID");
        System.out.println("Choose new status:");
        ApplicationStatus[] statuses = ApplicationStatus.values();
        for (int i = 0; i < statuses.length; i++) {
            System.out.println((i + 1) + ". " + statuses[i]);
        }
        int idx = ConsoleUI.promptInt(sc, "Choose") - 1;
        if (idx < 0 || idx >= statuses.length) {
            ConsoleUI.error("Invalid choice.");
            ConsoleUI.pause(sc);
            return;
        }
        try {
            applicationService.updateStatus(session.getCurrentUser(), appId, statuses[idx]);
            auditLogger.log(session.getCurrentUser().getEmail(), "UPDATE_STATUS application#" + appId + " -> " + statuses[idx]);
            ConsoleUI.success("Application #" + appId + " updated to " + statuses[idx]);
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void scheduleInterview() {
        ConsoleUI.section("Schedule an Interview");
        int appId = ConsoleUI.promptInt(sc, "Application ID");
        String interviewer = ConsoleUI.prompt(sc, "Interviewer name");
        String dateStr = ConsoleUI.prompt(sc, "Date & time (yyyy-MM-ddTHH:mm, e.g. 2026-08-30T14:30)");
        System.out.println("Mode: 1) ONLINE 2) IN_PERSON 3) PHONE");
        String modeChoice = ConsoleUI.prompt(sc, "Choose");
        InterviewMode mode = parseInterviewMode(modeChoice);

        try {
            LocalDateTime when = LocalDateTime.parse(dateStr);
            Interview interview = interviewService.schedule(session.getCurrentUser(), appId, when, interviewer, mode);
            ConsoleUI.success("Interview scheduled: " + interview);
        } catch (DateTimeParseException e) {
            ConsoleUI.error("Could not parse that date/time. Use format yyyy-MM-ddTHH:mm");
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    private static void completeInterview() {
        ConsoleUI.section("Complete an Interview");
        int interviewId = ConsoleUI.promptInt(sc, "Interview ID");
        String feedback = ConsoleUI.prompt(sc, "Feedback notes");
        String hireChoice = ConsoleUI.prompt(sc, "Hire this candidate? (y/n)");
        boolean hire = hireChoice.trim().equalsIgnoreCase("y");
        try {
            interviewService.complete(session.getCurrentUser(), interviewId, feedback, hire);
            ConsoleUI.success("Interview #" + interviewId + " marked complete. Outcome: "
                    + (hire ? "HIRED" : "REJECTED"));
        } catch (SmartHireException e) {
            ConsoleUI.error(e.getMessage());
        }
        ConsoleUI.pause(sc);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.trim().isEmpty()) return new ArrayList<>();
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    private static EmploymentType parseEmploymentType(String choice) {
        switch (choice) {
            case "2": return EmploymentType.PART_TIME;
            case "3": return EmploymentType.INTERNSHIP;
            case "4": return EmploymentType.CONTRACT;
            default: return EmploymentType.FULL_TIME;
        }
    }

    private static InterviewMode parseInterviewMode(String choice) {
        switch (choice) {
            case "2": return InterviewMode.IN_PERSON;
            case "3": return InterviewMode.PHONE;
            default: return InterviewMode.ONLINE;
        }
    }
}
