Nexus-Backend Service
=====================


## An Advanced and Secure RestApi Backend Service Gateway 

**Inside a Servlet Container a Rest Controller ApiBackend and its BackendService, Secure and Replicate all the HTTP Requests to a RestApi Backend Server.**

**Tomcat Servlet Containers under Servlet version 4.x**

| Clients               | RestApi Nexus-Backend Service                      | Backend Server Services                         |
|-----------------------|:---------------------------------------------------|:------------------------------------------------|
| Ajax / XMLHttpRequest | http://localhost:8082/nexus-backend/api/**         | https://secure.jservlet.com:9092/api/v1/service |   
| HttpClient            | https://front.jservlet.com:80/nexus-backend/api/** | https://secure.jservlet.com:9092/api/v1/service |   
| FeedService           | https://intra.jservlet.com:80/nexus-backend/api/** | https://10.100.100.50:9092/api/v1/service       |   

***An Ajax Single Page Application communicate through the Rest Controller ApiBackend and its BackendService to a RestApi Backend Server.***


### Ability to Secure all RestApi Request to a Backend Server

 * Implements a **BackendService**, ability to request typed response Object class or ParameterizedTypeReference, requested on all HTTP protocols to a RestApi Backend Server.
 * Implements an **EntityError** Json object on the HttpStatus 400, 401, 405 or 500 coming from the Backend Server.
 * Implements a **HttpFirewall** filter protection against evasion, rejected any suspicious Requests, Headers, Parameters, and log IP address at fault.
 * Implements a **WAF** filter protection against evasion on the Http Json BodyRequest, and log IP address at fault.
 * Implements a **Fingerprint** for each Http header Request, generate a unique trackable Token APP-REQUEST-ID in the access logs.
 * Implements a **Method Override** PUT or PATCH request can be switched in POST or DELETE switched in GET
 * Implements a **Forwarded Header** filter to extract values from "Forwarded" and "X-Forwarded-*" headers, wrap the request and response.
 * Implements a **Compressing** filter gzip for the Http response.
 

### Specials config Http Headers

 * **HTTP headers:** reset all Headers or remove host header.
 * **Basic Authentication:** set any security ACL.
 * **Bearer Authorization:** set any security Bearer Token.
 * **Cookie:** set any security session Cookie.
 * **CORS:** Bypass locally all **CORS Security** (Cross-origin resource sharing) from a Navigator, 
  not restricted to accessing resources from the same origin through what is known as same-origin policy.


### The Nexus Backend application can be configured by the following keys SpringBoot and Settings properties

 **SpringBoot keys application.properties**

| **Keys**                                      | **Default value** | **Descriptions**                                   |
|-----------------------------------------------|:------------------|:---------------------------------------------------|
| nexus.api.backend.enabled                     | true              | Activated the Nexus-Backend Service                |   
| nexus.api.backend.filter.waf.enabled          | true              | Activated the WAF Filter Json RequestBody          |   
| nexus.api.backend.listener.requestid.enabled  | true              | Activated the Fingerprint for each Http Request    |   
| nexus.api.backend.filter.httpoverride.enabled | false             | Activated the Http Override Method                 | 
| nexus.backend.tomcat.connector.https.enable   | false             | Activated a Connector TLS/SSL in a Embedded Tomcat | 
| nexus.backend.tomcat.accesslog.valve.enable   | false             | Activated an Accesslog in a Embedded Tomcat        | 


#### Noted the Spring config location can be overridden

* -Dspring.config.location=/your/config/dir/
* -Dspring.config.name=spring.properties
 

### The Nexus-Backend Url Server and miscellaneous options can be configured by the following keys Settings

 **Settings keys settings.properties**


