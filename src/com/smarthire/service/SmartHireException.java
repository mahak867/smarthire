package com.smarthire.service;

/** Unchecked exception used across the service layer for business-rule violations. */
public class SmartHireException extends RuntimeException {
    public SmartHireException(String message) {
        super(message);
    }
}
