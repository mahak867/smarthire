package com.smarthire.repository;

import com.smarthire.model.JobApplication;
import com.smarthire.util.CryptoUtil;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ApplicationRepository {

    private final Path file;
    private final CryptoUtil crypto;
    private final List<JobApplication> applications = new ArrayList<>();
    private int nextId = 1;

    public ApplicationRepository(String dataDir, CryptoUtil crypto) {
        this.file = Paths.get(dataDir, "applications.dat");
        this.crypto = crypto;
        load();
    }

    private void load() {
        for (String line : crypto.readDecryptedLines(file)) {
            if (line.trim().isEmpty()) continue;
            JobApplication a = JobApplication.fromCsv(line);
            applications.add(a);
            if (a.getId() >= nextId) nextId = a.getId() + 1;
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (JobApplication a : applications) lines.add(a.toCsv());
        crypto.writeEncryptedLines(file, lines);
    }

    public int nextId() { return nextId++; }

    public void save(JobApplication app) {
        applications.removeIf(a -> a.getId() == app.getId());
        applications.add(app);
        persist();
    }

    public Optional<JobApplication> findById(int id) {
        return applications.stream().filter(a -> a.getId() == id).findFirst();
    }

    public List<JobApplication> findAll() {
        return new ArrayList<>(applications);
    }

    public List<JobApplication> findByJob(int jobId) {
        return applications.stream().filter(a -> a.getJobId() == jobId).collect(Collectors.toList());
    }

    public List<JobApplication> findByCandidate(int candidateId) {
        return applications.stream().filter(a -> a.getCandidateId() == candidateId).collect(Collectors.toList());
    }

    public boolean existsByJobAndCandidate(int jobId, int candidateId) {
        return applications.stream().anyMatch(a -> a.getJobId() == jobId && a.getCandidateId() == candidateId);
    }
}
