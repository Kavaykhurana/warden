package edu.ashbrook.warden.user;

import java.time.OffsetDateTime;

import edu.ashbrook.warden.config.ClockConfig;

/** Contract type {@code AdminUser} ({@code User} plus {@code active} and {@code createdAt}). */
public record AdminUserDto(String id, String email, String fullName, Role role, String departmentCode,
                           String departmentName, String rollNo, String mentorName, boolean active,
                           OffsetDateTime createdAt) {

    static AdminUserDto of(User u) {
        UserDto base = UserDto.of(u);
        return new AdminUserDto(base.id(), base.email(), base.fullName(), base.role(), base.departmentCode(),
                base.departmentName(), base.rollNo(), base.mentorName(), u.isActive(), ClockConfig.ist(u.getCreatedAt()));
    }
}
