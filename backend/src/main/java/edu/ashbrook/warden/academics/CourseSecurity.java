package edu.ashbrook.warden.academics;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import edu.ashbrook.warden.user.CurrentUser;
import edu.ashbrook.warden.user.Role;
import edu.ashbrook.warden.user.User;
import edu.ashbrook.warden.user.UserRepository;

/** Course-level checks referenced from {@code @PreAuthorize}. Reads the database, never trusts token claims for scope. */
@Component("courseSecurity")
public class CourseSecurity {

    private final CourseRepository courses;
    private final UserRepository users;

    public CourseSecurity(CourseRepository courses, UserRepository users) {
        this.courses = courses;
        this.users = users;
    }

    /** The course's faculty, the HOD of the course's department, or any ADMIN. */
    @Transactional(readOnly = true)
    public boolean canViewRoster(String code, Authentication authentication) {
        User user = users.findById(CurrentUser.idOf(authentication)).filter(User::isActive).orElse(null);
        if (user == null) {
            return false;
        }
        if (user.getRole() == Role.ADMIN) {
            return true;
        }
        return courses.findById(code)
                .map(c -> c.getFaculty().getId().equals(user.getId()) || user.isHodOf(c.getDepartmentCode()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean teaches(String code, Authentication authentication) {
        return courses.findById(code)
                .map(c -> c.getFaculty().getId().equals(CurrentUser.idOf(authentication)))
                .orElse(false);
    }
}
