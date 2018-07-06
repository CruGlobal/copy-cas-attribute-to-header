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
    <filter-name>Copy User to Header Filter</filter-name>

    <filter-class>org.cru.userheader.CopyUserToHeaderFilter</filter-class>

    <!-- optional; default is 'X-Remote-User' -->
    <init-param>
        <param-name>headerName</param-name>

        <!-- use whatever header name your application expects -->
        <param-value>Some-User-Header-Name</param-value>
    </init-param>
  </filter>

  <filter-mapping>
      <!-- match the <filter-name> above -->
      <filter-name>Copy User to Header Filter</filter-name>
      
      <!-- adapt as required; this is probably good enough for most applications -->
      <url-pattern>/*</url-pattern>
  </filter-mapping>
```


# Misc

This was built to integrate Cru's peoplesoft instances with our CAS server.
It's a quick glue project, and will probably never see a version 2 or deployment to maven central.
