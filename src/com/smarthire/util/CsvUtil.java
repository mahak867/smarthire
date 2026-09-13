package com.smarthire.util;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Every record in this app is stored as one pipe-delimited line, and skill
 * lists within a record are semicolon-delimited (see model classes' toCsv()).
 * Both "|" and ";" are therefore structurally significant characters - if a
 * user-typed field (a name, job title, interviewer, skill, etc.) contains
 * one, the fixed-position split in fromCsv() silently reads the wrong value
 * into the wrong field, which can cascade into an enum/number parse failure
 * and crash the app on its *next* startup (since repositories load every
 * record eagerly). A stray newline in a field would similarly desync the
 * one-record-per-line file format.
 *
 * Every free-text field gets run through here before being written, so this
 * class is the single place that defines what's safe to store - rather than
 * each model repeating its own ad-hoc ".replace(...)" chain inconsistently.
 */
public class CsvUtil {

    /** For a single free-text field (name, title, description, cover letter, etc.). */
    public static String sanitize(String s) {
        if (s == null) return "";
        return s.replace("|", "/").replace("\n", " ").replace("\r", " ");
    }

    /** For one entry of a semicolon-joined list (skills) - also guards the list's own delimiter. */
    public static String sanitizeListItem(String s) {
        if (s == null) return "";
        return sanitize(s).replace(";", ",");
    }

    /** Sanitizes and joins a skills list the same way everywhere it's serialized. */
    public static String joinSkills(List<String> skills) {
        return skills.stream().map(CsvUtil::sanitizeListItem).collect(Collectors.joining(";"));
    }
}
