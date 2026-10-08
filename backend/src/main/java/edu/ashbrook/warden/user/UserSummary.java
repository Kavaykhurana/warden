package edu.ashbrook.warden.user;

public record UserSummary(String id, String fullName, Role role) {

    public static UserSummary of(User user) {
        return user == null ? null : new UserSummary(user.getId().toString(), user.getFullName(), user.getRole());
    }
}
