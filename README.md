Nexus-Backend Service
=====================


## An Advanced and Secure RestApi Backend Service Gateway 

**The Nexus-Backend Service** acts as an intermediary between a **REST Client application** and a **Backend REST API service**.
It forwards Requests from the client to the **Backend Service** and returns the Responses back to the client.
The Nexus-Backend integrate a HttpFirewall and **WAF Filter** for a protection against evasion on the **Http Request Headers,
Request Map parameters and Json BodyRequest.**

**Inside a Servlet Container a Rest Controller ApiBackend and its BackendService, Secure and Replicate all the HTTP
Requests to a RestApi Backend Server.**

**All HttpRequests methods supported:** Get, Post, Post Multipart File, Put, Put Multipart File, Patch, Patch Multipart File, Delete.

* Full support **Request Json Entity Object**: application/json, application/x-www-form-urlencoded
* Full support **MultipartRequest Resources and Map parameters**, and embedded form **Json Entity Object**: multipart/form-data
* Full support **Response in Json Entity Object**: application/json
* Full support **Response in ByteArray Resource file**: application/octet-stream
* Full support **Streaming Http Response Json Entity Object**: application/octet-stream, accept header Range bytes

**Tomcat Servlet Containers under Servlet version 4.x**

**Examples forwarded requests and responses through the Nexus-Backend Service:**

| REST Clients          | RestApi Nexus-Backend Service                      | Backend Server Services                         |
|-----------------------|:---------------------------------------------------|:------------------------------------------------|
| Ajax / XMLHttpRequest | http://localhost:8082/nexus-backend/api/**         | https://secure.jservlet.com:9092/api/v1/service |   
| HttpClient            | https://front.jservlet.com:80/nexus-backend/api/** | https://secure.jservlet.com:9092/api/v1/service |   
| FeedService           | https://intra.jservlet.com:80/nexus-backend/api/** | https://10.100.100.50:9092/api/v1/service       |   

***An Ajax Single Page Application communicate through the Rest Controller ApiBackend and its BackendService to a RestApi Backend Server.***


### Ability to Secure all RestApi Request to a Backend Server

 * Implements a **BackendService**, ability to request typed response Object class or ParameterizedTypeReference, requested on all HTTP methods to a RestApi Backend Server.
 * Implements an **EntityError** Json object on the HttpStatus 400, 401, 405 or 500 coming from the Backend Server.
 * Implements a **HttpFirewall** filter protection against evasion, rejected any suspicious Requests, Headers, Parameters, and log IP address at fault.
 * Implements a **WAF** filter protection against evasion on the Http Json BodyRequest, and log IP address at fault.
 * Implements a **Fingerprint** for each Http header Request, generate a unique trackable Token APP-REQUEST-ID in the access logs.
 * Implements a **Method Override** PUT or PATCH request can be switched in POST or DELETE switched in GET with header X-HTTP-Method-Override
 * Implements a **Forwarded Header** filter, set removeOnly at true by default, remove "Forwarded" and "X-Forwarded-*" headers.
 * Implements a **Shallow Etag Header** filter, force Content-length in the HttpResponse, avoid header Transfer-Encoding: Chunked.
 * Implements a **Compressing** filter Gzip compression for the Http Responses.
 * Implements a **FormContent** filter, parses form data for Http PUT, PATCH, and DELETE requests and exposes it as Servlet request parameters.
 * Implements a **CharacterEncoding** filter, UTF-8 default encoding for requests.


### Specials config Http Headers

 * **HTTP headers:** reset all Headers, remove host or origin header.
 * **Basic Authentication:** set any security ACL **Access Control List**
 * **Bearer Authorization:** set any security **Bearer Token**.
 * **Cookie:** set any **security session Cookie**.
 * **CORS:** Bypass locally all **CORS Security** (Cross-origin resource sharing) from a Navigator, 
  not restricted to accessing resources from the same origin through what is known as same-origin policy.


### The Nexus Backend application can be configured by the following keys SpringBoot and Settings properties

 **SpringBoot keys application.properties**

