# What?

A small simple servlet filter that copies attributes from the request's principal
to configurable set of headers.

# Why?

Some tools demand one or more headers be populated with certain user attributes
when integrating with an external authentication mechanism, like CAS.
The CAS client provides an AttributePrincipal that can be used to look up various user attributes,
but these are not stored in headers by default.
This filter makes (some of) those attributes available as headers.


# How?

Using maven?
Build this repo (`mvn install`), and add this dependency to your pom:

```xml
  <dependency>
    <groupId>org.cru.casheader</groupId>
    <artifactId>copy-cas-attribute-to-header</artifactId>
    <version>1</version>
  </dependency>
```

Not using maven?
Grab the jar from
[github](https://github.com/CruGlobal/copy-cas-attribute-to-header/releases/tag/1)
and get it into your WEB-INF/lib directory.

Add this filter to your web.xml:
```xml
  <filter>

     <!-- adapt as you'd like -->
    <filter-name>Copy Cas Attributes to Headers Filter</filter-name>

    <filter-class>org.cru.userheader.CopyCasAttributesToHeadersFilter</filter-class>

    <!--
      Optional; default behavior will map all attributes,
      mapping each attribute to a header of the same name prefixed by "CAS_".
     -->
    <init-param>
        <param-name>attributeMapping</param-name>

        <!-- A whitespace-separated list of attribute=header mappings -->
        <param-value>
          someCasAttribute=A-Specific-Header-Used-By-Your-Tool
          someOtherAttribute=Some-Other-Header
        </param-value>
    </init-param>
  </filter>

  <filter-mapping>
      <!-- match the <filter-name> above -->
      <filter-name>Copy Cas Attributes to Headers Filter</filter-name>
      
      <!-- adapt as required; this is probably good enough for most applications -->
      <url-pattern>/*</url-pattern>
  </filter-mapping>
```

Any Collection attributes will be converted to multiple headers.
Any non-String attributes will be converted to Strings using their `toString()` method.

Note: if an attribute mapping is not specified,
the header names will not be case-insensitive, as header names often are expected to be.
# Misc

This was built to integrate Cru's peoplesoft instances with our CAS server.
It's a quick glue project, and will probably never see a deployment to maven central.
