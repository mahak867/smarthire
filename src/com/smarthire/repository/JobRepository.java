package com.smarthire.repository;

import com.smarthire.model.Job;
import com.smarthire.util.CryptoUtil;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class JobRepository {

    private final Path file;
    private final CryptoUtil crypto;
    private final List<Job> jobs = new ArrayList<>();
    private int nextId = 1;

    public JobRepository(String dataDir, CryptoUtil crypto) {
        this.file = Paths.get(dataDir, "jobs.dat");
        this.crypto = crypto;
        load();
    }

    private void load() {
        for (String line : crypto.readDecryptedLines(file)) {
            if (line.trim().isEmpty()) continue;
            Job j = Job.fromCsv(line);
            jobs.add(j);
            if (j.getId() >= nextId) nextId = j.getId() + 1;
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (Job j : jobs) lines.add(j.toCsv());
        crypto.writeEncryptedLines(file, lines);
    }

    public int nextId() { return nextId++; }

    public void save(Job job) {
        jobs.removeIf(j -> j.getId() == job.getId());
        jobs.add(job);
        persist();
    }

    /** Saves a group of new/updated jobs with one encrypted file rewrite. */
    public void saveAll(List<Job> batch) {
        if (batch == null || batch.isEmpty()) return;
        Set<Integer> ids = new HashSet<>();
        for (Job job : batch) ids.add(job.getId());
        jobs.removeIf(existing -> ids.contains(existing.getId()));
        jobs.addAll(batch);
        for (Job job : batch) if (job.getId() >= nextId) nextId = job.getId() + 1;
        persist();
    }

    public Optional<Job> findById(int id) {
        return jobs.stream().filter(j -> j.getId() == id).findFirst();
    }

    public List<Job> findAll() {
        return new ArrayList<>(jobs);
    }

    public List<Job> findByRecruiter(int recruiterId) {
        return jobs.stream().filter(j -> j.getRecruiterId() == recruiterId).collect(Collectors.toList());
    }
}