| **Keys**                                      | **Default value** | **Descriptions**                                   |
|-----------------------------------------------|:------------------|:---------------------------------------------------|
| nexus.api.backend.enabled                     | true              | Activated the Nexus-Backend Service                |   
| nexus.api.backend.filter.waf.enabled          | true              | Activated the WAF filter Json RequestBody          |   
| nexus.api.backend.listener.requestid.enabled  | true              | Activated the Fingerprint for each Http Request    |   
| nexus.api.backend.filter.httpoverride.enabled | false             | Activated the Http Override Method                 | 
| nexus.backend.filter.forwardedHeader.enabled  | true              | Activated the ForwardedHeader filter               |   
| nexus.backend.filter.gzip.enabled             | true              | Activated the Gzip compression filter              |   
| nexus.backend.filter.cors.enabled             | true              | Activated the Cors filter                          |   
| nexus.backend.filter.shallowEtag.enabled      | false             | Activated the ShallowEtagHeader filter             | 
| spring.mvc.formcontent.filter.enabled         | true              | Activated the FormContent parameterMap Support     |   
| nexus.backend.tomcat.connector.https.enable   | false             | Activated a Connector TLS/SSL in a Embedded Tomcat | 
| nexus.backend.tomcat.accesslog.valve.enable   | false             | Activated an Accesslog in a Embedded Tomcat        | 

#### Noted the Spring config location can be overridden

* -Dspring.config.location=/your/config/dir/
* -Dspring.config.name=spring.properties


### The Nexus-Backend Url Server and miscellaneous options can be configured by the following keys Settings

 **Settings keys settings.properties**

| **Keys**                                         | **Default value**            | **Example value**               | **Descriptions**                                |
|--------------------------------------------------|:-----------------------------|:--------------------------------|:------------------------------------------------|
| **nexus.backend.url**                            | https://postman-echo.com     | https://nexus6.jservlet.com/api | The API Backend Server targeted                 |   
| **nexus.backend.uri.alive**                      | /get                         | /health/info                    | The endpoint alive Backend Server               |   
| nexus.backend.http.response.truncated            | false                        | true                            | Truncated the Json output in the logs           |   
| nexus.backend.http.response.truncated.maxLength  | 1000                         | 100                             | MaxLength truncated                             |   
| **WAF**                                          |                              |                                 |                                                 |
| nexus.api.backend.filter.waf.reactive.mode       | STRICT                       | PASSIVE                         | Default Strict HttpFirewall + Json RequestBody  |
| nexus.api.backend.filter.waf.deepscan.cookie     | false                        | true                            | Activated Deep Scan Cookie                      |
| **Headers**                                      |                              |                                 |                                                 |
| nexus.backend.header.remove                      | false                        | true                            | Remove all Headers                              |   
| nexus.backend.header.host.remove                 | false                        | false                           | Remove just host Header                         |   
| nexus.backend.header.origin.remove               | false                        | false                           | Remove just origin Header                       |   
| nexus.backend.header.cookie                      | -                            | XSession=0XX1YY2ZZ3XX4YY5ZZ6XX  | Set a Cookie Request Header                     |   
| nexus.backend.header.bearer                      | -                            | eyJhbGciO                       | Activated Bearer Authorization request          |   
| nexus.backend.header.user-agent                  | JavaNexus                    | Apache HttpClient/4.5           | User Agent header                               |
| nexus.backend.header.authorization.username      | -                            | XUsername                       | Activated Basic Authorization request           |   
| nexus.backend.header.authorization.password      | -                            | XPassword                       | "                                               |
| **Mapper**                                       |                              |                                 |                                                 |
| nexus.backend.mapper.indentOutput                | false                        | true                            | Indent Output Json                              |   
| nexus.backend.mapper.serializer.date             | yyyy-MM-dd'T'HH:mm:ss.SSS'Z' | yyyy-MM-dd'T'HH:mm:ssZZ         | Date Pattern Zulu Time: .SSS'Z', X or ZZ +00:00 |   
| nexus.backend.mapper.serializer.timezone         | -                            | Europe/Paris                    | Locale TimeZone by Default                      |   
| nexus.backend.mapper.date.timezone               | Zulu                         | Europe/Paris                    | Locale Zulu by Default                          |   
| nexus.backend.mapper.date.withColon              | true                         | true                            | Locale with Colon in TimeZone                   |   
| **Exceptions**                                   |                              |                                 |                                                 |
| nexus.backend.exception.http500                  | false                        | true                            | Activated Object mapper on a Http error 500     |   
| nexus.backend.exception.http400                  | false                        | true                            | Activated Object mapper on a Http error 400     |   
| nexus.backend.exception.http401                  | false                        | true                            | Activated Object mapper on a Http error 401     |   
| nexus.backend.exception.http405                  | false                        | true                            | Activated Object mapper on a Http error 405     |   
| **Debug**                                        |                              |                                 |                                                 |
| nexus.spring.web.security.debug                  | false                        | true                            | Debug the Spring FilterChain                    |


