package com.smarthire.repository;

import com.smarthire.model.StatusHistoryEntry;
import com.smarthire.util.CryptoUtil;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Encrypted at rest, same as the other repositories. This one is
 * logically append-only, but since AES-GCM needs a fresh IV per encryption
 * (see CryptoUtil), each append re-encrypts the whole (still small) file
 * rather than appending raw bytes to it.
 */
public class StatusHistoryRepository {

    private final Path file;
    private final CryptoUtil crypto;
    private final List<StatusHistoryEntry> entries = new ArrayList<>();
    private int nextId = 1;

    public StatusHistoryRepository(String dataDir, CryptoUtil crypto) {
        this.file = Paths.get(dataDir, "status_history.dat");
        this.crypto = crypto;
        load();
    }

    private void load() {
        for (String line : crypto.readDecryptedLines(file)) {
            if (line.trim().isEmpty()) continue;
            StatusHistoryEntry e = StatusHistoryEntry.fromCsv(line);
            entries.add(e);
            if (e.getId() >= nextId) nextId = e.getId() + 1;
        }
    }

    public int nextId() { return nextId++; }

    public void append(StatusHistoryEntry entry) {
        entries.add(entry);
        List<String> lines = new ArrayList<>();
        for (StatusHistoryEntry e : entries) lines.add(e.toCsv());
        crypto.writeEncryptedLines(file, lines);
    }

    public List<StatusHistoryEntry> findByApplication(int applicationId) {
        return entries.stream()
                .filter(e -> e.getApplicationId() == applicationId)
                .sorted(Comparator.comparing(StatusHistoryEntry::getChangedAt))
                .collect(Collectors.toList());
    }
}
