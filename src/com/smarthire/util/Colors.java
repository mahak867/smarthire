package com.smarthire.util;

/**
 * ANSI escape codes for colored terminal output. No external library needed -
 * these are just special character sequences that most terminals interpret.
 * If ENABLED is false (e.g. output is being redirected/graded via a script),
 * all codes resolve to empty strings so plain text still prints correctly.
 */
public class Colors {

    /** Flip to false to disable all color output (e.g. when piping to a file). */
    public static boolean ENABLED = true;

    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String CYAN = "\u001B[36m";
    public static final String GRAY = "\u001B[90m";

    /** Wraps text in a color code, honoring the ENABLED flag (checked at call time). */
    public static String wrap(String color, String text) {
        return ENABLED ? color + text + RESET : text;
    }
}