#### Noted the settings.properties can be overridden by a file Path config.properties
 
* **${user.home}**/conf-global/config.properties
* **${user.home}**/conf/config.properties
* **${user.home}**/cfg/**${servletContextPath}**/config.properties

### The ApiBackend Configuration Json Entity Object or a ByteArray Resource

**ApiBackend ResponseType** can be now a **ByteArray Resource.** 

**Download** any content in a **ByteArray** included **JSON, PDF, Gif, PNG, TEXT, HTML!**

The **ResourceMatchers** Config can be configured on specific ByteArray Resources path
and on specific Methods **GET, POST, PUT, PATCH** and Ant Path pattern: 

**Settings keys settings.properties**

| **Keys Methods** and **Keys Path pattern**                    | **Default value** |
|---------------------------------------------------------------|:------------------|
| nexus.backend.api-backend-resource.matchers.matchers1.method  | GET               |
| nexus.backend.api-backend-resource.matchers.matchers1.pattern | /api/encoding/**  |    
| nexus.backend.api-backend-resource.matchers.matchers2.method  | GET               |
| nexus.backend.api-backend-resource.matchers.matchers2.pattern | /api/streaming/** | 
| nexus.backend.api-backend-resource.matchers.matchers3.method  | POST              |
| nexus.backend.api-backend-resource.matchers.matchers3.pattern | /api/streaming/** | 
| nexus.backend.api-backend-resource.matchers.matchersX.method  | X Others Methods  |   
| nexus.backend.api-backend-resource.matchers.matchersX.pattern | X Others Pattern  |   

**Http Responses** are considerate as **Resources**, the Http header **"Accept-Ranges: bytes"** is injected and allow you to use
the Http header **'Range:bytes=1-100'** in the request and grabbed only range of Bytes desired. <br>
And the Http Responses didn't come back with a HttpHeader **"Transfer-Encoding: chunked"** cause the header **Content-Length**.

**Noted:** For configure **all the Responses** in **Resource** put an empty Method and use the path pattern=/api/**

| **Keys Methods** and **Keys Path pattern**                    | **Default value** |
|---------------------------------------------------------------|:------------------|
| nexus.backend.api-backend-resource.matchers.matchers1.method  |                   |
| nexus.backend.api-backend-resource.matchers.matchers1.pattern | /api/**           |

**Noted bis:** For remove the Http header **"Transfer-Encoding: chunked"** the header Content-Length need to be calculated.
enable the **ShallowEtagHeader Filter** in the configuration for force to calculate the header **Content-Length**
for all the **Response Json Entity Object**, no more HttpHeader **"Transfer-Encoding: chunked"**.

### The Nexus-Backend provides a full support MultipartRequest and Map parameters inside a form-data HttpRequest

#### MultipartConfig

**SpringBoot keys application.properties**

| **Keys**                                     | **Default value** | **Example value** | **Descriptions**    |
|----------------------------------------------|:------------------|:------------------|:--------------------|
| spring.servlet.multipart.enabled             | true              | true              | Enabled multipart   |   
| spring.servlet.multipart.file-size-threshold | 10MB              | 25MB              | File size threshold |   
| spring.servlet.multipart.max-file-size       | 15MB              | 150MB             | Max file size       |   
| spring.servlet.multipart.max-request-size    | 15MB              | 150MB             | Max request size    |   

**Noted** All the HttpRequests with a **Content-Type multipart/form-data** will be managed by a temporary **BackendResource**.
This BackendResource can convert a **MultipartFile** to a temporary **Resource**, ready to be sent to the **Backend Server**.


### The BackendService HttpFactory Client Configuration

 **Settings keys settings.properties**

| **Keys**                                            | **Default value** | **Example value** | **Descriptions**               |
|-----------------------------------------------------|:------------------|:------------------|:-------------------------------|
| nexus.backend.client.header.user-agent              | JavaNexus         | curl              | User Agent Header              |
| nexus.backend.client.connectTimeout                 | 10                | 5                 | Connection timeout in second   |
| nexus.backend.client.requestTimeout                 | 20                | 10                | Request timeout in second      |
| nexus.backend.client.socketTimeout                  | 10                | 5                 | Socket timeout in second       |
| nexus.backend.client.max_connections_per_route      | 20                | 30                | Max Connections per route      |
| nexus.backend.client.max_connections                | 100               | 300               | Max Connections in the Pool    |
| nexus.backend.client.close_idle_connections_timeout | 0                 | 0                 | Close idle connections timeout |
| nexus.backend.client.validate_after_inactivity      | 2                 | 2                 | Validate after inactivity      |
| nexus.backend.client.requestSentRetryEnabled        | false             | true              | Request Sent Retry Enabled     |
| nexus.backend.client.retryCount                     | 3                 | 2                 | Retry Count                    |
| nexus.backend.client.redirectsEnabled               | true              | true              | Redirects enabled              |
| nexus.backend.client.maxRedirects                   | 5                 | 2                 | Maximum redirections           |
| nexus.backend.client.authenticationEnabled          | false             | true              | Authentication enabled         |
| nexus.backend.client.circularRedirectsAllowed       | false             | true              | Circular redirections allowed  |


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

**Noted** the Full access logs are available with the **CommonsRequestLoggingFilter**, included the **RequestBody**.

Already initialized, activated by setting the logback.xml at **level="DEBUG"**.


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

### Available HTTP methods:
 
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
* **HttpMessageConverter** are also mandatory, StringHttp, FormHttp, ByteArrayHttp, ResourceHttp and MappingJackson2Http are the minimal.
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
* Last version **1.0.13**, released at 06/10/2024 Full support Response in ByteArray Resource and Streaming Http Response Range Bytes 
* Version **1.0.12**, released at 02/10/2024 Fix ApiBase error Message super.getResponseEntity
* Version **1.0.11**, released at 30/09/2024 Does not encode the URI template! 
* Version **1.0.10**, released at 29/09/2024 Add full support MultipartRequest content type multipart/form-data 
* Version **1.0.9**, released at 24/09/2024 Fix replicate requests ApiBackend.requestEntity
* Version **1.0.8**, released at 13/08/2024 Re-encoding HttpUrl, Special Characters are re-interpreted
* Version **1.0.7**, released at 03/08/2024 All is Bytes.
* Version **1.0.6**, released at 14/07/2024 Clarify Byte Array deserialization.
* Version **1.0.5**, released at 13/07/2024 Optimize build war/jar.
* Version **1.0.4**, released at 08/07/2024.
* Version **1.0.3**, released at 23/06/2024 Reinit project.
* Version **1.0.2** released on 28/04/2024.
* Version **1.0.1** released on 21/11/2022.
* Initial release **1.0.0** at 03/06/2021.

## Support
If you need help using Nexus-Backend Service feel free to drop an email or create an issue in GitHub.com (preferred).

## Contributions
To help **Nexus-Backend / ApiBackend / BackendService** development you are encouraged to
* Provide suggestion/feedback/Issue
* pull requests for new features
* Star :star2: the project

## License

This project is an Open Source Software released under the [GPL-3.0 license](https://github.com/javaguru/nexus-backend/blob/master/LICENSE.txt).

Copyright (c) 2001-2024 JServlet.com [Franck ANDRIANO.](http://jservlet.com)

