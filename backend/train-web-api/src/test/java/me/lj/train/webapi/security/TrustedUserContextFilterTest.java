package me.lj.train.webapi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import me.lj.train.api.admin.AdminModels.LoginAccount;
import me.lj.train.common.core.result.AppErrorCode;
import me.lj.train.common.security.SecurityConstants;
import me.lj.train.common.security.context.UserContext;
import me.lj.train.common.security.model.LoginUser;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TrustedUserContextFilterTest {

    private final AuthorizationFacade authorizationFacade = mock(AuthorizationFacade.class);
    private final TrustedUserContextFilter filter =
            new TrustedUserContextFilter(authorizationFacade, new ObjectMapper());

    @Test
    void shouldRejectProtectedRequestWithoutTrustedHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains(AppErrorCode.UNAUTHORIZED.getCode());
    }

    @Test
    void shouldSetAndAlwaysClearUserContext() throws Exception {
        LoginAccount account = new LoginAccount(
                10L,
                20L,
                "tester",
                "测试用户",
                "测试企业",
                3L,
                false,
                false,
                List.of("ADMIN"),
                List.of("admin:user:view"));
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(10L);
        loginUser.setEnterpriseId(20L);
        loginUser.setLoginVersion(3L);
        when(authorizationFacade.load(10L)).thenReturn(account);
        when(authorizationFacade.toLoginUser(account, "session-1")).thenReturn(loginUser);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.addHeader(SecurityConstants.HEADER_USER_ID, "10");
        request.addHeader(SecurityConstants.HEADER_LOGIN_VERSION, "3");
        request.addHeader(SecurityConstants.HEADER_SESSION_ID, "session-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean contextVisible = new AtomicBoolean();
        FilterChain chain = (servletRequest, servletResponse) ->
                contextVisible.set(UserContext.require().getUserId().equals(10L));

        filter.doFilter(request, response, chain);

        assertThat(contextVisible).isTrue();
        assertThat(UserContext.get()).isNull();
    }
}
