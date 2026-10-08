package edu.ashbrook.warden.auth;

import edu.ashbrook.warden.user.UserDto;

public record AuthResponse(String accessToken, long expiresIn, UserDto user) {
}
