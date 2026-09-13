package com.smarthire.model;

import com.smarthire.util.CsvUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a registered user of the system.
 * Passwords are never stored in plain text - only a salted SHA-256 hash.
 */
public class User {

    private int id;
    private String name;
    private String email;
    private String passwordHash;
    private String salt;
    private UserRole role;
    /** Candidate's saved skill profile, so they don't have to retype it on every apply/search/gap-check. Empty for non-candidates. */
    private List<String> skills;

    public User(int id, String name, String email, String passwordHash, String salt, UserRole role) {
        this(id, name, email, passwordHash, salt, role, new ArrayList<>());
    }

    public User(int id, String name, String email, String passwordHash, String salt, UserRole role, List<String> skills) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.role = role;
        this.skills = skills == null ? new ArrayList<>() : skills;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getSalt() { return salt; }
    public UserRole getRole() { return role; }
    public List<String> getSkills() { return skills; }

    /** Serializes this user to a single pipe-delimited line for file storage. */
    public String toCsv() {
        return id + "|" + CsvUtil.sanitize(name) + "|" + email + "|" + passwordHash + "|" + salt + "|" + role
                + "|" + CsvUtil.joinSkills(skills);
    }

    public static User fromCsv(String line) {
        String[] p = line.split("\\|", -1);
        List<String> skills = (p.length >= 7 && !p[6].isEmpty())
                ? new ArrayList<>(Arrays.asList(p[6].split(";")))
                : new ArrayList<>();
        return new User(
                Integer.parseInt(p[0]),
                p[1],
                p[2],
                p[3],
                p[4],
                UserRole.valueOf(p[5]),
                skills
        );
    }

    @Override
    public String toString() {
        return "#" + id + " " + name + " <" + email + "> [" + role + "]";
    }
}
