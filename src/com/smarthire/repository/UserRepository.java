package com.smarthire.repository;

import com.smarthire.model.User;
import com.smarthire.util.CryptoUtil;

import java.nio.file.*;
import java.util.*;

/**
 * File-backed repository for User records. Data is encrypted at rest with
 * AES-GCM (see util.CryptoUtil) since this file contains password hashes
 * and personal info. Loads the whole (decrypted) file into memory on
 * startup and re-encrypts+rewrites it after every change.
 */
public class UserRepository {

    private final Path file;
    private final CryptoUtil crypto;
    private final List<User> users = new ArrayList<>();
    private int nextId = 1;

    public UserRepository(String dataDir, CryptoUtil crypto) {
        this.file = Paths.get(dataDir, "users.dat");
        this.crypto = crypto;
        load();
    }

    private void load() {
        for (String line : crypto.readDecryptedLines(file)) {
            if (line.trim().isEmpty()) continue;
            User u = User.fromCsv(line);
            users.add(u);
            if (u.getId() >= nextId) nextId = u.getId() + 1;
        }
    }

    private void persist() {
        List<String> lines = new ArrayList<>();
        for (User u : users) lines.add(u.toCsv());
        crypto.writeEncryptedLines(file, lines);
    }

    public int nextId() { return nextId++; }

    public void save(User user) {
        users.removeIf(u -> u.getId() == user.getId());
        users.add(user);
        persist();
    }

    public Optional<User> findByEmail(String email) {
        return users.stream().filter(u -> u.getEmail().equalsIgnoreCase(email)).findFirst();
    }

    public Optional<User> findById(int id) {
        return users.stream().filter(u -> u.getId() == id).findFirst();
    }

    public List<User> findAll() {
        return new ArrayList<>(users);
    }

    public boolean isEmpty() {
        return users.isEmpty();
    }
}