| **Keys**                                     | **Default value**            | **Example value**               | **Descriptions**                                |
|----------------------------------------------|:-----------------------------|:--------------------------------|:------------------------------------------------|
| **nexus.backend.url**                        | https://postman-echo.com     | https://nexus6.jservlet.com/api | The API Backend Server targeted                 |   
| **nexus.backend.uri.alive**                  | /get                         | /health/info                    | The endpoint alive Backend Server               |   
| nexus.backend.http.response.truncated        | false                        | true                            | Truncated the Json output in the logs           |   
| **WAF**                                      |                              |                                 |                                                 |
| nexus.api.backend.filter.waf.reactive.mode   | STRICT                       | PASSIVE                         | Default Strict HttpFirewall + Json RequestBody  |
| nexus.api.backend.filter.waf.deepscan.cookie | false                        | true                            | Activated Deep Scan Cookie                      |
| **Headers**                                  |                              |                                 |                                                 |
| nexus.backend.header.remove                  | false                        | true                            | Remove all Headers                              |   
| nexus.backend.header.host.remove             | false                        | false                           | Remove just host Header                         |   
| nexus.backend.header.origin.remove           | false                        | false                           | Remove just origin Header                       |   
| nexus.backend.header.cookie                  | -                            | XSession=0XX1YY2ZZ3XX4YY5ZZ6XX  | Set a Cookie Request Header                     |   
| nexus.backend.header.bearer                  | -                            | eyJhbGciO                       | Activated Bearer Authorization request          |   
| nexus.backend.header.user-agent              | JavaNexus                    | Apache HttpClient/4.5           | User Agent header                               |
| nexus.backend.header.authorization.username  | -                            | XUsername                       | Activated Basic Authorization request           |   
| nexus.backend.header.authorization.password  | -                            | XPassword                       | "                                               |
| **Mapper**                                   |                              |                                 |                                                 |
| nexus.backend.mapper.indentOutput            | true                         | false                           | Indent Output Json                              |   
| nexus.backend.mapper.serializer.date         | yyyy-MM-dd'T'HH:mm:ss.SSS'Z' | yyyy-MM-dd'T'HH:mm:ssZZ         | Date Pattern Zulu Time: .SSS'Z', X or ZZ +00:00 |   
| nexus.backend.mapper.serializer.timezone     | -                            | Europe/Paris                    | Locale TimeZone by Default                      |   
| nexus.backend.mapper.date.timezone           | Zulu                         | Europe/Paris                    | Locale Zulu by Default                          |   
| nexus.backend.mapper.date.withColon          | true                         | true                            | Locale with Colon in TimeZone                   |   
| **Exceptions**                               |                              |                                 |                                                 |
| nexus.backend.exception.http500              | false                        | true                            | Activated Object mapper on a Http error 500     |   
| nexus.backend.exception.http400              | false                        | true                            | Activated Object mapper on a Http error 400     |   
| nexus.backend.exception.http401              | false                        | true                            | Activated Object mapper on a Http error 401     |   
| nexus.backend.exception.http405              | false                        | true                            | Activated Object mapper on a Http error 405     |   
| **Debug**                                    |                              |                                 |                                                 |
| nexus.spring.web.security.debug              | false                        | true                            | Debug the Spring FilterChain                    |


#### Noted the settings.properties can be overridden by a file Path config.properties
 
* **${user.home}**/conf-global/config.properties
* **${user.home}**/conf/config.properties
* **${user.home}**/cfg/**${servletContextPath}**/config.properties


### The BackendService HttpFactory Client Configuration

 **Settings keys settings.properties**

| **Keys**                                            | **Default value**                    | **Example value** | **Descriptions**               |
|-----------------------------------------------------|:-------------------------------------|:------------------|:-------------------------------|
| nexus.backend.client.header.user-agent              | JavaNexus                            | curl              | User Agent Header              |
| nexus.backend.client.timeout                        | 30                                   | 10                | Connection/Read/Write timeout  |
| nexus.backend.client.max_connections_per_route      | 20                                   | 30                | Max Connections per route      |
| nexus.backend.client.max_connections                | 100                                  | 300               | Max Connections in the Pool    |
| nexus.backend.client.close_idle_connections_timeout | 0                                    | 0                 | Close idle connections timeout |
| nexus.backend.client.validate_after_inactivity      | 2                                    | 2                 | Validate after inactivity      |


### The Nexus-Backend Firewall and the WAF Filter Configuration

