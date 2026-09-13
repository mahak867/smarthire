package com.smarthire.model;

import com.smarthire.util.CsvUtil;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Represents a job posting created by a recruiter/admin.
 */
public class Job {

    private int id;
    private String title;
    private String description;
    private String department;
    private List<String> requiredSkills;
    private EmploymentType employmentType;
    private int recruiterId;
    private JobStatus status;
    private LocalDate postedDate;

    public Job(int id, String title, String description, String department, List<String> requiredSkills,
               EmploymentType employmentType, int recruiterId, JobStatus status, LocalDate postedDate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.department = department;
        this.requiredSkills = requiredSkills;
        this.employmentType = employmentType;
        this.recruiterId = recruiterId;
        this.status = status;
        this.postedDate = postedDate;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getDepartment() { return department; }
    public List<String> getRequiredSkills() { return requiredSkills; }
    public EmploymentType getEmploymentType() { return employmentType; }
    public int getRecruiterId() { return recruiterId; }
    public JobStatus getStatus() { return status; }
    public LocalDate getPostedDate() { return postedDate; }

    public void setStatus(JobStatus status) { this.status = status; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setRequiredSkills(List<String> requiredSkills) { this.requiredSkills = requiredSkills; }
    public void setDepartment(String department) { this.department = department; }
    public void setEmploymentType(EmploymentType employmentType) { this.employmentType = employmentType; }

    public String toCsv() {
        String safeTitle = CsvUtil.sanitize(title);
        String safeDesc = CsvUtil.sanitize(description);
        String safeDept = CsvUtil.sanitize(department);
        return id + "|" + safeTitle + "|" + safeDesc + "|" + safeDept + "|"
                + CsvUtil.joinSkills(requiredSkills) + "|" + employmentType + "|"
                + recruiterId + "|" + status + "|" + postedDate;
    }

    public static Job fromCsv(String line) {
        String[] p = line.split("\\|", -1);
        List<String> skills = p[4].isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(p[4].split(";")));
        return new Job(
                Integer.parseInt(p[0]),
                p[1],
                p[2],
                p[3],
                skills,
                EmploymentType.valueOf(p[5]),
                Integer.parseInt(p[6]),
                JobStatus.valueOf(p[7]),
                LocalDate.parse(p[8])
        );
    }

    @Override
    public String toString() {
        return "#" + id + " " + title + " (" + department + ", " + employmentType + ") [" + status + "]"
                + "\n    Skills required: " + String.join(", ", requiredSkills)
                + "\n    Posted: " + postedDate;
    }
}
