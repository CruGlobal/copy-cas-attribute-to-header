# What?

A small simple servlet filter that copies the request's "remote user" value to a configurable header.

# Why?

Some tools (like Siebel) demand a header be populated with the user's username,
when integrating with an external authentication mechanism.

# How?

Using maven?
Build this repo (`mvn install`), and add this dependency to your pom:

```xml
  <dependency>
    <groupId>org.cru.userheader</groupId>
    <artifactId>copy-user-to-header</artifactId>
    <version>1</version>
  </dependency>
```

Not using maven?
Grab the jar from
[github](https://github.com/CruGlobal/copy-user-to-header/releases/tag/1)
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

This was built to integrate Cru's siebel instance with our CAS server.
It's a quick glue project, and will probably never see a version 2 or deployment to maven central.
