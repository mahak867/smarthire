package com.smarthire.test;

import com.smarthire.model.*;
import com.smarthire.repository.*;
import com.smarthire.search.SearchEngine;
import com.smarthire.service.*;
import com.smarthire.util.CryptoUtil;
import com.smarthire.util.Sorter;

import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.*;

/**
 * A lightweight, self-written test harness using Java's built-in `assert`
 * keyword instead of a JUnit dependency - keeping the project truly
 * "core Java, no external libraries" while still demonstrating testing
 * discipline.
 *
 * IMPORTANT: assert statements are no-ops unless assertions are enabled.
 * Run with:
 *   java -ea -cp out com.smarthire.test.SelfTestRunner
 *
 * Uses a throwaway data directory (self_test_data/) so it never touches
 * your real smarthire_data/.
 */
public class SelfTestRunner {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        if (!assertionsEnabled()) {
            System.out.println("WARNING: assertions are disabled. Re-run with: java -ea -cp out com.smarthire.test.SelfTestRunner");
        }

        System.out.println("Running SmartHire self-tests...\n");

        testPasswordHashingAndAuth();
        testScoringEngine();
        testMergeSortCorrectness();
        testSearchEngineRelevanceOrdering();
        testJobApplicationWorkflow();
        testEncryptionRoundTrip();
        testSkillProfilePersistence();
        testJobEditPermissions();
        testCsvDelimiterSafety();
        testShortlistRejectsNonPositiveCount();
        testResumeUploadEncryptionAndValidation();
        testJobCsvImportExportRoundTrip();

