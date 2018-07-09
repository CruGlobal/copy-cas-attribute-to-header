package org.cru.userheader;

import static org.cru.userheader.CopyCasAttributesToHeadersFilter.ATTRIBUTE_MAPPING_PARAMETER;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.jasig.cas.client.authentication.AttributePrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

class CopyCasAttributesToHeadersFilterTest {

    private static final String EMPLOYEE_ID_ATTRIBUTE = "employeeId";

    @Mock
    FilterConfig filterConfig;

    @Mock
    HttpServletRequest containerRequest;

    @Mock
    HttpServletResponse response;

    @Mock
    FilterChain filterChain;

    @Mock
    AttributePrincipal principal;

    @Captor
    ArgumentCaptor<HttpServletRequest> filteredRequest;


    CopyCasAttributesToHeadersFilter filter = new CopyCasAttributesToHeadersFilter();

    @BeforeEach
    void initMocks() {
        MockitoAnnotations.initMocks(this);
    }


    @Nested
    @DisplayName("when configured with explicit mapping")
    class ExplicitMapping extends MappingTest {

        final String MAPPING = EMPLOYEE_ID_ATTRIBUTE + "=X-Employee-Id";

        @BeforeEach
        void initFilter() throws ServletException {
            when(filterConfig.getInitParameter(ATTRIBUTE_MAPPING_PARAMETER))
                .thenReturn(MAPPING);
            filter.init(filterConfig);
        }

        @Test
        void mappedHeaderIsCaseInsensitive() throws IOException, ServletException {
            whenLoggedInWithAttribute(EMPLOYEE_ID_ATTRIBUTE, "42");
            doFilter();
            assertAll(
                () -> assertFilteredRequestHasHeader(mappedHeader().toUpperCase(), "42"),
                () -> assertFilteredRequestHasHeader(mappedHeader().toLowerCase(), "42")
            );
        }

        @Override
        String mappedHeader() {
            return "X-Employee-Id";
        }
    }

    @Nested
    @DisplayName("when configured with implicit mapping")
    class ImplicitMapping extends MappingTest {

        @BeforeEach
        void initFilter() throws ServletException {
            when(filterConfig.getInitParameter(ATTRIBUTE_MAPPING_PARAMETER))
                .thenReturn(null);
            filter.init(filterConfig);
        }

        @Override
        String mappedHeader() {
            return "CAS_" + EMPLOYEE_ID_ATTRIBUTE;
        }
    }

    private void doFilter() throws IOException, ServletException {
        filter.doFilter(containerRequest, response, filterChain);
        verify(filterChain).doFilter(filteredRequest.capture(), eq(response));
    }

    abstract class MappingTest {

        final Set<String> unrelatedHeaders = Collections.singleton("Content-Type");

        @BeforeEach
        void mockUnrelatedHeaders() throws ServletException {
            mockContainerHeaderValue("Content-Type", "application/json");
            when(containerRequest.getHeaderNames())
                .thenAnswer(enumerate(unrelatedHeaders));
        }

        @Test
        void unmmappedHeaderIsNotOverridden() throws IOException, ServletException {
            whenLoggedInWithAttribute("employeeId", "42");
            doFilter();
            assertFilteredRequestHasHeader("Content-Type", "application/json");
        }

        @Test
        void mappedHeaderOverridesContainerHeaderWithAttribute() throws IOException, ServletException {
            whenLoggedInWithAttribute(EMPLOYEE_ID_ATTRIBUTE, "42");
            whenContainerRequestHasHeader(mappedHeader(), "007");
            doFilter();
            assertFilteredRequestHasHeader(mappedHeader(), "42");
        }

        @Test
        void mappedHeaderOverridesContainerHeaderWhenNotLoggedIn() throws IOException, ServletException {
            whenNotLoggedIn();
            whenContainerRequestHasHeader(mappedHeader(), "007");
            doFilter();
            assertFilteredRequestHasNoHeader(mappedHeader());
        }

        @Test
        void mappedHeaderOverridesContainerHeaderWhenLoggedInButNoAttributePopulated()
            throws IOException, ServletException {
            whenLoggedInWithNoAttributes();
            whenContainerRequestHasHeader(mappedHeader(), "007");
            doFilter();
            assertFilteredRequestHasNoHeader(mappedHeader());
        }

