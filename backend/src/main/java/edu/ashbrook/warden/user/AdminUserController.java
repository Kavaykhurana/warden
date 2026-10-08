package edu.ashbrook.warden.user;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/api/admin/users")
    List<AdminUserDto> users() {
        return userService.adminUsers();
    }
}
