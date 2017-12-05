package org.cru.userheader;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

/**
 * "Copies" the request {@link HttpServletRequest#getRemoteUser() user} to the configured header.
 * The header name is defined by the filter init-param {@code headerName}.
 * If the parameter is absent or empty, the header name defaults to 'X-Remote-User'.
 *
 * @author Matt Drees
 */
public class CopyUserToHeaderFilter implements Filter {

    private static final String HEADER_NAME_PARAMETER = "headerName";
    private static final String DEFAULT_HEADER_NAME = "X-Remote-User";

    private String headerName;

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        String headerName = filterConfig.getInitParameter(HEADER_NAME_PARAMETER);
        if (headerName != null && !headerName.isEmpty()) {
            this.headerName = headerName;
        } else {
            this.headerName = DEFAULT_HEADER_NAME;
        }
    }

    @Override
    public void doFilter(
        final ServletRequest servletRequest,
        final ServletResponse servletResponse,
        final FilterChain filterChain
    ) throws IOException, ServletException {

        ServletRequest possiblyWrappedRequest = wrapServletRequestIfNecessary(servletRequest);
        filterChain.doFilter(possiblyWrappedRequest, servletResponse);
    }

    private ServletRequest wrapServletRequestIfNecessary(ServletRequest servletRequest) {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            if (httpServletRequest.getRemoteUser() != null) {
                return new CopyUserToHeaderWrapper(httpServletRequest);
            } else {
                return httpServletRequest;
            }
        } else {
            return servletRequest;
        }
    }

    private class CopyUserToHeaderWrapper extends HttpServletRequestWrapper {

        public CopyUserToHeaderWrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Enumeration<String> originalHeaderNames = super.getHeaderNames();
            if (originalHeaderNames == null) {
                // container does not allow the header names to be enumerated
                return null;
            }
            Set<String> allHeaderNames = new LinkedHashSet<>(Collections.list(originalHeaderNames));
            allHeaderNames.add(headerName);
            return Collections.enumeration(allHeaderNames);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (name.equalsIgnoreCase(headerName)) {
                return Collections.enumeration(Collections.singleton(getRemoteUser()));
            } else {
                return super.getHeaders(name);
            }
        }

        @Override
        public String getHeader(String name) {
            if (name.equalsIgnoreCase(headerName)) {
                return getRemoteUser();
            } else {
                return super.getHeader(name);
            }
        }

    }

    @Override
    public void destroy() {
    }

}
