package com.smarthire.repository;

import com.smarthire.model.Interview;
import com.smarthire.util.CryptoUtil;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class InterviewRepository {

    private final Path file;
    private final CryptoUtil crypto;
    private final List<Interview> interviews = new ArrayList<>();
    private int nextId = 1;

    public InterviewRepository(String dataDir, CryptoUtil crypto) {
        this.file = Paths.get(dataDir, "interviews.dat");
        this.crypto = crypto;
        load();
    }

    private void load() {
        for (String line : crypto.readDecryptedLines(file)) {
            if (line.trim().isEmpty()) continue;
            Interview i = Interview.fromCsv(line);
            interviews.add(i);
            if (i.getId() >= nextId) nextId = i.getId() + 1;
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (Interview i : interviews) lines.add(i.toCsv());
        crypto.writeEncryptedLines(file, lines);
    }

    public int nextId() { return nextId++; }

    public void save(Interview interview) {
        interviews.removeIf(i -> i.getId() == interview.getId());
        interviews.add(interview);
        persist();
    }

    public Optional<Interview> findById(int id) {
        return interviews.stream().filter(i -> i.getId() == id).findFirst();
    }

    public List<Interview> findAll() {
        return new ArrayList<>(interviews);
    }

    public List<Interview> findByApplication(int applicationId) {
        return interviews.stream().filter(i -> i.getApplicationId() == applicationId).collect(Collectors.toList());
    }
}