        abstract String mappedHeader();

        private void whenContainerRequestHasHeader(String headerName, String headerValue) {
            mockContainerHeaderValue(headerName, headerValue);

            Set<String> allHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            allHeaders.addAll(unrelatedHeaders);
            allHeaders.add(headerName);

            when(containerRequest.getHeaderNames()).thenAnswer(enumerate(allHeaders));
        }
    }

    private void assertFilteredRequestHasHeader(String headerName, String headerValue) {
        HttpServletRequest request = filteredRequest.getValue();

        assertAll(
            "request",
            () -> assertEquals(
                headerValue,
                request.getHeader(headerName),
                "getHeader() is " + headerValue
            ),
            () -> assertEquals(
                Collections.singletonList(headerValue),
                Collections.list(request.getHeaders(headerName)),
                "getHeaders() is [" + headerValue + "]"
            ),
            () -> assertTrue(
                caseInsensitiveHeaderNames(request).contains(headerName),
                "getHeaderNames() contains " + headerName
            )
        );
    }

    private void assertFilteredRequestHasNoHeader(String headerName) {
        HttpServletRequest request = filteredRequest.getValue();

        assertAll(
            "request",
            () -> assertNull(request.getHeader(headerName), "getHeader() is null"),
            () -> assertFalse(request.getHeaders(headerName).hasMoreElements(), "getHeaders() is empty"),
            () -> assertFalse(caseInsensitiveHeaderNames(request).contains(headerName), "getHeaderNames() doesn't contain " + headerName)
        );
    }

    private Set<String> caseInsensitiveHeaderNames(HttpServletRequest request) {
        return Collections.list(request.getHeaderNames())
            .stream()
            .collect(Collectors.toCollection(() -> new TreeSet<>(String.CASE_INSENSITIVE_ORDER)));
    }

    private void mockContainerHeaderValue(String headerName, String headerValue) {
        when(containerRequest.getHeader(headerName)).thenReturn(headerValue);
        when(containerRequest.getHeaders(headerName)).thenAnswer(enumerate(Collections.singletonList(headerValue)));
    }

    // Mock calls that retur an enumeration need to use thenAnswer(),
    // because the enumeration can only be used once.
    // Each call to to the mock needs to return a new enumeration.
    private Answer<Enumeration<String>> enumerate(Collection<String> headerValues) {
        return invocation -> Collections.enumeration(headerValues);
    }

    private void whenNotLoggedIn() {
        when(containerRequest.getUserPrincipal()).thenReturn(null);
    }

    private void whenLoggedInWithAttribute(String attributeName, String attributeValue) {
        when(containerRequest.getUserPrincipal()).thenReturn(principal);
        when(principal.getAttributes()).thenReturn(Collections.singletonMap(attributeName, attributeValue));
    }

    private void whenLoggedInWithNoAttributes() {
        when(containerRequest.getUserPrincipal()).thenReturn(principal);
        when(principal.getAttributes()).thenReturn(Collections.emptyMap());
    }


    @Test
    void convertNullAttribute() {
        assertEquals(Collections.emptyList(), filter.convertAttributeValue(null));
    }

    @Test
    void convertStringAttribute() {
        assertEquals(Collections.singletonList("foo"), filter.convertAttributeValue("foo"));
    }

    @Test
    void convertStringCollectionAttribute() {
        List<String> value = Arrays.asList("foo", "bar");
        assertEquals(value, filter.convertAttributeValue(value));
    }

    @Test
    void convertNonCollectionAttribute() {
        assertEquals(Collections.singletonList("foo"), filter.convertAttributeValue(new Foo()));
    }

    @Test
    void convertNonStringCollectionAttribute() {
        List<Foo> value = Arrays.asList(new Foo(), new Foo());
        assertEquals(Arrays.asList("foo", "foo"), filter.convertAttributeValue(value));
    }

    @Test
    void convertCollectionAttributeWithNullValues() {
        List<String> value = Arrays.asList(null, "foo");
        assertEquals(Collections.singletonList("foo"), filter.convertAttributeValue(value));
    }

    class Foo {

        @Override
        public String toString() {
            return "foo";
        }
    }
}
