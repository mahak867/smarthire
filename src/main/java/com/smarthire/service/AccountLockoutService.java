// ── SmartHire · service/AccountLockoutService.java ──
package com.smarthire.service;

import com.smarthire.domain.entities.User;
import com.smarthire.domain.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Tracks failed login attempts and locks accounts after exceeding the threshold.
 * Lock duration doubles with each successive lock (exponential backoff).
 * A successful login resets the counter.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountLockoutService {

    private final UserRepository userRepository;

    @Value("${smarthire.security.max-login-attempts:5}")
    private int maxAttempts;

    @Value("${smarthire.security.lockout-minutes:15}")
    private int lockoutMinutes;

    /** Call after a failed password check. Returns true if account is now locked. */
    @Transactional
    public boolean recordFailedAttempt(User user) {
        user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
        user.setLastFailedAt(OffsetDateTime.now());

        if (user.getFailedLoginAttempts() >= maxAttempts) {
            // Exponential backoff: multiply lockout by number of times locked
            int multiplier = Math.max(1, user.getFailedLoginAttempts() / maxAttempts);
            long lockMinutes = (long) lockoutMinutes * multiplier;
            user.setLockedUntil(OffsetDateTime.now().plusMinutes(lockMinutes));
            userRepository.save(user);
            log.warn("Account locked for {} minutes after {} failed attempts: {}",
                lockMinutes, user.getFailedLoginAttempts(), user.getEmail());
            return true;
        }

        userRepository.save(user);
        return false;
    }

    /** Call after a successful login. Resets counter and lock. */
    @Transactional
    public void resetAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            user.setLastFailedAt(null);
            userRepository.save(user);
        }
    }

    /**
     * Returns a safe, consistent error message that does NOT reveal whether
     * the account exists or the password is wrong (prevents email enumeration).
     */
    public String lockedMessage(User user) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(OffsetDateTime.now())) {
            long minutesLeft = java.time.Duration.between(
                OffsetDateTime.now(), user.getLockedUntil()).toMinutes() + 1;
            return String.format(
                "This account has been temporarily locked. Try again in %d minute%s, " +
                "or contact support@smarthire.app to unlock it.",
                minutesLeft, minutesLeft == 1 ? "" : "s");
        }
        return "Incorrect email or password.";
    }
}
