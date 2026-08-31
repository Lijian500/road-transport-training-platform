package me.lj.train.realtime.security;

import me.lj.train.common.security.jwt.AccessTokenClaims;
import me.lj.train.common.security.model.LoginUser;

import java.security.Principal;

/** WebSocket握手后传递给处理器的可信身份。 */
public class RealtimePrincipal implements Principal {

    private final AccessTokenClaims claims;
    private final LoginUser loginUser;
    private final String traceId;

    public RealtimePrincipal(
            AccessTokenClaims claims,
            LoginUser loginUser,
            String traceId) {
        this.claims = claims;
        this.loginUser = loginUser;
        this.traceId = traceId;
    }

    @Override
    public String getName() {
        return loginUser.getUsername();
    }

    public AccessTokenClaims getClaims() {
        return claims;
    }

    public LoginUser getLoginUser() {
        return loginUser;
    }

    public String getTraceId() {
        return traceId;
    }
}