The **Nexus-Backend** implements a **HttpFirewall** protection against evasion and rejected any suspicious Http Request 
on the Headers and Cookies, the Parameters, the keys and Values.

The **WAF Filter** implements a secure WAF protection against evasion on a **Json Http RequestBody**.

**Un-normalized** Http requests are automatically rejected by the **StrictHttpFirewall**, 
and path parameters and duplicate slashes are removed for matching purposes.

**Noted** the valid characters are defined in **RFC 7230** and **RFC 3986** are checked
by the **Apache Coyote http11 processor** (see coyote Error parsing HTTP request header)

All the Http request with **Cookies, Headers, Parameters and RequestBody** will be filtered and the suspicious **IP address** in fault will be logged.

 **Settings keys settings.properties**
 
| **Keys**                                                   | **Default value**                      | **Descriptions**                      |
|------------------------------------------------------------|:---------------------------------------|:--------------------------------------|
| nexus.backend.security.allowedHttpMethods                  | GET,POST,PUT,OPTIONS,HEAD,DELETE,PATCH | Allowed Http Methods                  |
| nexus.backend.security.allowSemicolon                      | false                                  | Allowed Semi Colon                    |                                
| nexus.backend.security.allowUrlEncodedSlash                | false                                  | Allow url encoded Slash               |                              
| nexus.backend.security.allowUrlEncodedDoubleSlash          | false                                  | Allow url encoded double Slash        |                             
| nexus.backend.security.allowUrlEncodedPeriod               | false                                  | Allow url encoded Period              |                            
| nexus.backend.security.allowBackSlash                      | false                                  | Allow BackSlash                       |                            
| nexus.backend.security.allowNull                           | false                                  | Allow Null                            |                           
| nexus.backend.security.allowUrlEncodedPercent              | false                                  | Allow url encoded Percent             |                          
| nexus.backend.security.allowUrlEncodedCarriageReturn       | false                                  | Allow url encoded Carriage Return     |                         
| nexus.backend.security.allowUrlEncodedLineFeed             | false                                  | Allow url encoded Line Feed           |                        
| nexus.backend.security.allowUrlEncodedParagraphSeparator   | false                                  | Allow url encoded Paragraph Separator |                       
| nexus.backend.security.allowUrlEncodedLineSeparator        | false                                  | Allow url encoded Line Separator      |                           

**Implements a WAF Predicate for potential evasion by Headers:**

 * HeaderNames / HeaderValues
 * ParameterNames / ParameterValues
 * Hostnames

**And check for Buffer Overflow evasion by the Length:**

 * Parameter Names/Values
 * Header Names/Values
 * Hostnames

**The WAF Utilities Predicates checked for potential evasion:**

 * XSS script injection
 * SQL injection
 * Google injection
 * Command injection
 * File injection
 * Link injection

 **The WAF Reactive mode configuration:**

 * **STRICT**:  Strict HttpFirewall + Json RequestBody
 * **PASSIVE**: Strict HttpFirewall + Clean Json RequestBody and Parameters Map
 * **UNSAFE**:  Strict HttpFirewall + No check Json RequestBody!


### Activated the Mutual Authentication or mTLS connection on the HttpFactory Client

 **Settings keys settings.properties** *nexus.backend.client.ssl.mtls.enable* at **true** for activated the mTLS connection

| **Keys**                                    | **Default value**      | **Descriptions**          |
|---------------------------------------------|:-----------------------|:--------------------------|
| nexus.backend.client.ssl.mtls.enable        | **false**              | Activated the Mutual TLS  |   
| nexus.backend.client.ssl.key-store          | nexus-default.jks      | Path to the Java KeyStore |   
| nexus.backend.client.ssl.key-store-password | changeit               | The password              |   
| nexus.backend.client.ssl.certificate.alias  | key_server             | The certificate alias     |   
| nexus.backend.client.ssl.https.protocols    | TLSv1.3                | The protocols             |   
| nexus.backend.client.ssl.https.cipherSuites | TLS_AES_256_GCM_SHA384 | The Cipher Suites         |   


