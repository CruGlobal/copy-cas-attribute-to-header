package org.cru.userheader;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.jasig.cas.client.authentication.AttributePrincipal;

/**
 * "Copies" CAS attributes from the request's Principal to a set of configured headers.
 * The mapping is defined by the filter init-param {@code attributeMapping}.
 *
 *
 * @author Matt Drees
 */
public class CopyCasAttributesToHeadersFilter implements Filter {

    static final String ATTRIBUTE_MAPPING_PARAMETER = "attributeMapping";
    private static final String LINE_SEPARATOR = "=";
    private static final String CAS_PREFIX = "CAS_";

    private AttributeMapping attributeMapping;

    interface AttributeMapping {
        public boolean headerIsMapped(String headerName);

        public String getAttributeNameForHeader(String headerName);

        public Set<String> getHeaderNamesForAttribute(String attributeName);
    }

    @Override
    public void init(final FilterConfig filterConfig) throws ServletException {
        String mapping = filterConfig.getInitParameter(ATTRIBUTE_MAPPING_PARAMETER);
        if (mapping != null && !mapping.isEmpty()) {
            this.attributeMapping = parseAttributeMapping(mapping);
        } else {
            this.attributeMapping = new AttributeMapping() {
                @Override
                public boolean headerIsMapped(String headerName) {
                    // case-insensitive "startsWith"
                    return headerName.regionMatches(true, 0, CAS_PREFIX, 0, CAS_PREFIX.length());
                }

                @Override
                public String getAttributeNameForHeader(String headerName) {
                    return headerName.substring(CAS_PREFIX.length());
                }

                @Override
                public Set<String> getHeaderNamesForAttribute(String attributeName) {
                    return Collections.singleton(CAS_PREFIX + attributeName);
                }
            };
        }
    }

    private AttributeMapping parseAttributeMapping(String mapping) {
        Map<String, String> headersToAttributeNames = parseHeadersToAttributeNames(mapping);
        Map<String, Set<String>> attributeNamesToHeaders = reverseMap(headersToAttributeNames);

        return new AttributeMapping() {
            @Override
            public boolean headerIsMapped(String headerName) {
                return headersToAttributeNames.containsKey(headerName);
            }

            @Override
            public String getAttributeNameForHeader(String headerName) {
                return headersToAttributeNames.get(headerName);
            }

            @Override
            public Set<String> getHeaderNamesForAttribute(String attributeName) {
                Set<String> headerNames = attributeNamesToHeaders.get(attributeName);
                if (headerNames != null) {
                    return headerNames;
                } else {
                    return Collections.emptySet();
                }
            }
        };
    }

    private Map<String, String> parseHeadersToAttributeNames(String mapping) {
        return Stream.of(mapping.split("\\s"))
                .filter(string -> !string.isEmpty())
                .peek(string -> {
                    if (!string.contains(LINE_SEPARATOR)) {
                        throw new IllegalArgumentException("bad mapping line: " + string);
                    }
                })
                .collect(Collectors.toMap(
                    line -> line.split(LINE_SEPARATOR)[1],
                    line -> line.split(LINE_SEPARATOR)[0],
                    throwingMergeFunction(),
                    () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
                ));
    }

    // borrowed from Collections.throwingMerger(), which is private
    private <T> BinaryOperator<T> throwingMergeFunction() {
        return (u, v) -> { throw new IllegalStateException(String.format("Duplicate key %s", u)); };
    }

    private Map<String, Set<String>> reverseMap(Map<String, String> headersToAttributeNames) {
        return headersToAttributeNames.entrySet()
            .stream()
            .collect(Collectors.toMap(
                Entry::getValue,
                entry -> Collections.singleton(entry.getKey()),
                this::mergeSets
            ));
    }

    private Set<String> mergeSets(Set<String> set1, Set<String> set2) {
        return Stream.of(set1, set2)
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    @Override
    public void doFilter(
        final ServletRequest servletRequest,
        final ServletResponse servletResponse,
        final FilterChain filterChain
    ) throws IOException, ServletException {

        ServletRequest possiblyWrappedRequest = wrapServletRequestIfPossible(servletRequest);
        filterChain.doFilter(possiblyWrappedRequest, servletResponse);
    }

    private ServletRequest wrapServletRequestIfPossible(ServletRequest servletRequest) {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
            return new Wrapper(httpServletRequest);
        } else {
            return servletRequest;
        }
    }

    private class Wrapper extends HttpServletRequestWrapper {

        public Wrapper(HttpServletRequest request) {
            super(request);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Enumeration<String> originalHeaderNames = super.getHeaderNames();
            if (originalHeaderNames == null) {
                // container does not allow the header names to be enumerated
                return null;
            }
            Set<String> allHeaderNames = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            allHeaderNames.addAll(Collections.list(originalHeaderNames));
            allHeaderNames.removeIf(name -> attributeMapping.headerIsMapped(name));
            allHeaderNames.addAll(getAttributeHeaderNames());
            return Collections.enumeration(allHeaderNames);
        }

        private Set<String> getAttributeHeaderNames() {
            return getAttributes().entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .map(entry -> attributeMapping.getHeaderNamesForAttribute(entry.getKey()))
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (attributeMapping.headerIsMapped(name)) {
                return Collections.enumeration(getHeaderValues(name));
            } else {
                return super.getHeaders(name);
            }
        }

        @Override
        public String getHeader(String name) {
            if (attributeMapping.headerIsMapped(name)) {
                return getFirstOrElseNull(getHeaderValues(name));
            } else {
                return super.getHeader(name);
            }
        }

        private List<String> getHeaderValues(String name) {
            String attributeName = attributeMapping.getAttributeNameForHeader(name);
            Object value = getAttributes().get(attributeName);
            return convertAttributeValue(value);
        }

        private Map<String, Object> getAttributes() {
            AttributePrincipal principal = (AttributePrincipal) super.getUserPrincipal();
            if (principal != null) {
                return principal.getAttributes();
            } else {
                return Collections.emptyMap();
            }
        }

        private String getFirstOrElseNull(List<String> values) {
            if (values.isEmpty()) {
                return null;
            } else {
                return values.get(0);
            }
        }
    }

    //@VisibleForTesting
    List<String> convertAttributeValue(Object value) {
        if (value == null) {
            return Collections.emptyList();
        } else if (value instanceof Collection<?>) {
            Collection<?> collection = (Collection<?>) value;
            return collection.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toList());
        } else {
            return Collections.singletonList(value.toString());
        }
    }


    @Override
    public void destroy() {
    }

}
