package com.washie.util;

import com.washie.model.User;
import org.springframework.stereotype.Component;

@Component
public class SessionManager {

    private User currentUser;

    public void setCurrentUser(User user) {
        this.currentUser = user;
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isLoggedIn() {
        return currentUser != null;
    }

    public boolean isAdmin() {
        return currentUser != null && currentUser.getRole() == User.Role.ADMIN;
    }

    public void logout() {
        this.currentUser = null;
    }
}