        System.out.println("\n" + passed + " passed, " + failed + " failed.");
        if (failed > 0) System.exit(1);
    }

    private static boolean assertionsEnabled() {
        boolean enabled = false;
        assert enabled = true;
        return enabled;
    }

    // ------------------------------------------------------------------

    private static void testPasswordHashingAndAuth() {
        run("Password hashing + auth", () -> {
            String data = "self_test_data/auth";
            wipe(data);
            CryptoUtil crypto = new CryptoUtil(data);
            UserRepository users = new UserRepository(data, crypto);
            AuthService auth = new AuthService(users);

            User u = auth.register("Test User", "test@example.com", "password123", UserRole.CANDIDATE);
            assert !u.getPasswordHash().equals("password123") : "Password must never be stored in plain text";

            User loggedIn = auth.login("test@example.com", "password123");
            assert loggedIn.getId() == u.getId() : "Login should return the same user that registered";

            boolean rejected = false;
            try {
                auth.login("test@example.com", "wrongpassword");
            } catch (SmartHireException e) {
                rejected = true;
            }
            assert rejected : "Login with the wrong password must be rejected";
        });
    }

    private static void testScoringEngine() {
        run("Resume-vs-job scoring", () -> {
            ScoringService scoring = new ScoringService();
            List<String> resume = Arrays.asList("Java", "SQL", "Git");
            List<String> required = Arrays.asList("Java", "SQL", "Spring", "Docker");

            double score = scoring.computeScore(resume, required);
            assert Math.abs(score - 50.0) < 0.01 : "2 of 4 required skills matched should score 50%, got " + score;

            List<String> missing = scoring.missingSkills(resume, required);
            assert missing.size() == 2 : "Should report exactly 2 missing skills";
            assert missing.contains("Spring") && missing.contains("Docker") : "Missing skills should be Spring and Docker";
        });
    }

    private static void testMergeSortCorrectness() {
        run("Merge sort correctness", () -> {
            List<Integer> unsorted = Arrays.asList(5, 3, 8, 1, 9, 2, 7);
            List<Integer> sorted = Sorter.mergeSort(unsorted, Comparator.naturalOrder());
            List<Integer> expected = Arrays.asList(1, 2, 3, 5, 7, 8, 9);
            assert sorted.equals(expected) : "Merge sort should produce ascending order, got " + sorted;

            List<Integer> reversed = Sorter.mergeSort(unsorted, Comparator.<Integer>naturalOrder().reversed());
            assert reversed.get(0) == 9 : "Reversed comparator should put the largest element first";

            List<Integer> empty = Sorter.mergeSort(new ArrayList<Integer>(), Comparator.naturalOrder());
            assert empty.isEmpty() : "Sorting an empty list should return an empty list";
        });
    }

    private static void testSearchEngineRelevanceOrdering() {
        run("TF-IDF search relevance ranking", () -> {
            Job javaJob = new Job(1, "Java Backend Developer", "Build backend systems", "Engineering",
                    Arrays.asList("Java", "Spring", "SQL"), EmploymentType.FULL_TIME, 1, JobStatus.OPEN, LocalDate.now());
            Job dataJob = new Job(2, "Data Analyst", "Analyze data with Python", "Analytics",
                    Arrays.asList("Python", "SQL", "Excel"), EmploymentType.FULL_TIME, 1, JobStatus.OPEN, LocalDate.now());

            SearchEngine engine = new SearchEngine();
            engine.build(Arrays.asList(javaJob, dataJob));

            List<SearchEngine.SearchResult> results = engine.search("java backend engineer");
            assert !results.isEmpty() : "Search for 'java backend engineer' should return at least one result";
            assert results.get(0).getJob().getId() == 1 : "The Java job should rank above the Data Analyst job for this query";
        });
    }

    private static void testJobApplicationWorkflow() {
        run("End-to-end job/application workflow", () -> {
            String data = "self_test_data/workflow";
            wipe(data);
            CryptoUtil crypto = new CryptoUtil(data);
            UserRepository users = new UserRepository(data, crypto);
            JobRepository jobs = new JobRepository(data, crypto);
            ApplicationRepository apps = new ApplicationRepository(data, crypto);
            StatusHistoryRepository history = new StatusHistoryRepository(data, crypto);

            AuthService auth = new AuthService(users);
            JobService jobService = new JobService(jobs);
            ApplicationService appService = new ApplicationService(apps, jobs, history, new ScoringService());

            User recruiter = auth.register("Recruiter", "r@example.com", "password1", UserRole.RECRUITER);
            User candidate = auth.register("Candidate", "c@example.com", "password1", UserRole.CANDIDATE);

            Job job = jobService.postJob(recruiter, "Java Developer", "desc", "Eng",
                    Arrays.asList("Java", "SQL"), EmploymentType.FULL_TIME);
            assert job.getStatus() == JobStatus.OPEN : "New jobs should start OPEN";

            JobApplication app = appService.apply(candidate, job.getId(), Arrays.asList("Java", "SQL"), "Cover letter");
            assert app.getScore() == 100.0 : "Full skill match should score 100%, got " + app.getScore();

            boolean duplicateBlocked = false;
            try {
                appService.apply(candidate, job.getId(), Arrays.asList("Java"), "Second try");
            } catch (SmartHireException e) {
                duplicateBlocked = true;
            }
            assert duplicateBlocked : "Applying twice to the same job must be blocked";

            appService.updateStatus(recruiter, app.getId(), ApplicationStatus.HIRED);
            List<StatusHistoryEntry> h = appService.getHistory(app.getId());
            assert h.size() >= 2 : "History should contain both the initial APPLIED entry and the HIRED transition";
        });
    }

    private static void testEncryptionRoundTrip() {
        run("AES encryption round-trip", () -> {
            String data = "self_test_data/crypto";
            wipe(data);
            CryptoUtil crypto = new CryptoUtil(data);
            java.nio.file.Path file = java.nio.file.Paths.get(data, "sample.dat");

            List<String> original = Arrays.asList("line one", "line two with data", "line three");
            crypto.writeEncryptedLines(file, original);

            byte[] rawBytes = java.nio.file.Files.readAllBytes(file);
            String rawAsText = new String(rawBytes, java.nio.charset.StandardCharsets.UTF_8);
            assert !rawAsText.contains("line one") : "Ciphertext on disk must not contain the plaintext";

            List<String> roundTripped = crypto.readDecryptedLines(file);
            assert roundTripped.equals(original) : "Decrypting should return exactly the original lines";
        });
    }

    private static void testSkillProfilePersistence() {
        run("Skill profile save + reload", () -> {
            String data = "self_test_data/skills";
            wipe(data);
            CryptoUtil crypto = new CryptoUtil(data);
            UserRepository users = new UserRepository(data, crypto);
            AuthService auth = new AuthService(users);

            User candidate = auth.register("Skill Tester", "skills@example.com", "password1", UserRole.CANDIDATE);
            assert candidate.getSkills().isEmpty() : "New users should start with no saved skills";

            User updated = auth.updateSkills(candidate, Arrays.asList("Java", "SQL", "Python"));
            assert updated.getSkills().size() == 3 : "Skill profile should hold exactly what was saved";

            // Reload from a fresh repository instance to prove it round-trips through the encrypted file, not just memory.
            UserRepository reloaded = new UserRepository(data, crypto);
            User fromDisk = reloaded.findById(candidate.getId()).orElseThrow(() -> new AssertionError("User missing after reload"));
            assert fromDisk.getSkills().equals(Arrays.asList("Java", "SQL", "Python")) : "Saved skills must survive a reload from disk";
            assert passwordStillVerifies(fromDisk) : "Updating skills must not corrupt the password hash";
        });
    }

    private static boolean passwordStillVerifies(User u) {
        return com.smarthire.util.PasswordUtil.verify("password1", u.getSalt(), u.getPasswordHash());
    }

    private static void testJobEditPermissions() {
        run("Job edit permission + field update", () -> {
            String data = "self_test_data/jobedit";
            wipe(data);
            CryptoUtil crypto = new CryptoUtil(data);
            UserRepository users = new UserRepository(data, crypto);
            JobRepository jobs = new JobRepository(data, crypto);
            AuthService auth = new AuthService(users);
            JobService jobService = new JobService(jobs);

            User owner = auth.register("Owner Recruiter", "owner@example.com", "password1", UserRole.RECRUITER);
            User otherRecruiter = auth.register("Other Recruiter", "other@example.com", "password1", UserRole.RECRUITER);
            User admin = auth.register("Admin", "admin2@example.com", "password1", UserRole.ADMIN);

            Job job = jobService.postJob(owner, "Old Title", "old desc", "Old Dept",
                    Arrays.asList("Java"), EmploymentType.FULL_TIME);

            boolean blocked = false;
            try {
                jobService.editJob(otherRecruiter, job.getId(), "Hacked Title", "x", "x", Arrays.asList("x"), EmploymentType.CONTRACT);
            } catch (SmartHireException e) {
                blocked = true;
            }
            assert blocked : "A recruiter must not be able to edit another recruiter's job";

            Job editedByOwner = jobService.editJob(owner, job.getId(), "New Title", "new desc", "New Dept",
                    Arrays.asList("Java", "SQL"), EmploymentType.PART_TIME);
            assert editedByOwner.getTitle().equals("New Title") : "Owner edit should update the title";
            assert editedByOwner.getRequiredSkills().size() == 2 : "Owner edit should update required skills";
            assert editedByOwner.getEmploymentType() == EmploymentType.PART_TIME : "Owner edit should update employment type";

            Job editedByAdmin = jobService.editJob(admin, job.getId(), "Admin Title", "d", "d", Arrays.asList("d"), EmploymentType.INTERNSHIP);
            assert editedByAdmin.getTitle().equals("Admin Title") : "Admin should be able to edit any job";
        });
    }

    private static void testCsvDelimiterSafety() {
        run("CSV delimiter injection safety", () -> {
            String data = "self_test_data/csvsafety";
            wipe(data);
            CryptoUtil crypto = new CryptoUtil(data);
            UserRepository users = new UserRepository(data, crypto);
            JobRepository jobs = new JobRepository(data, crypto);
            AuthService auth = new AuthService(users);
            JobService jobService = new JobService(jobs);

            // A "|" or newline in free text used to desync every field after it in the
            // pipe-delimited file, corrupting the role/enum field and crashing the app
            // on its *next* startup (repositories load every record eagerly).
            User recruiter = auth.register("Priya | Recruiter\nSecond Line", "priya.safety@example.com",
                    "password1", UserRole.RECRUITER);
            Job job = jobService.postJob(recruiter, "Backend | Frontend Dev", "desc", "Eng | Ops",
                    Arrays.asList("java|weird", "sql;weird"), EmploymentType.FULL_TIME);

            // Reload from fresh repository instances - exactly what a real restart does.
            UserRepository reloadedUsers = new UserRepository(data, crypto);
            JobRepository reloadedJobs = new JobRepository(data, crypto);

            User userFromDisk = reloadedUsers.findById(recruiter.getId())
                    .orElseThrow(() -> new AssertionError("User vanished after reload"));
            assert userFromDisk.getRole() == UserRole.RECRUITER : "Role field must not be corrupted by a '|' in the name";
            assert com.smarthire.util.PasswordUtil.verify("password1", userFromDisk.getSalt(), userFromDisk.getPasswordHash())
                    : "Password must still verify after a '|'/newline in the name";

            Job jobFromDisk = reloadedJobs.findById(job.getId())
                    .orElseThrow(() -> new AssertionError("Job vanished after reload"));
            assert jobFromDisk.getEmploymentType() == EmploymentType.FULL_TIME
                    : "Employment type must not be corrupted by a '|' in title/department";
            assert jobFromDisk.getRequiredSkills().size() == 2
                    : "Skill list must not be corrupted by a stray '|' or ';' inside a skill";
        });
    }

    private static void testShortlistRejectsNonPositiveCount() {
        run("Shortlist rejects non-positive count", () -> {
            String data = "self_test_data/shortlistguard";
            wipe(data);
            CryptoUtil crypto = new CryptoUtil(data);
            UserRepository users = new UserRepository(data, crypto);
            JobRepository jobs = new JobRepository(data, crypto);
            ApplicationRepository apps = new ApplicationRepository(data, crypto);
            StatusHistoryRepository history = new StatusHistoryRepository(data, crypto);
            AuthService auth = new AuthService(users);
            JobService jobService = new JobService(jobs);
            ApplicationService appService = new ApplicationService(apps, jobs, history, new ScoringService());

            User recruiter = auth.register("Rec", "rec.guard@example.com", "password1", UserRole.RECRUITER);
            User candidate = auth.register("Cand", "cand.guard@example.com", "password1", UserRole.CANDIDATE);
            Job job = jobService.postJob(recruiter, "Job", "desc", "Dept", Arrays.asList("java"), EmploymentType.FULL_TIME);
            appService.apply(candidate, job.getId(), Arrays.asList("java"), "cover");

            boolean rejectedZero = false, rejectedNegative = false;
            try { appService.shortlistTop(recruiter, job.getId(), 0); } catch (SmartHireException e) { rejectedZero = true; }
            try { appService.shortlistTop(recruiter, job.getId(), -5); } catch (SmartHireException e) { rejectedNegative = true; }

            assert rejectedZero : "Shortlisting 0 should be a clean validation error, not a silent no-op";
            assert rejectedNegative : "A negative count used to reach Stream.limit(negative) and end the whole session";
        });
    }

    private static void testResumeUploadEncryptionAndValidation() {
        run("Resume upload validation + encrypted persistence", () -> {
            String data = "self_test_data/resume";
            wipe(data);
            CryptoUtil crypto = new CryptoUtil(data);
            UserRepository users = new UserRepository(data, crypto);
            ApplicationRepository applications = new ApplicationRepository(data, crypto);
            JobRepository jobRepository = new JobRepository(data, crypto);
            AuthService auth = new AuthService(users);
            ResumeService resumes = new ResumeService(data, crypto, applications, jobRepository);
            User candidate = auth.register("Resume Candidate", "resume.candidate@example.com", "password1", UserRole.CANDIDATE);
            User recruiter = auth.register("Resume Recruiter", "resume.recruiter@example.com", "password1", UserRole.RECRUITER);
            User otherRecruiter = auth.register("Other Recruiter", "resume.other@example.com", "password1", UserRole.RECRUITER);
            Path pdf = Paths.get(data, "Taylor Morgan Resume.PDF");
            byte[] pdfBytes = "%PDF-1.7\nCandidate resume fixture".getBytes(StandardCharsets.ISO_8859_1);
            Files.write(pdf, pdfBytes);

            assert resumes.upload(candidate, pdf).equals("Taylor Morgan Resume.PDF") : "Original resume name should be retained";
            assert resumes.hasResume(candidate.getId()) : "Candidate upload should be available";
            ResumeService.ResumeDocument pdfFromDisk = resumes.loadOwn(candidate);
            assert pdfFromDisk.getFileName().equals("Taylor Morgan Resume.PDF") : "PDF metadata should persist";
            assert Arrays.equals(pdfBytes, pdfFromDisk.getContent()) : "PDF bytes should round-trip exactly";
            byte[] encrypted = Files.readAllBytes(Paths.get(data, "resumes", "candidate_" + candidate.getId() + ".resume.dat"));
            assert !new String(encrypted, StandardCharsets.ISO_8859_1).contains("%PDF-") : "Resume content must be encrypted at rest";

            Path docx = Paths.get(data, "resume.docx");
            try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(docx))) {
                zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
                zip.write("<Types/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write("<document/>".getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
            }
            resumes.upload(candidate, docx);
            assert resumes.loadOwn(candidate).getFileName().equals("resume.docx") : "DOCX upload should replace the previous resume";

            boolean invalidContentRejected = false;
            Path fakePdf = Paths.get(data, "fake.pdf"); Files.write(fakePdf, "not a PDF".getBytes(StandardCharsets.UTF_8));
            try { resumes.upload(candidate, fakePdf); } catch (SmartHireException e) { invalidContentRejected = true; }
            assert invalidContentRejected : "A renamed non-PDF file must be rejected";

            JobService jobs = new JobService(jobRepository);
            Job job = jobs.postJob(recruiter, "Resume review role", "desc", "eng", Arrays.asList("Java"), EmploymentType.FULL_TIME);
            ApplicationService applicationService = new ApplicationService(applications, jobRepository,
                    new StatusHistoryRepository(data, crypto), new ScoringService());
            JobApplication application = applicationService.apply(candidate, job.getId(), Arrays.asList("Java"), "Application with resume");
            assert resumes.loadForApplication(recruiter, application.getId()).getFileName().equals("resume.docx")
                    : "The recruiter owning a job should retrieve an applicant resume";
            boolean otherRecruiterRejected = false;
            try { resumes.loadForApplication(otherRecruiter, application.getId()); } catch (SmartHireException e) { otherRecruiterRejected = true; }
            assert otherRecruiterRejected : "A different recruiter must not retrieve the resume for another recruiter's job";
        });
    }

    private static void testJobCsvImportExportRoundTrip() {
        run("Job CSV import/export round trip", () -> {
            String data = "self_test_data/jobcsv";
            String secondData = "self_test_data/jobcsv_roundtrip";
            wipe(data); wipe(secondData);
            CryptoUtil crypto = new CryptoUtil(data);
            UserRepository users = new UserRepository(data, crypto);
            JobRepository jobs = new JobRepository(data, crypto);
            User recruiter = new AuthService(users).register("CSV Recruiter", "csv.recruiter@example.com", "password1", UserRole.RECRUITER);
            JobService jobService = new JobService(jobs);
            JobCsvService csv = new JobCsvService(jobService);
            Path importFile = Paths.get(data, "import.csv"); Files.createDirectories(importFile.getParent());
            String source = "\uFEFFTitle,Department,Description,Required Skills,Employment Type\r\n"
                    + "\"Platform, Engineer\",\"R&D\",\"Build \"\"fast\"\" tools\nfor APIs\",\"Java; SQL\",Full Time\r\n"
                    + "\"=SUM(1,1)\",Operations,Safe,Excel,CONTRACT\r\n";
            Files.write(importFile, source.getBytes(StandardCharsets.UTF_8));

            assert csv.importCsv(recruiter, importFile) == 2 : "Both valid rows should import";
            List<Job> imported = jobService.listByRecruiter(recruiter.getId());
            assert imported.get(0).getTitle().equals("Platform, Engineer") : "Quoted comma in title should parse";
            assert imported.get(0).getDescription().contains("\n") : "Quoted multiline descriptions should parse; got: " + imported.get(0).getDescription().replace("\n", "<LF>");
            assert imported.get(0).getRequiredSkills().equals(Arrays.asList("Java", "SQL")) : "Semicolon-separated skills should parse";
            assert imported.get(0).getEmploymentType() == EmploymentType.FULL_TIME : "Human-readable employment type should normalize";

            Path exported = Paths.get(data, "export.csv"); csv.exportCsv(imported, exported);
            String exportText = new String(Files.readAllBytes(exported), StandardCharsets.UTF_8);
            assert exportText.startsWith("\uFEFF\"Title\"") : "CSV export should include Excel-friendly UTF-8 BOM and header";
            assert exportText.contains("\"'=SUM(1,1)\"") : "Formula-leading values should be escaped for spreadsheet safety";

            CryptoUtil secondCrypto = new CryptoUtil(secondData);
            User secondRecruiter = new AuthService(new UserRepository(secondData, secondCrypto)).register("Second Recruiter", "csv.second@example.com", "password1", UserRole.RECRUITER);
            JobRepository secondJobs = new JobRepository(secondData, secondCrypto);
            JobService secondJobService = new JobService(secondJobs);
            JobCsvService roundTrip = new JobCsvService(secondJobService);
            assert roundTrip.importCsv(secondRecruiter, exported) == 2 : "Exported CSV should import again";
            List<Job> roundTripped = secondJobService.listByRecruiter(secondRecruiter.getId());
            assert roundTripped.size() == 2 : "Both exported rows should be present after a round trip";
            assert roundTripped.get(1).getTitle().equals("=SUM(1,1)") : "Spreadsheet safety prefix should not alter the actual imported job title";

            Path invalid = Paths.get(data, "invalid.csv");
            Files.write(invalid, "Title,Employment Type\nValid,Full Time\nBroken,UNKNOWN\n".getBytes(StandardCharsets.UTF_8));
            int before = jobService.listAllJobs().size();
            boolean invalidRowRejected = false;
            try { csv.importCsv(recruiter, invalid); } catch (SmartHireException e) { invalidRowRejected = e.getMessage().contains("row 3"); }
            assert invalidRowRejected : "Invalid rows should report their spreadsheet row number";
            assert jobService.listAllJobs().size() == before : "An invalid import must not partially create jobs";
        });
    }

    // ------------------------------------------------------------------

    private static void run(String name, ThrowingRunnable test) {
        try {
            test.run();
            passed++;
            System.out.println("  [PASS] " + name);
        } catch (AssertionError e) {
            failed++;
            System.out.println("  [FAIL] " + name + " -- " + e.getMessage());
        } catch (Exception e) {
            failed++;
            System.out.println("  [ERROR] " + name + " -- " + e);
        }
    }

    private static void wipe(String dir) {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(dir);
            if (!java.nio.file.Files.exists(path)) return;
            java.nio.file.Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> { try { java.nio.file.Files.deleteIfExists(p); } catch (Exception ignored) {} });
        } catch (Exception ignored) {
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
