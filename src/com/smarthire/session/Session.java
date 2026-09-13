package com.smarthire.session;

import com.smarthire.model.User;

/**
 * Holds the currently logged-in user for the lifetime of the console session.
 * Replaces the JWT-based authentication of the original web version -
 * in a single-terminal app the "session" is just the current process state.
 */
public class Session {

    private User currentUser;

    public void login(User user) {
        this.currentUser = user;
    }

    public void logout() {
        this.currentUser = null;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public User getCurrentUser() {
        return currentUser;
    }
}
