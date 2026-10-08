package edu.ashbrook.warden.auth;

import edu.ashbrook.warden.common.UnauthorizedException;

public class InvalidRefreshTokenException extends UnauthorizedException {

    public InvalidRefreshTokenException() {
        super("Sign in to continue.");
    }
}
