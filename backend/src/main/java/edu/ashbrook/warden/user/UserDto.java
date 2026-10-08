package edu.ashbrook.warden.user;

/** Contract type {@code User}. Build inside a transaction: the mentor is lazily loaded. */
public record UserDto(String id, String email, String fullName, Role role, String departmentCode,
                      String departmentName, String rollNo, String mentorName) {

    public static UserDto of(User u) {
        return new UserDto(u.getId().toString(), u.getEmail(), u.getFullName(), u.getRole(), u.getDepartmentCode(),
                u.getDepartment() == null ? null : u.getDepartment().getName(), u.getRollNo(),
                u.getMentor() == null ? null : u.getMentor().getFullName());
    }
}
