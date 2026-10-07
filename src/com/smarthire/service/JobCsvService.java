package com.smarthire.service;

import com.smarthire.model.EmploymentType;
import com.smarthire.model.Job;
import com.smarthire.model.User;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Excel-compatible UTF-8 CSV exchange for job postings (no external spreadsheet library required). */
public final class JobCsvService {
    public static final String[] HEADERS = {"Title", "Department", "Description", "Required Skills", "Employment Type"};
    private static final long MAX_CSV_BYTES = 10L * 1024L * 1024L;

    private final JobService jobService;

    public JobCsvService(JobService jobService) {
        this.jobService = jobService;
    }

    /** Imports new OPEN postings for the signed-in recruiter/admin; validates every row before saving any job. */
    public int importCsv(User actor, Path file) {
        if (file == null || file.getFileName() == null || !file.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw new SmartHireException("Choose a .csv file (Excel: Save As → CSV UTF-8).");
        }
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new SmartHireException("Choose a readable CSV file.");
        final List<List<String>> rows;
        try {
            if (Files.size(file) > MAX_CSV_BYTES) throw new SmartHireException("CSV file is too large (maximum 10 MB).");
            String contents = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            rows = parseCsv(contents);
        } catch (IOException e) {
            throw new SmartHireException("Could not read the CSV file: " + e.getMessage());
        }
        if (rows.isEmpty()) throw new SmartHireException("The CSV file is empty.");

        Map<String, Integer> columns = indexHeaders(rows.get(0));
        int titleIndex = column(columns, "title");
        int departmentIndex = column(columns, "department");
        int descriptionIndex = column(columns, "description");
        int skillsIndex = column(columns, "requiredskills", "skills");
        int typeIndex = column(columns, "employmenttype", "type");
        if (titleIndex < 0 || typeIndex < 0) {
            throw new SmartHireException("CSV needs at least these headers: Title, Employment Type. Use Export CSV to get a template.");
        }

        List<JobDraft> drafts = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlank(row)) continue;
            if (row.size() > rows.get(0).size()) {
                for (int extra = rows.get(0).size(); extra < row.size(); extra++) {
                    if (!row.get(extra).trim().isEmpty()) throw rowError(i + 1, "unexpected extra column");
                }
            }
            String title = value(row, titleIndex).trim();
            if (title.isEmpty()) throw rowError(i + 1, "Title cannot be blank");
            String rawType = value(row, typeIndex).trim();
            EmploymentType type;
            try {
                String normalized = rawType.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
                type = EmploymentType.valueOf(normalized);
            } catch (IllegalArgumentException e) {
                throw rowError(i + 1, "Employment Type must be FULL_TIME, PART_TIME, INTERNSHIP, or CONTRACT");
            }
            String department = unprotectExcelText(value(row, departmentIndex)).trim();
            String description = unprotectExcelText(value(row, descriptionIndex)).trim();
            String skillsText = unprotectExcelText(value(row, skillsIndex)).trim();
            List<String> skills = skillsText.isEmpty()
                    ? new ArrayList<>()
                    : new ArrayList<>(Arrays.asList(skillsText.split("[,;]")));
            for (int skill = 0; skill < skills.size(); skill++) skills.set(skill, skills.get(skill).trim());
            skills.removeIf(String::isEmpty);
            drafts.add(new JobDraft(unprotectExcelText(title), description, department, skills, type));
        }
        if (drafts.isEmpty()) throw new SmartHireException("The CSV has no job rows to import.");

        for (JobDraft draft : drafts) {
            jobService.postJob(actor, draft.title, draft.description, draft.department, draft.skills, draft.type);
        }
        return drafts.size();
    }

    /** Writes postings as UTF-8 CSV with a BOM so Excel opens non-ASCII text correctly. */
    public void exportCsv(List<Job> jobs, Path target) {
        if (target == null) throw new SmartHireException("Choose a destination for the CSV file.");
        StringBuilder csv = new StringBuilder("\uFEFF");
        appendRow(csv, Arrays.asList(HEADERS));
        for (Job job : jobs) {
            appendRow(csv, Arrays.asList(job.getTitle(), job.getDepartment(), job.getDescription(),
                    String.join("; ", job.getRequiredSkills()), job.getEmploymentType().name()));
        }
        try {
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(target, csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new SmartHireException("Could not export jobs CSV: " + e.getMessage());
        }
    }

    private static Map<String, Integer> indexHeaders(List<String> headerRow) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < headerRow.size(); i++) {
            String key = headerRow.get(i).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!key.isEmpty()) result.put(key, i);
        }
        return result;
    }

    private static int column(Map<String, Integer> columns, String... names) {
        for (String name : names) if (columns.containsKey(name)) return columns.get(name);
        return -1;
    }

    private static String value(List<String> row, int index) {
        return index < 0 || index >= row.size() ? "" : row.get(index);
    }

    private static boolean isBlank(List<String> row) {
        for (String field : row) if (!field.trim().isEmpty()) return false;
        return true;
    }

    private static SmartHireException rowError(int rowNumber, String message) {
        return new SmartHireException("CSV row " + rowNumber + ": " + message + ".");
    }

    private static String unprotectExcelText(String value) {
        if (value.length() < 2 || value.charAt(0) != '\'') return value;
        int i = 1;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        if (i < value.length() && "=+-@".indexOf(value.charAt(i)) >= 0) return value.substring(1);
        return value;
    }

    private static void appendRow(StringBuilder output, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) output.append(',');
            String value = spreadsheetSafe(fields.get(i));
            output.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        output.append("\r\n");
    }

    private static String spreadsheetSafe(String value) {
        if (value == null) return "";
        int i = 0;
        while (i < value.length() && Character.isWhitespace(value.charAt(i))) i++;
        if (i < value.length() && "=+-@".indexOf(value.charAt(i)) >= 0) return "'" + value;
        return value;
    }

    /** Parses comma-delimited CSV including escaped quotes and quoted multiline fields. */
    private static List<List<String>> parseCsv(String contents) {
        if (contents.startsWith("\uFEFF")) contents = contents.substring(1);
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterQuote = false;
        for (int i = 0; i < contents.length(); i++) {
            char c = contents.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < contents.length() && contents.charAt(i + 1) == '"') {
                        field.append('"'); i++;
                    } else {
                        inQuotes = false; afterQuote = true;
                    }
                } else field.append(c);
                continue;
            }
            if (afterQuote) {
                if (c == ',') {
                    row.add(field.toString()); field.setLength(0); afterQuote = false;
                } else if (c == '\n' || c == '\r') {
                    row.add(field.toString()); field.setLength(0); rows.add(row); row = new ArrayList<>(); afterQuote = false;
                    if (c == '\r' && i + 1 < contents.length() && contents.charAt(i + 1) == '\n') i++;
                } else if (!Character.isWhitespace(c)) {
                    throw new SmartHireException("CSV contains unexpected characters after a quoted field.");
                }
                continue;
            }
            if (c == '"' && field.length() == 0) {
                inQuotes = true;
            } else if (c == '"') {
                throw new SmartHireException("CSV contains a quote inside an unquoted field.");
            } else if (c == ',') {
                row.add(field.toString()); field.setLength(0);
            } else if (c == '\n' || c == '\r') {
                row.add(field.toString()); field.setLength(0); rows.add(row); row = new ArrayList<>();
                if (c == '\r' && i + 1 < contents.length() && contents.charAt(i + 1) == '\n') i++;
            } else field.append(c);
        }
        if (inQuotes) throw new SmartHireException("CSV has an unclosed quoted field.");
        if (afterQuote || !row.isEmpty() || field.length() > 0) {
            row.add(field.toString()); rows.add(row);
        }
        return rows;
    }

    private static final class JobDraft {
        private final String title;
        private final String description;
        private final String department;
        private final List<String> skills;
        private final EmploymentType type;

        private JobDraft(String title, String description, String department, List<String> skills, EmploymentType type) {
            this.title = title; this.description = description; this.department = department; this.skills = skills; this.type = type;
        }
    }
}
