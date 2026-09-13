package com.smarthire.service;

import com.smarthire.model.User;
import com.smarthire.model.UserRole;
import com.smarthire.repository.UserRepository;
import com.smarthire.util.PasswordUtil;
import com.smarthire.util.Validator;

import java.util.List;

public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User register(String name, String email, String password, UserRole role) {
        if (!Validator.isNonEmpty(name)) throw new SmartHireException("Name cannot be empty.");
        if (!Validator.isValidEmail(email)) throw new SmartHireException("Invalid email address.");
        if (!Validator.isValidPassword(password)) throw new SmartHireException("Password must be at least 6 characters.");
        if (userRepository.findByEmail(email).isPresent()) throw new SmartHireException("An account with this email already exists.");

        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hash(password, salt);
        User user = new User(userRepository.nextId(), name, email, hash, salt, role);
        userRepository.save(user);
        return user;
    }

    public User login(String email, String password) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new SmartHireException("No account found with that email."));
        if (!PasswordUtil.verify(password, user.getSalt(), user.getPasswordHash())) {
            throw new SmartHireException("Incorrect password.");
        }
        return user;
    }

    /**
     * Returns the updated User so the caller can refresh its in-memory session copy -
     * the User object passed in still has the old hash/salt and must not keep being used
     * for further auth checks in the same session.
     */
    public User changePassword(User user, String oldPassword, String newPassword) {
        if (!PasswordUtil.verify(oldPassword, user.getSalt(), user.getPasswordHash())) {
            throw new SmartHireException("Current password is incorrect.");
        }
        if (!Validator.isValidPassword(newPassword)) {
            throw new SmartHireException("New password must be at least 6 characters.");
        }
        String newSalt = PasswordUtil.generateSalt();
        String newHash = PasswordUtil.hash(newPassword, newSalt);
        User updated = new User(user.getId(), user.getName(), user.getEmail(), newHash, newSalt, user.getRole(), user.getSkills());
        userRepository.save(updated);
        return updated;
    }

    /** Updates a candidate's saved skill profile. Returns the updated User to refresh the session with. */
    public User updateSkills(User user, List<String> skills) {
        User updated = new User(user.getId(), user.getName(), user.getEmail(),
                user.getPasswordHash(), user.getSalt(), user.getRole(), skills);
        userRepository.save(updated);
        return updated;
    }
}
