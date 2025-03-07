/**
 * SPDX-FileCopyrightText: 2018-2022 SAP SE or an SAP affiliate company and Cloud Security Client Java contributors
 * 
 * SPDX-License-Identifier: Apache-2.0
 */
package com.sap.cloud.security.samples;

import com.sap.cloud.security.client.HttpClientFactory;
import com.sap.cloud.security.servlet.AbstractTokenAuthenticator;
import com.sap.cloud.security.servlet.TokenAuthenticationResult;
import com.sap.cloud.security.servlet.XsuaaTokenAuthenticator;
import com.sap.cloud.security.token.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.annotation.WebFilter;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;

import static com.sap.cloud.security.token.TokenClaims.USER_NAME;

@WebFilter("/*") // filter for any endpoint
public class XsuaaSecurityFilter implements Filter {
	private static final Logger LOGGER = LoggerFactory.getLogger(XsuaaSecurityFilter.class);
	private final AbstractTokenAuthenticator xsuaaTokenAuthenticator;

	public XsuaaSecurityFilter() {
		// in productive usage never use a default rest client!
		xsuaaTokenAuthenticator = new XsuaaTokenAuthenticator().withHttpClient(HttpClientFactory.create(null));
	}

	@Override
	public void init(FilterConfig filterConfig) {
	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		try {
			TokenAuthenticationResult authenticationResult = xsuaaTokenAuthenticator.validateRequest(request, response);
			if (authenticationResult.isAuthenticated()) {
				LOGGER.debug("AUTHENTICATED");
				chain.doFilter(request, response);
			} else {
				LOGGER.debug("UNAUTHENTICATED");
				sendUnauthenticatedResponse(response, authenticationResult.getUnauthenticatedReason());
			}
		} finally {
			SecurityContext.clear();
		}
	}

	private void sendUnauthenticatedResponse(ServletResponse response, String unauthenticatedReason)  {
		if (response instanceof HttpServletResponse) {
			try {
				HttpServletResponse httpServletResponse = (HttpServletResponse) response;
				httpServletResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, unauthenticatedReason); // 401
			} catch (IOException e) {
				LOGGER.error("Failed to send error response", e);
			}
		}
	}

	public static void sendUnauthorizedResponse(ServletResponse response, String missingScope)  {
		if (response instanceof HttpServletResponse) {
			try {
				String user = Objects.nonNull(SecurityContext.getAccessToken()) ? SecurityContext.getToken().getClaimAsString(USER_NAME) : "<Unknown>";
				HttpServletResponse httpServletResponse = (HttpServletResponse) response;
				LOGGER.error("User {} is unauthorized. User does not have scope {}.", user, missingScope);
				httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "User " + user + " is unauthorized."); // 403
			} catch (IOException e) {
				LOGGER.error("Failed to send error response", e);
			}
		}
	}

	@Override
	public void destroy() {
	}
}
