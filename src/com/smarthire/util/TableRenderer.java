package com.smarthire.util;

import com.smarthire.model.*;

import java.util.List;
import java.util.Map;

/**
 * Renders lists of domain objects as aligned, readable console tables
 * using only String.format column widths - no external table library.
 */
public class TableRenderer {

    /** Plain version - no applicant counts (used only where that data isn't available). */
    public static void printJobs(List<Job> jobs) {
        printJobs(jobs, null);
    }

    /**
     * @param applicantCounts optional map of jobId -> number of applications received.
     *                        Pass null to omit the "Apps" column entirely.
     */
    public static void printJobs(List<Job> jobs, Map<Integer, Integer> applicantCounts) {
        if (jobs.isEmpty()) { ConsoleUI.info("No jobs to show."); return; }
        boolean showCounts = applicantCounts != null;
        if (showCounts) {
            System.out.printf("%-4s %-22s %-14s %-12s %-10s %-6s %-10s%n", "ID", "Title", "Department", "Type", "Status", "Apps", "Posted");
        } else {
            System.out.printf("%-4s %-22s %-14s %-12s %-10s %-10s%n", "ID", "Title", "Department", "Type", "Status", "Posted");
        }
        printDivider(showCounts ? 84 : 78);
        for (Job j : jobs) {
            if (showCounts) {
                int count = applicantCounts.getOrDefault(j.getId(), 0);
                System.out.printf("%-4d %-22s %-14s %-12s %-19s %-6d %-10s%n",
                        j.getId(), trim(j.getTitle(), 22), trim(j.getDepartment(), 14),
                        j.getEmploymentType(), ConsoleUI.statusColor(j.getStatus().toString()), count, j.getPostedDate());
            } else {
                System.out.printf("%-4d %-22s %-14s %-12s %-19s %-10s%n",
                        j.getId(), trim(j.getTitle(), 22), trim(j.getDepartment(), 14),
                        j.getEmploymentType(), ConsoleUI.statusColor(j.getStatus().toString()), j.getPostedDate());
            }
            System.out.println("     Skills: " + Colors.wrap(Colors.GRAY, String.join(", ", j.getRequiredSkills())));
        }
    }

    /** Plain version - shows a bare candidate ID (used only where a name lookup isn't available). */
    public static void printApplications(List<JobApplication> apps) {
        printApplications(apps, null);
    }

    /** @param candidateNames optional map of candidateId -> display name. Pass null to just show "#id". */
    public static void printApplications(List<JobApplication> apps, Map<Integer, String> candidateNames) {
        if (apps.isEmpty()) { ConsoleUI.info("No applications to show."); return; }
        System.out.printf("%-4s %-6s %-22s %-8s %-22s %-10s%n", "ID", "JobID", "Candidate", "Score", "Status", "Applied");
        printDivider(78);
        for (JobApplication a : apps) {
            String candidateLabel = candidateNames != null
                    ? trim(candidateNames.getOrDefault(a.getCandidateId(), "Unknown"), 18) + " (#" + a.getCandidateId() + ")"
                    : "#" + a.getCandidateId();
            System.out.printf("%-4d %-6d %-22s %-7s %-29s %-10s%n",
                    a.getId(), a.getJobId(), candidateLabel,
                    String.format("%.1f%%", a.getScore()),
                    ConsoleUI.statusColor(a.getStatus().toString()), a.getAppliedDate());
        }
    }

    public static void printUsers(List<User> users) {
        if (users.isEmpty()) { ConsoleUI.info("No users to show."); return; }
        System.out.printf("%-4s %-20s %-28s %-10s%n", "ID", "Name", "Email", "Role");
        printDivider(70);
        for (User u : users) {
            System.out.printf("%-4d %-20s %-28s %-10s%n", u.getId(), trim(u.getName(), 20), trim(u.getEmail(), 28), u.getRole());
        }
    }

    public static void printInterviews(List<Interview> interviews) {
        if (interviews.isEmpty()) { ConsoleUI.info("No interviews to show."); return; }
        System.out.printf("%-4s %-8s %-18s %-16s %-10s %-19s%n", "ID", "AppID", "Interviewer", "Mode", "Status", "When");
        printDivider(80);
        for (Interview i : interviews) {
            System.out.printf("%-4d %-8d %-18s %-16s %-19s %-19s%n",
                    i.getId(), i.getApplicationId(), trim(i.getInterviewer(), 18), i.getMode(),
                    ConsoleUI.statusColor(i.getStatus().toString()), i.getScheduledAt());
            if (i.getFeedback() != null && !i.getFeedback().isEmpty()) {
                System.out.println("     Feedback: " + Colors.wrap(Colors.GRAY, i.getFeedback()));
            }
        }
    }

    private static void printDivider(int width) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) sb.append('-');
        System.out.println(Colors.wrap(Colors.GRAY, sb.toString()));
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
