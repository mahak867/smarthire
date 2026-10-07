package com.smarthire.service;

import com.smarthire.model.Job;
import com.smarthire.model.JobApplication;
import com.smarthire.model.User;
import com.smarthire.model.UserRole;
import com.smarthire.repository.ApplicationRepository;
import com.smarthire.repository.JobRepository;
import com.smarthire.util.CryptoUtil;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Stores candidate resumes encrypted with the same local AES-GCM key as SmartHire records. */
public final class ResumeService {
    public static final long MAX_RESUME_BYTES = 15L * 1024L * 1024L;

    private final Path resumesDirectory;
    private final CryptoUtil crypto;
    private final ApplicationRepository applications;
    private final JobRepository jobs;

    public ResumeService(String dataDirectory, CryptoUtil crypto, ApplicationRepository applications, JobRepository jobs) {
        this.resumesDirectory = Paths.get(dataDirectory, "resumes");
        this.crypto = crypto;
        this.applications = applications;
        this.jobs = jobs;
    }

    /** Validates and stores a candidate's PDF/DOCX resume, replacing any previous upload. */
    public String upload(User candidate, Path source) {
        if (candidate == null || candidate.getRole() != UserRole.CANDIDATE) {
            throw new SmartHireException("Only candidates can upload a resume.");
        }
        if (source == null || !Files.isRegularFile(source) || !Files.isReadable(source)) {
            throw new SmartHireException("Choose a readable PDF or DOCX file.");
        }
        try {
            long size = Files.size(source);
            if (size == 0 || size > MAX_RESUME_BYTES) {
                throw new SmartHireException("Resume must be between 1 byte and 15 MB.");
            }
            String originalName = source.getFileName().toString();
            String lowerName = originalName.toLowerCase(Locale.ROOT);
            if (!lowerName.endsWith(".pdf") && !lowerName.endsWith(".docx")) {
                throw new SmartHireException("Only PDF and DOCX resumes are supported.");
            }
            byte[] bytes = Files.readAllBytes(source);
            if (lowerName.endsWith(".pdf")) validatePdf(bytes);
            else validateDocx(source);

            Files.createDirectories(resumesDirectory);
            Path target = resumePath(candidate.getId());
            Path temporary = Files.createTempFile(resumesDirectory, ".resume-", ".tmp");
            try {
                List<String> encryptedRecord = Arrays.asList(
                        Base64.getEncoder().encodeToString(originalName.getBytes(StandardCharsets.UTF_8)),
                        Base64.getEncoder().encodeToString(bytes));
                crypto.writeEncryptedLines(temporary, encryptedRecord);
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException ignored) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return originalName;
        } catch (IOException e) {
            throw new SmartHireException("Could not save the resume: " + e.getMessage());
        }
    }

    public boolean hasResume(int candidateId) {
        return candidateId > 0 && Files.isRegularFile(resumePath(candidateId));
    }

    public Optional<String> fileName(int candidateId) {
        if (!hasResume(candidateId)) return Optional.empty();
        return Optional.of(readDocument(candidateId).getFileName());
    }

    /** Candidate can retrieve only their own resume. */
    public ResumeDocument loadOwn(User candidate) {
        if (candidate == null || candidate.getRole() != UserRole.CANDIDATE) {
            throw new SmartHireException("Only candidates can access a personal resume.");
        }
        return readDocument(candidate.getId());
    }

    /** Recruiters may retrieve resumes only through a real application on their own job; admins may retrieve any applicant resume. */
    public ResumeDocument loadForApplication(User viewer, int applicationId) {
        if (viewer == null) throw new SmartHireException("A signed-in recruiter or admin is required.");
        if (viewer.getRole() != UserRole.ADMIN && viewer.getRole() != UserRole.RECRUITER) {
            throw new SmartHireException("Only recruiters or admins can access applicant resumes.");
        }
        JobApplication application = applications.findById(applicationId)
                .orElseThrow(() -> new SmartHireException("Application not found."));
        Job job = jobs.findById(application.getJobId())
                .orElseThrow(() -> new SmartHireException("The application's job was not found."));
        if (viewer.getRole() == UserRole.RECRUITER && viewer.getId() != job.getRecruiterId()) {
            throw new SmartHireException("You can only access resumes for your own job postings.");
        }
        return readDocument(application.getCandidateId());
    }

    private ResumeDocument readDocument(int candidateId) {
        if (candidateId <= 0 || !hasResume(candidateId)) {
            throw new SmartHireException("No resume has been uploaded for this candidate.");
        }
        List<String> record = crypto.readDecryptedLines(resumePath(candidateId));
        if (record.size() != 2) throw new SmartHireException("The saved resume record is damaged.");
        try {
            String name = new String(Base64.getDecoder().decode(record.get(0)), StandardCharsets.UTF_8);
            byte[] content = Base64.getDecoder().decode(record.get(1));
            return new ResumeDocument(name, content);
        } catch (IllegalArgumentException e) {
            throw new SmartHireException("The saved resume record is damaged.");
        }
    }

    private Path resumePath(int candidateId) {
        if (candidateId <= 0) throw new SmartHireException("Invalid candidate id.");
        return resumesDirectory.resolve("candidate_" + candidateId + ".resume.dat");
    }

    private static void validatePdf(byte[] bytes) {
        int prefixLength = Math.min(bytes.length, 1024);
        String prefix = new String(bytes, 0, prefixLength, StandardCharsets.ISO_8859_1);
        if (!prefix.contains("%PDF-")) throw new SmartHireException("The selected file is not a valid PDF document.");
    }

    private static void validateDocx(Path file) throws IOException {
        boolean contentTypes = false;
        boolean wordDocument = false;
        try (ZipFile zip = new ZipFile(file.toFile())) {
            java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if ("[Content_Types].xml".equals(name)) contentTypes = true;
                if ("word/document.xml".equals(name)) wordDocument = true;
            }
        } catch (IOException e) {
            throw new SmartHireException("The selected file is not a valid DOCX document.");
        }
        if (!contentTypes || !wordDocument) throw new SmartHireException("The selected file is not a valid DOCX document.");
    }

    public static final class ResumeDocument {
        private final String fileName;
        private final byte[] content;

        private ResumeDocument(String fileName, byte[] content) {
            this.fileName = fileName;
            this.content = content.clone();
        }

        public String getFileName() { return fileName; }
        public byte[] getContent() { return content.clone(); }
    }
}