### Activated Tomcat Catalina Connector TLS/SSL on a wildcard domain Certificate

 **Settings keys settings.properties**

 **SpringBoot key** *nexus.backend.tomcat.connector.https.enable* at **true** for activated the TLS/SSL protocol

| **Keys**                                              | **Default value**    | **Descriptions**          |
|-------------------------------------------------------|:---------------------|:--------------------------|
| nexus.backend.tomcat.ssl.keystore-path                | /home/root/.keystore | Path to the Java KeyStore |   
| nexus.backend.tomcat.ssl.keystore-password            | changeit             | The password              |   
| nexus.backend.tomcat.ssl.certificate.alias            | key_server           | The certificate alias     |   
| nexus.backend.tomcat.ssl.https.port                   | 8443                 | The Https port            |   
| nexus.backend.tomcat.ssl.ajp.connector.enable         | false                | Start the Ajp connector   |   
| nexus.backend.tomcat.ssl.ajp.connector.port           | 8009                 | The Ajp port              |   
| nexus.backend.tomcat.ssl.ajp.connector.protocol       | AJP/1.3              | AJP version 1.3           |   
| nexus.backend.tomcat.ssl.ajp.connector.secretRequired | false                | A secret is Required      |   


### Activated Tomcat Catalina Extended AccessLog Valve

 **Settings keys settings.properties**

 **StringBoot key** *nexus.backend.tomcat.accesslog.valve.enable* at **true** for activated the Accesslogs

| **Keys**                                      | **Default value**                                                                                                                          | **Descriptions**       |
|-----------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------|:-----------------------|
| nexus.backend.tomcat.accesslog.directory      | /tmp/logs/tomcat-nexus                                                                                                                     | Directory access log   |   
| nexus.backend.tomcat.accesslog.suffix         | .log                                                                                                                                       | The suffix             |   
| nexus.backend.tomcat.accesslog.encoding       | UTF-8                                                                                                                                      | The suffix             |   
| nexus.backend.tomcat.accesslog.pattern        | date time x-threadname c-ip cs-method cs-uri sc-status bytes x-H(contentLength) time-taken x-H(authType) cs(Authorization) cs(User-Agent)  | The pattern            |   
| nexus.backend.tomcat.accesslog.checkExists    | true                                                                                                                                       | Check if file exists   |   
| nexus.backend.tomcat.accesslog.asyncSupported | true                                                                                                                                       | Support async requests |   
| nexus.backend.tomcat.accesslog.renameOnRotate | true                                                                                                                                       | Rename on rotate       |   
| nexus.backend.tomcat.accesslog.throwOnFailure | true                                                                                                                                       | Throw on failure       |   
| nexus.backend.tomcat.accesslog.maxDay         | -1                                                                                                                                         | Max day file retention |   

**Noted** the Full access logs are available with the CommonsRequestLoggingFilter, included the RequestBody.

Already initialized, activated by setting the logback.xml at level="DEBUG".


## Build Nexus-Backend

