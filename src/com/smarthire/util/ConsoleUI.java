package com.smarthire.util;

import java.util.Scanner;

/**
 * Small collection of console formatting helpers so Main.java stays readable.
 */
public class ConsoleUI {

    private static final String LINE = "======================================================";
    private static final String THIN = "------------------------------------------------------";

    public static void banner(String title) {
        System.out.println("\n" + Colors.wrap(Colors.CYAN, LINE));
        System.out.println(Colors.wrap(Colors.CYAN + Colors.BOLD, "   " + title));
        System.out.println(Colors.wrap(Colors.CYAN, LINE));
    }

    public static void section(String title) {
        System.out.println("\n" + Colors.wrap(Colors.BLUE, THIN));
        System.out.println(Colors.wrap(Colors.BLUE + Colors.BOLD, " " + title));
        System.out.println(Colors.wrap(Colors.BLUE, THIN));
    }

    public static void error(String msg) {
        System.out.println(Colors.wrap(Colors.RED, "[!] " + msg));
    }

    public static void success(String msg) {
        System.out.println(Colors.wrap(Colors.GREEN, "[OK] " + msg));
    }

    public static void info(String msg) {
        System.out.println(Colors.wrap(Colors.YELLOW, "[i] " + msg));
    }

    public static String prompt(Scanner sc, String label) {
        System.out.print(label + ": ");
        return sc.nextLine().trim();
    }

    public static int promptInt(Scanner sc, String label) {
        while (true) {
            String raw = prompt(sc, label);
            try {
                return Integer.parseInt(raw.trim());
            } catch (NumberFormatException e) {
                error("Please enter a valid whole number.");
            }
        }
    }

    public static void pause(Scanner sc) {
        System.out.print(Colors.wrap(Colors.GRAY, "\nPress Enter to continue..."));
        sc.nextLine();
    }

    /** Colors an ApplicationStatus/JobStatus/InterviewStatus name for quick visual scanning in tables. */
    public static String statusColor(String status) {
        switch (status) {
            case "OPEN":
            case "HIRED":
            case "COMPLETED":
                return Colors.wrap(Colors.GREEN, status);
            case "CLOSED":
            case "REJECTED":
            case "CANCELLED":
                return Colors.wrap(Colors.RED, status);
            case "SHORTLISTED":
            case "INTERVIEW_SCHEDULED":
            case "SCHEDULED":
                return Colors.wrap(Colors.YELLOW, status);
            default:
                return status;
        }
    }
}