[SpringBoot](https://projects.spring.io/spring-boot/)

### Build requirements

 * Java 13
 * SpringBoot 2.7.18
 * Tomcat 9.0.90 & Servlet 4.0.1
 * Maven 3.9.x

### Build war external Tomcat 9

with the profile withoutTomcat:

* `mvn clean compile -P withoutTomcat`
* `mvn clean package -P withoutTomcat`
* `mvn clean install -P withoutTomcat`

and look for the jar at `target/nexus-backend-{version}.war`

### Build jar embedded Tomcat 9

with the profile withTomcat:

* `mvn clean compile -P withTomcat`
* `mvn clean install -P withTomcat`
* `mvn clean package -P withTomcat`

and look for the jar at `target/nexus-backend-{version}.jar`

### Get javadoc

`mvn javadoc:javadoc`

### Run SpringBoot App

with maven:

`mvn spring-boot:run -P withTomcat`

### The Configuration

By default, it uses `8082` port and the Servlet Context `/nexus-backend`.

The default SpringBoot config is in `/src/main/resources/application.properties` file.

The default NexusBackend config in `/src/main/resources/settings.properties` file.  

The Config keys and values can be modified or override by external path files, here:

 * file `{user.home}/conf-global/config.properties`
 * file `{user.home}/conf/config.properties`
 * file `{user.home}/cfg/nexus-backend/config.properties`

### Swagger Tests environment

See RestControllerTest is in interaction with the MockController, run the tests with a local Tomcat running on localhost:8082/nexus-backend

The Swagger Mock-Api is only available in Dev mode, added in JVM Options: -Denvironment=development

## The BackendService API Implementation

This API implementation is used for the communication to a backend server.
It provides methods for all supported http protocols on the backend side. 
Normally, it communicates to an API interface Backend.

### Available HTTP protocols:
 
 * Get
 * Post
 * Post Multipart File
 * Put
 * Put Multipart File
 * Patch
 * Patch Multipart File
 * Delete
 
### Sample BackendService API

#### Prerequisites:

* **RestOperations** should be configured with an Apache-HttpClient and a Pooling connection should be properly configured.
* **HttpMessageConverter** are also mandatory, StringHttp, FormHttp, ResourceHttp, ByteArrayHttp and MappingJackson2Http are the minimal.
* **Typed Response** parameter Class Object or a ParameterizedTypeReference are mandatory
* **Object.class** cannot be converted in a Resource or ByteArray directly without a minimal support Typed Response.

#### Initialize the RestApi BackendService

```
BackendService backendService = new BackendServiceImpl();
backendService.setBackendURL("https://internal.domain.com:9094");
backendService.setRestOperations(new RestTemplate());
backendService.setObjectMapper(new ObjectMapper());
```

#### Get Data

```
Data data = backendService.get("/mock/v1/data", backendService.createResponseType(Data.class));
```

#### Get List Data

```
ResponseType<List<Data>> typeReference = backendService.createResponseType(new ParameterizedTypeReference<>(){});
List<Data> list = backendService.get("/mock/v1/dataList", typeReference);
```

#### Get Resource File

```
Resource image = backendService.getFile("/static/images/logo-marianne.svg");
FileUtils.copyInputStreamToFile(image.getInputStream(), new File(System.getProperty("java.io.tmpdir") + "/logo-marianne.svg"));
```


#### Do Request List Data
```
ResponseType<List<Data>> typeReference = backendService.createResponseType(new ParameterizedTypeReference<>(){});
Object obj = backendService.doRequest("/mock/v1/dataList", HttpMethod.GET, typeReference, null, null);
System.out.println(obj);
```

#### Do Request Resource
```
Resource resource = backendService.doRequest("/mock/v1/datafile", HttpMethod.GET,
backendService.createResponseType(Resource.class), null, headers);  // WARN mandatory typed Resource.class
String data = StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset());
System.out.println(data);
```

#### Do Request Byte Array
```
ResponseType<byte[]> typeReference = backendService.createResponseType(byte[].class)
byte[] bytes = backendService.doRequest("/mock/v1/dataBytes", HttpMethod.GET, typeReference , null, null); // WARN mandatory typed byte[].class
System.out.println(new String(bytes, StandardCharsets.UTF_8));
```


## Last News
* Last version **1.0.6**, released at 14/07/2024 Clarify Byte Array deserialization.
* Version **1.0.5**, released at 13/07/2024 Optimize build war/jar.
* Version **1.0.4**, released at 08/07/2024.
* Version **1.0.3**, released at 23/06/2024 Reinit project.
* Version **1.0.2** released on 28/04/2024.
* Version **1.0.1** released on 21/11/2022.
* Initial release **1.0.0** at 03/06/2021.

## Support
If you need help using Nexus-Backend Service feel free to drop an email or create an issue in GitHub.com (preferred).

## Contributions
To help Nexus-Backend / ApiBackend / BackendService development you are encouraged to
* Provide suggestion/feedback/Issue
* pull requests for new features
* Star :star2: the project

## License

This project is an Open Source Software released under the [GPL-3.0 license](https://github.com/javaguru/nexus-backend/blob/master/LICENSE.txt).

Copyright (c) 2001-2024 JServlet.com [Franck ANDRIANO.](http://jservlet.com)

