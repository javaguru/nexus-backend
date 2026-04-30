Nexus-Backend Service
=====================

## 🛡️ Advanced API Gateway with NLP-Driven Threat Detection

**Nexus-Backend Service** acts as a highly secure reverse-proxy and intermediary gateway between REST clients and backend 
services. It intercepts, analyzes, and securely forwards HTTP requests while providing enterprise-grade protection against
modern web vulnerabilities.

At the core of the Nexus-Backend is a next-generation **AI WAF Engine**. Instead of relying on static Regex rules, 
it utilizes a quantized Machine Learning model (DistilBERT) to understand the *semantics* of the traffic.

**Key Security Features:**
* **Deep Payload Inspection:** Real-time semantic analysis of HTTP Headers, URL Parameters, and massive JSON bodies.
* **Sliding Window Chunking:** Safely processes infinite JSON payloads with mathematical overlap and Early-Exit architecture.
* **Zero-Day & Evasion Resistance:** Immune to spatial biases, complex obfuscation, and novel syntax injections.
* **High Performance:** Operates entirely locally via JNI/ONNX (C++) within the Servlet Container, delivering sub-50ms inference times with strict native memory tracking.

Nexus ensures that only sanitized, perfectly safe traffic ever reaches your Backend REST API.

**All HttpRequests methods supported:** Get, Post, Post Multipart File, Put, Put Multipart File, Patch, Patch Multipart File, Delete.

* Full support **Request JSON Entity Object**: application/json, application/x-www-form-urlencoded.
* Full support **MultipartRequest Resources and Map parameters**, and embedded form **Json Entity Object**: multipart/form-data.
* Full support **Response in JSON Entity Object**: application/json.
* Full support **Response in ByteArray Resource file**: application/octet-stream.
* Full support **Streaming Http Response JSON Entity Object**: application/octet-stream, accept header Range bytes.
* Full support **Cookie managed** during a redirection Http status 3xx.  

**🐱 Tomcat Servlet Containers under Servlet Specification version 6.x**

### 🛸 Nexus-Backend Services functionalities implemented

* Implements a **BackendService** Ability to request typed response Object class or ParameterizedTypeReference, requested a Rest-Api Backend Server.
  * Features an **EntityBackend** JSON Object or Resource, transfer back headers, manage error HttpStatus 400, 401, 405 or 500 from the Backend Server.
* Implements a **WEB HttpFirewall** Filter protection against evasion, rejected any suspicious Requests Encoding and log IP address at fault
* Implements a **WAF Filter** Predicate Patterns protection against evasion on the Http JSON BodyRequest, Headers, Keys, Parameters and log IP address at fault.
* Implements a **Nano AI DistilBERT ONNX** Nexus AI WAF Engine detect malicious Http requests and log IP address at fault.
  * Features a **AnalyzerRequest** Analyze content text with a pure Sequential Sliding Window.
  * Features a **Native Memory Tracking Monitoring** NMT: System calls, Tracks the actual RAM footprint, allowing jcmd to generate reports.
* **Rate Limiting:** Implements a per-IP rate limit interceptor using **Bucket4j** and **JCache**, allowing a maximum of 1,000 requests per minute to protect against abuse and DDoS attacks. Returns `429 Too Many Requests` when the quota is exceeded.
* **Circuit Breaker & Retry:** Implements **Resilience4j** to protect downstream backend communications.
  * Features a **Retry** mechanism (3 attempts max) for transient network glitches.
  * Features a **Circuit Breaker** that fails fast and blocks requests if the backend error rate exceeds 30%, preventing cascading failures.
  * Provides a graceful fallback returning a formatted `503 Service Unavailable` JSON response.
* **Observability:** Both the Circuit Breaker and the Rate Limiter states are exposed in real-time via **Spring Boot Actuator** (`/actuator/health`, `/actuator/circuitbreakers`).
* Implements a **Fingerprint** Each Http header Request generate a unique trackable Token APP-REQUEST-ID in the access logs.
* Implements a **HttpMethodOverride** Filter, PUT or PATCH request can be switched in POST or DELETE switched in GET with header X-HTTP-Method-Override.
* Implements a **CORS Security Request** Filter, authorize request based on Origin Domains and Methods.
* Implements a **Content Security Policy** Filter define your own policy rules CSP.
* Implements a **ForwardedHeader** filter, set removeOnly at true by default, remove "Forwarded" and "X-Forwarded-*" headers.
* Implements a **FormContent** filter, parses form data for Http PUT, PATCH, and DELETE requests and exposes it as Servlet request parameters.
* Implements a **Compressing** filter Gzip compression for the Http Responses.
* Implements a **CharacterEncoding** filter, UTF-8 default encoding for requests.


**Examples forwarded requests and responses through the Nexus-Backend Service:**

| REST Clients          | RestApi Nexus-Backend Service                      | Backend Server Services                         |
|-----------------------|:---------------------------------------------------|:------------------------------------------------|
| Ajax / XMLHttpRequest | http://localhost:8082/nexus-backend/api/**         | https://secure.jservlet.com:9092/api/v1/service |   
| HttpClient            | https://front.jservlet.com:80/nexus-backend/api/** | https://secure.jservlet.com:9092/api/v1/service |   
| FeedService           | https://intra.jservlet.com:80/nexus-backend/api/** | https://10.100.100.50:9092/api/v1/service       |   

***An Ajax Single Page Application communicate through the Rest Controller ApiBackend and its BackendService to a RestApi Backend Server.***


## 🛠️ Build Nexus-Backend

### ✅ Build requirements

* **Java >= 21 (Virtual Threads)** [Java](https://jdk.java.net/archive/)
* **SpringBoot 3.3.0** [SpringBoot](https://projects.spring.io/spring-boot/)
* **Apache Tomcat 10.0.54 & Servlet 6.0.0 (Jakarta)** [Tomcat](https://tomcat.apache.org/download-10.cgi)
* **Apache Maven >= 3.9.3** [Maven](https://maven.apache.org/download.cgi)


### 🏭 Build WAR Tomcat 10.xx (Embedded or External Tomcat)

Maven clean, compile, package or install:

* `mvn clean compile`
* `mvn clean package `
* `mvn clean install`

and look for the war at `target/nexus-backend-{version}.war`


### 📋 Get the Javadoc

`mvn javadoc:javadoc`


### 🔥 Run SpringBoot App

The prerequisite is to define the JAVA_HOME environment variable points to the directory containing the JRE (Java Runtime Environment) of JDK 21:
Set the path folder of your JDK folder:

"set JAVA_HOME={Path_folder_JDK}\jdk-21.0.1"

Maven clean and package nexus-backend (also-make multi-module and skip Tests):

`mvn clean package -pl nexus-backend -am -DskipTests`

Maven clean and install nexus-backend:

`mvn clean install`

- With Java, Embedded Tomcat and custom embedded Tomcat Container (Profile withTomcat) and NativeMemoryTracking:

`java -Dspring.profiles.active=withTomcat -XX:NativeMemoryTracking=summary -jar nexus-backend\target\nexus-backend.war`

- With Java, Development environment, Embedded Tomcat and  custom embedded Tomcat Container, and NativeMemoryTracking:

`java -Denvironment=development -Dspring.profiles.active=withTomcat -XX:NativeMemoryTracking=summary -jar nexus-backend\target\nexus-backend.war`

💡 Note: The Tomcat profile enables a custom Embedded Tomcat Container with Security Constraints (equivalent to a web.xml file)
and loads a predefined embedded tomcat-user.xml file. I recommend externalizing your own tomcat-user.xml file, see next part the configuration.

- With Java and a Java KeyStore SSL TrustStore, Development environment, Embedded Tomcat and  custom embedded Tomcat Container, and NativeMemoryTracking:

If you are using the Tomcat HTTPS connector, the JKS file must be filled with the certificate of the SSL domain postman-echo.com or others to communicate via the HTTP TLS/SSL protocol.

`java -Djavax.net.ssl.trustStore=/root/.keystore -Djavax.net.ssl.trustStorePassword=changeit -Denvironment=development -Dspring.profiles.active=withTomcat -XX:NativeMemoryTracking=summary -jar nexus-backend\target\nexus-backend.war`

💡 Note: Enable the Tomcat connector HTTPS and configure the SSL port 8443 in the config 

- With Maven, we need change dir to /nexus-backend (prerequisite compile the project):

`cd nexus-backend`

And run Spring-boot (-XX:NativeMemoryTracking=summary for monitored Native Memory Tracking for ONNX Neural Network):

`mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Denvironment=development -Dspring.profiles.active=withTomcat -XX:NativeMemoryTracking=summary"`


### 🚀 The Nexus-Backend Configuration

By default, it uses `8082` port and the Servlet Context is `/nexus-backend`.

The default SpringBoot config is in `/src/main/resources/application.properties` file.

The default NexusBackend config in `/src/main/resources/settings.properties` file.

The Config keys and values can be modified and override by external path files, here:

* file `{user.home}/conf-global/config.properties`
* file `{user.home}/conf/config.properties`
* file `{user.home}/cfg/{appName}/config.properties`

### 💡 Specials config Http Headers

 * **HTTP headers:** reset all Headers, remove Host or Origin header.
 * **Basic Authentication:** set any security ACL **Access Control List**
 * **Bearer Authorization:** set any security **Bearer Token**.
 * **Cookie:** set any **security session Cookie**.
 * **CORS:** Bypass locally all **CORS Security** (Cross-origin resource sharing) from a Navigator, 
  not restricted to accessing resources from the same origin through what is known as same-origin policy.


### ⚙️ The Nexus Backend application can be configured by the following keys SpringBoot and Settings properties

 **SpringBoot keys application.properties:**

| **Keys**                                         | **Default value** | **Descriptions**                                   |
|--------------------------------------------------|:------------------|:---------------------------------------------------|
| nexus.api.backend.enabled                        | true              | Activated the Nexus-Backend Service                |   
| nexus.api.backend.filter.waf.enabled             | true              | Activated the WAF filter Json RequestBody          |   
| nexus.api.backend.listener.requestid.enabled     | true              | Activated the Fingerprint for each Http Request    |   
| nexus.api.backend.filter.httpoverride.enabled    | true              | Activated the Http Override Method                 | 
| nexus.api.backend.interceptor.ratelimit.enabled  | true              | Activated the RateLimit                            | 
| nexus.backend.filter.forwardedHeader.enabled     | true              | Activated the ForwardedHeader filter               |   
| nexus.backend.filter.gzip.enabled                | true              | Activated the Gzip compression filter              |   
| spring.mvc.formcontent.filter.enabled            | true              | Activated the FormContent parameterMap Support     |   
| nexus.backend.tomcat.connector.https.enable      | false             | Activated a Connector TLS/SSL in a Embedded Tomcat | 
| nexus.backend.tomcat.accesslog.valve.enable      | false             | Activated an Accesslog in a Embedded Tomcat        | 

#### 💡 Noted the Spring config location can be overridden

* -Dspring.config.location=/your/config/dir/
* -Dspring.config.name=spring.properties


### ⚙️ The Nexus-Backend Url Server and miscellaneous options can be configured by the following keys Settings

 **Settings keys settings.properties:**

| **Keys**                                        | **Default value**        | **Example value**               | **Descriptions**                                     |
|-------------------------------------------------|:-------------------------|:--------------------------------|:-----------------------------------------------------|
| **nexus.backend.url**                           | https://postman-echo.com | https://nexus6.jservlet.com/api | The API Backend<br/> Server targeted                 |
| **nexus.backend.uri.alive**                     | /get                     | /health/info                    | The endpoint alive <br/>Backend Server               |
| nexus.backend.http.response.truncated           | false                    | true                            | Truncated the Json<br/> output in the logs           |
| nexus.backend.http.response.truncated.maxLength | 1000                     | 100                             | MaxLength truncated                                  |
| **WAF**                                         |                          |                                 |                                                      |
| nexus.api.backend.filter.waf.reactive.mode      | STRICT_ONNX_AI           | ONNX_AI                         | Default Strict HttpFirewall <br/>+ AI Neural Network |
| nexus.api.backend.filter.waf.deepscan.cookie    | false                    | true                            | Activated Deep Scan Cookie                           |
| nexus.api.backend.filter.waf.incidents.path     |                          | /var/log/nexus/waf_audits/      | The Path folder audits incidents with the payload    |
| **Headers**                                     |                          |                                 |                                                      |
| nexus.backend.header.remove                     | **true**                 | true                            | Remove all Headers                                   |
| nexus.backend.header.host.remove                | false                    | false                           | Remove just host Header                              |
| nexus.backend.header.origin.remove              | false                    | false                           | Remove just origin Header                            |
| nexus.backend.header.cookie                     | -                        | XSession=0XX1YY2ZZ3XX4YY5ZZ6XX  | Set a Cookie Request Header                          |
| nexus.backend.header.bearer                     | -                        | eyJhbGciO                       | Activated Bearer <br/>Authorization request          |
| nexus.backend.header.user-agent                 | JavaNexus                | Apache HttpClient/4.5           | User Agent header                                    |
| nexus.backend.header.authorization.username     | -                        | XUsername                       | Activated Basic <br/>Authorization request           |
| nexus.backend.header.authorization.password     | -                        | XPassword                       | "                                                    |
| **Backend Headers**                             |                          |                                 |                                                      |
| nexus.api.backend.transfer.headers              | test                     | test,Link,Content-Range         | Headers list back<br/>from Backend Server            |
| **Mapper**                                      |                          |                                 |                                                      |
| nexus.backend.mapper.indentOutput               | false                    | true                            | Indent Output Json                                   |
| **Debug**                                       |                          |                                 |                                                      |
| nexus.spring.web.security.debug                 | false                    | true                            | Debug the Spring FilterChain                         |

**Noted**: About the list HttpHeaders transfer back, the CORS can expose these Headers see key security.cors.exposedHeaders


#### Noted the settings.properties can be overridden by a file Path config.properties
 
* **${user.home}**/conf-global/config.properties
* **${user.home}**/conf/config.properties
* **${user.home}**/cfg/**${servletContextPath}**/config.properties


### 🛡️ The Nexus-Backend Firewall and the WAF Filter Configuration

The **Nexus-Backend** implements a **Web Application Firewall**. The **WAF Filter** protection against evasion and rejected
any suspicious Http Request on the Headers, Cookies, Parameters, the Keys or Values and **Json Http RequestBody**.

**Un-normalized** Http requests are automatically rejected by the **WebHttpFirewall**,
and path parameters and duplicate slashes are removed for matching purposes.

**💡 Noted** the valid characters are defined in **RFC 7230** and **RFC 3986** are checked
by the **Apache Coyote http11 processor** (see coyote Error parsing HTTP request header)

All the Http request with **Cookies, Headers, Parameters and RequestBody** will be filtered and the suspicious
**IP address** in fault will be logged.

**Settings keys settings.properties:**

| **Keys**                                                   | **Default value**                           | **Descriptions**                      |
|------------------------------------------------------------|:--------------------------------------------|:--------------------------------------|
| nexus.backend.security.allowedHttpMethods                  | GET,POST,PUT,OPTIONS,<br/>HEAD,DELETE,PATCH | Allowed Http Methods                  |
| nexus.backend.security.allowSemicolon                      | false                                       | Allowed Semi Colon                    |                                
| nexus.backend.security.allowUrlEncodedSlash                | false                                       | Allow url encoded Slash               |                              
| nexus.backend.security.allowUrlEncodedDoubleSlash          | false                                       | Allow url encoded double Slash        |                             
| nexus.backend.security.allowUrlEncodedPeriod               | false                                       | Allow url encoded Period              |                            
| nexus.backend.security.allowBackSlash                      | false                                       | Allow BackSlash                       |                            
| nexus.backend.security.allowNull                           | false                                       | Allow Null                            |                           
| nexus.backend.security.allowUrlEncodedPercent              | false                                       | Allow url encoded Percent             |                          
| nexus.backend.security.allowUrlEncodedCarriageReturn       | false                                       | Allow url encoded Carriage Return     |                         
| nexus.backend.security.allowUrlEncodedLineFeed             | false                                       | Allow url encoded Line Feed           |                        
| nexus.backend.security.allowUrlEncodedParagraphSeparator   | false                                       | Allow url encoded Paragraph Separator |                       
| nexus.backend.security.allowUrlEncodedLineSeparator        | false                                       | Allow url encoded Line Separator      |                           

**🎯 The WAF Utilities Predicates checked for potential evasion:**

* XSS script injection
* SQL injection
* Google injection
* Command injection
* File injection
* Link injection

**📃 Implements a WAF Predicate for potential evasion by Headers or Parameters:**

* Header Names / Header Values
* Parameter Names / Parameter Values
* Hostnames
* UserAgent
* AI UserAgent

**⚙️ And check for Buffer Overflow evasion by the Length:**

* Parameter Names 255 characters max. / Values 1000000 characters max.
* Header Names 255 characters max. / Values 25000 characters max.
* Hostnames 255 characters max.

**🛡️ The WAF Reactive mode configuration:**

* **STRICT_ONNX_AI**:  STRICT HttpFirewall + Artificial Intelligence Scan by ONNX Neural Network
* **ONNX_AI**:  Artificial Intelligence Scan by ONNX Neural Network
* **STRICT**:  Strict HttpFirewall + JSON RequestBody
* **PASSIVE**: Strict HttpFirewall + Clean JSON RequestBody and Parameters Map
* **UNSAFE**:  Strict HttpFirewall + No check JSON RequestBody!

**🛸 The AI Model ONNX**

The AnalyzerRequestService analyze content text with a pure Sequential Sliding Window(Performance <50 ms).
Use **DistilBERT Model ONNX Environment** an **Open Neural Network Exchange** with **HuggingFace Tokenizer**.

**Recommendations:**
- 4-6 Cpu max recommended
- Strict limits to prevent DoS via massive payloads **~6500 Tokens maximum for 15 Chunks**
- Calibrated threshold:
  - 0.50 = Very strict WAF (blocks at the slightest doubt)
  - 0.65 = Balanced WAF (tolerates noise, blocks real attacks)
  - 0.85 = Permissive WAF (only blocks absolutely obvious threats)

**Settings keys settings.properties:** Define file model and tokenizer

| **Keys**                                       | **Default value**            | **Descriptions**   |
|------------------------------------------------|:-----------------------------|:-------------------|
| nexus.api.backend.analyzer.onnx.maxLength      | 512                          | Length max Token   |   
| nexus.api.backend.analyzer.onnx.truncation     | false                        | Truncation         |   
| nexus.api.backend.analyzer.onnx.path.model     | model/nexus_v10_14_int8.onnx | AI Model ONNX INT8 |   
| nexus.api.backend.analyzer.onnx.path.tokenizer | model/tokenizer.json         | File Tokenizer     |   
| nexus.api.backend.analyzer.onnx.cpu            | 4                            | Number of CPU      |   

**⚔️ Web HttpFirewall**

**Settings keys settings.properties:** Define a max length for Keys/Values Headers or Parameters

| **Keys**                                                 | **Default value** | **Descriptions**                |
|----------------------------------------------------------|:------------------|:--------------------------------|
| nexus.backend.security.predicate.parameterNamesLength    | 255               | Parameter names length max      |   
| nexus.backend.security.predicate.parameterValuesLength   | 1000000           | Parameter values length max     |   
| nexus.backend.security.predicate.headerNamesLength       | 255               | Header names length max         |   
| nexus.backend.security.predicate.headerNamesValuesLength | 25000             | Header values length max        |   
| nexus.backend.security.predicate.hostNamesLength         | 255               | Host names length max           |   
| nexus.backend.security.predicate.hostName.pattern        |                   | Hostname pattern filter         |   
| nexus.backend.security.predicate.userAgent.blocked       | false             | Active Scanner UserAgent filter |   
| nexus.backend.security.predicate.aiUserAgent.blocked     | true              | Active AI UserAgent filter      |   

### ⚙️ The CircuitBreaker Resilient4j Configuration

**Settings keys settings.properties:**

The default CircuitBreaker Configuration:

| **Resilience4j Configuration**                       | **Default value** | **Example value** | **Descriptions**       |
|------------------------------------------------------|:------------------|:------------------|:-----------------------|
| **CircuitBreaker Config**                            |                   |                   |                        |  
| nexus.backend.circuitbreaker.failureRateThreshold    | 30                | 10                | % failure rate exceeds |  
| nexus.backend.circuitbreaker.minimumNumberOfCalls    | 20                | 5                 |                        |  
| nexus.backend.circuitbreaker.waitDurationInOpenState | 30000             | 5000              | Fail Fast 30s          |  
| nexus.backend.circuitbreaker.slidingWindowSize       | 20                | 20                |                        |  
| nexus.backend.circuitbreaker.permittedNumberOfCalls  | 3                 | 5                 |                        |  
| **Retry Config**                                     |                   |                   |                        |  
| nexus.backend.circuitbreaker.retry.maxAttempts       | 3                 | 5                 | 3 test requests        |  
| nexus.backend.circuitbreaker.retry.waitDuration      | 1000              | 500               | Wait duration in ms    |  


### ⚙️ The RateLimit Configuration

**Rate limit** 1000 per minutes and per-IP-address.

**SpringBoot key** *nexus.api.backend.interceptor.ratelimit.enabled* at **true** for activated the RateLimit.

**Settings keys settings.properties:**

The default RateLimit Configuration:

| **RateLimit Configuration**                           | **Default value** | **Example value** | **Descriptions** |
|-------------------------------------------------------|:------------------|:------------------|:-----------------|
| nexus.backend.interceptor.ratelimit.refillToken       | 1000              | 600               | Filled tokens    |  
| nexus.backend.interceptor.ratelimit.refillMinutes     | 1                 | 1                 | Duration minutes |  
| nexus.backend.interceptor.ratelimit.bandwidthCapacity | 1000              | 600               | Bucket capacity  |  


### 🛡️ System Resilience & Traffic Control

To ensure high availability and protect both our application and downstream services, this project implements advanced traffic control and fault tolerance mechanisms.

#### 🚦 Rate Limiting (Bucket4j)

API endpoints are protected by a custom `RateLimitInterceptor` using **Bucket4j** backed by a thread-safe **JCache**.
* **Rule:** 1,000 requests / minute / IP Address.
* **Behavior:** If a client exceeds the quota, the request is intercepted and immediately rejected with a `429 Too Many Requests` standard JSON response.

#### 🔌 Circuit Breaker & Retry (Resilience4j)

Outbound HTTP calls to the external backend are wrapped in a **Resilience4j** decorator chain.

* **1. Retry Mechanism:** * Automatically retries failed requests up to **3 times** with a **1-second delay** between attempts.
  * Skips retrying on definitive client errors (e.g., `404 Not Found`).
* **2. Circuit Breaker (`nexusBackendService`):**
  * **Threshold:** Opens if the failure rate exceeds **30%** over a sliding window of **20 calls**.
  * **Open State:** Instantly blocks new requests (Fail Fast) for **30 seconds** to let the backend recover, returning a graceful `503 Service Unavailable` response.
  * **Half-Open State:** Allows exactly **3 test requests** to pass through to determine if the backend has recovered before fully closing the circuit again.

#### 📊 Observability & Metrics

The state of our resilience components is fully integrated with **Spring Boot Actuator**:
* `/actuator/health` or `/actuator/health/custom`: Provide global health status (rateLimit, circuitBreakers, diskSpace, ping, livenessState and readinessState).
* `/actuator/health/custom/bucket4jRateLimiter`: Provides real-time status RateLimit Bucket4j (status OPEN, HALF-OPEN, CLOSE).
* `/actuator/health`: Displays the global health, including custom indicators for the JCache storage and the Circuit Breaker status.
* `/actuator/circuitbreakers`: Provides real-time metrics (failure rate, buffered calls, state transitions).

**Automatic schema of request flows**

```mermaid
sequenceDiagram
    participant Client
    participant RateLimiter as RateLimit (Bucket4j)
    participant Retry as Retry (3 attempts)
    participant CB as Circuit Breaker (30% threshold)
    participant Backend

    Client->>RateLimiter: HTTP Request
    alt Quota Exceeded
        RateLimiter-->>Client: 429 Too Many Requests
    else Quota OK
        RateLimiter->>Retry: Forward Request
        Retry->>CB: Execute Call
        alt Circuit is OPEN
            CB-->>Client: 503 Service Unavailable (Fallback)
        else Circuit is CLOSED
            CB->>Backend: HTTP GET/POST
            Backend-->>CB: Response
            CB-->>Retry: Success or Error
            Retry-->>Client: Final Response
        end
    end
```


### ⚙️️ The ApiBackend Configuration JSON Entity Object or a ByteArray Resource

**ApiBackend ResponseType** can be now a **ByteArray Resource.** 

**Download** any content in a **ByteArray** included commons extensions files (see **[MediaTypes](#The-MediaTypes-safe-extensions-configuration)** section)  

The **ResourceMatchers** Config can be configured on specific ByteArray Resources path
and on specific Methods **GET, POST, PUT, PATCH** and Ant Path pattern: 

**Settings keys settings.properties:**

| **Keys Methods** and **Keys Path pattern**                    | **Default value**      | **Content-Type**         |
|---------------------------------------------------------------|:-----------------------|:-------------------------|
| nexus.backend.api-backend-resource.matchers.1.method          | GET                    |                          |
| nexus.backend.api-backend-resource.matchers.1.pattern         | /api/encoding/**       | text/html;charset=utf-8  |   
| nexus.backend.api-backend-resource.matchers.2.method          | GET                    |                          |
| nexus.backend.api-backend-resource.matchers.2.pattern         | /api/streaming/**      | application/octet-stream |
| nexus.backend.api-backend-resource.matchers.3.method          | GET                    |                          |
| nexus.backend.api-backend-resource.matchers.3.pattern         | /api/time/now          | text/html;charset=utf-8  |
| nexus.backend.api-backend-resource.matchers.{name}[X].method  | Methods                |                          |  
| nexus.backend.api-backend-resource.matchers.{name}[X].pattern | Patterns               |                          | 

**Http Responses** are considerate as **Resources**, the Http header **"Accept-Ranges: bytes"** is injected and allow you
to use the Http header **'Range: bytes=1-100'** in the request and grabbed only range of Bytes desired. 

And the Http Responses didn't come back with a HttpHeader **"Transfer-Encoding: chunked"** cause the header **Content-Length**.


**💡 Noted:** For configure **all the Responses** in **Resource** put an empty Method and use the path pattern=/api/**

| **Keys Methods** and **Keys Path pattern**                    | **Default value** |
|---------------------------------------------------------------|:------------------|
| nexus.backend.api-backend-resource.matchers.matchers1.method  |                   |
| nexus.backend.api-backend-resource.matchers.matchers1.pattern | /api/**           |

**💡 Noted bis:** For remove the Http header **"Transfer-Encoding: chunked"** the header Content-Length need to be calculated.

Enable the **ShallowEtagHeader Filter** in the configuration for force to calculate the header **Content-Length**
for all the **Response JSON Entity Object**, no more HttpHeader **"Transfer-Encoding: chunked"**.


### ⚙️ The MediaTypes safe extensions configuration

**MediaTypes safe extensions**

The Spring ContentNegotiation load the safe extensions files that can be extended.
A commons MediaTypes properties file is loaded [resources/mime/MediaTypes_commons.properties](https://github.com/javaguru/nexus-backend/blob/master/src/main/resources/mime/MediaTypes_commons.properties)
and can be disabled:

**Settings keys settings.properties:**

Default Header ContentNegotiation Strategy:

| **ContentNegotiation Strategy**                               | **Default value** | **Descriptions Strategy**   |
|---------------------------------------------------------------|:------------------|:----------------------------|
| **Header Strategy**                                           |                   |                             | 
| nexus.backend.content.negotiation.ignoreAcceptHeader          | false             | Header Strategy Enabled     |  
| **Parameter Strategy**                                        |                   |                             | 
| nexus.backend.content.negotiation.favorParameter              | false             | Parameter Strategy Disabled |   
| nexus.backend.content.negotiation.parameterName               | mediaType         |                             |  
| **Registered Extensions**                                     |                   |                             |  
| nexus.backend.content.negotiation.useRegisteredExtensionsOnly | true              | Registered Only Enabled     |   
| **Load commons MediaTypes**                                   |                   |                             |  
| nexus.backend.content.negotiation.commonMediaTypes            | true              | Enabled                     |   


### ⚙️ The CORS Security configuration

**CORS Security configuration, allow Control Request on Domains and Methods**

**Settings keys settings.properties:**

The default Cors Configuration:

| **Cors Configuration**                            | **Default value**                                                          | **Example value**                                  | **Descriptions**       |
|---------------------------------------------------|:---------------------------------------------------------------------------|:---------------------------------------------------|:-----------------------|
| nexus.backend.security.cors.credentials           | false                                                                      | true                                               | Enable credentials     |  
| nexus.backend.security.cors.allowedHttpMethods    | GET,POST,PUT<br/>,OPTIONS,HEAD,<br/>DELETE,PATCH                           | GET,POST,PUT,OPTIONS                               | List Http Methods      |  
| nexus.backend.security.cors.allowedOriginPatterns |                                                                            |                                                    | Regex Patterns domains |  
| nexus.backend.security.cors.allowedOrigins        | *                                                                          | http://localhost:4042,<br/>http://localhost:4083   | List domains           |  
| nexus.backend.security.cors.allowedHeaders        | Authorization,Cache-Control,<br/>Content-Type,<br/>X-Requested-With,Accept | Authorization,<br/>Cache-Control,<br/>Content-Type | List Allowed Headers   |  
| nexus.backend.security.cors.exposedHeaders        |                                                                            | Link,X-Custom-Header                               | List Exposed Headers   |  
| nexus.backend.security.cors.maxAge                | 3600                                                                       | 1800                                               | Max Age cached         |  

**💡 Noted:** allowedOrigins cannot be a wildcard '*' if credentials is at true, a list of domains need to be provided. 


### ⚙️ The Nexus-Backend provides a full support MultipartRequest and Map parameters inside a form-data HttpRequest

#### MultipartConfig

**SpringBoot keys application.properties:**

| **Keys**                                     | **Default value** | **Example value** | **Descriptions**    |
|----------------------------------------------|:------------------|:------------------|:--------------------|
| spring.servlet.multipart.enabled             | true              | true              | Enabled multipart   |   
| spring.servlet.multipart.file-size-threshold | 2MB               | 5MB               | File size threshold |   
| spring.servlet.multipart.max-file-size       | 15MB              | 150MB             | Max file size       |   
| spring.servlet.multipart.max-request-size    | 15MB              | 150MB             | Max request size    |   

**💡 Noted** All the HttpRequests with a **Content-Type multipart/form-data** will be managed by a temporary **BackendResource**.

~~This BackendResource can convert a **MultipartFile** to a temporary **Resource**, ready to be sent to the **Backend Server**.~~

Since the version **1.0.24** no more BackendResource and temporary file. **All is in memory**.  


### 🏭 The BackendService HttpFactory Client Configuration

 **Settings keys settings.properties:**

| **Keys**                                            | **Default value** | **Example value** | **Descriptions**                  |
|-----------------------------------------------------|:------------------|:------------------|:----------------------------------|
| nexus.backend.client.header.user-agent              | JavaNexus         | curl              | User Agent Header                 |
| nexus.backend.client.connectTimeout                 | 10                | 5                 | Connection timeout in second      |
| nexus.backend.client.requestTimeout                 | 20                | 10                | Request timeout in second         |
| nexus.backend.client.socketTimeout                  | 10                | 5                 | Socket timeout in second          |
| nexus.backend.client.max_connections_per_route      | 20                | 30                | Max Connections per route         |
| nexus.backend.client.max_connections                | 100               | 300               | Max Connections in the Pool       |
| nexus.backend.client.close_idle_connections_timeout | 0                 | 0                 | Close idle connections timeout    |
| nexus.backend.client.validate_after_inactivity      | 2                 | 2                 | Validate after inactivity         |
| nexus.backend.client.retryCount                     | 3                 | 0                 | Retry Count                       |
| nexus.backend.client.redirectsEnabled               | true              | true              | Redirects enabled                 |
| nexus.backend.client.maxRedirects                   | 5                 | 2                 | Maximum redirections              |
| nexus.backend.client.authenticationEnabled          | false             | true              | Authentication enabled            |
| nexus.backend.client.circularRedirectsAllowed       | false             | true              | Circular redirections allowed     |
| nexus.backend.client.cookie.domain                  |                   | postman-echo.com  | Domain cookie or empty or #{null} |
| nexus.backend.client.cookie.secure                  | false             | true              | Remove secure cookie              |


### ⚙️ Activated the Mutual Authentication or mTLS connection on the HttpFactory Client

**Settings keys settings.properties:** *nexus.backend.client.ssl.mtls.enable* at **true** for activated the mTLS connection

| **Keys**                                    | **Default value**      | **Descriptions**          |
|---------------------------------------------|:-----------------------|:--------------------------|
| nexus.backend.client.ssl.mtls.enable        | **false**              | Activated the Mutual TLS  |   
| nexus.backend.client.ssl.key-store          | nexus-default.jks      | Path to the Java KeyStore |   
| nexus.backend.client.ssl.key-store-password | changeit               | The password              |   
| nexus.backend.client.ssl.certificate.alias  | key_server             | The certificate alias     |   
| nexus.backend.client.ssl.https.protocols    | TLSv1.3                | The protocols             |   
| nexus.backend.client.ssl.https.cipherSuites | TLS_AES_256_GCM_SHA384 | The Cipher Suites         |  


### ⚙️ Tomcat 10.xx Embedded with external configuration

**Settings keys settings.properties:** 

| **Keys**                                          | **Default value**         | **Descriptions**                              |
|---------------------------------------------------|:--------------------------|:----------------------------------------------|
| nexus.backend.tomcat.security.gui.roles           | **admin-gui**             | Roles Admin GUI                               |   
| nexus.backend.tomcat.security.patterns            | /actuator/*,/mnt/admin/** | Pattern match paths actuator                  |   
| nexus.backend.tomcat.security.health.roles        | **admin-health**          | Roles Health status                           |   
| nexus.backend.tomcat.security.health.patterns     | /health/*                 | Pattern match paths                           |   
| nexus.backend.tomcat.security.users.file          |                           | /apps/apache-tomcat/conf/tomcat-users.xml     |   


### ⚙️ Tomcat Executor

**Settings keys settings.properties:**

| **Keys**                                          | **Default value**         | **Descriptions**                              |
|---------------------------------------------------|:--------------------------|:----------------------------------------------|
| nexus.backend.tomcat.executor.maxThreads          | 300                       | Max Thread Excecution                         |   
| nexus.backend.tomcat.executor.minSpareThreads     | 4                         | Min Thread Excecution                         |   


### ⚙️ Tomcat Realm lock config

**Settings keys settings.properties:**

| **Keys**                                          | **Default value**         | **Descriptions**                              |
|---------------------------------------------------|:--------------------------|:----------------------------------------------|
| nexus.backend.tomcat.acl.realm.lock.failureCount  | 5                         | Number failure count maximum                  |   
| nexus.backend.tomcat.acl.realm.lock.lockOutTime   | 300                       | Lock out time in second (300s lock 5 minutes) |   


### ⚙️ Activated Tomcat Catalina Connector TLS/SSL on a wildcard domain Certificate

 **Settings keys settings.properties:**

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


### ⚙️ Activated Tomcat Catalina Extended AccessLog Valve

 **Settings keys settings.properties:**

 **SpringBoot key** *nexus.backend.tomcat.accesslog.valve.enable* at **true** for activated the Accesslogs

| **Keys**                                      | **Default value**                                                                                                                                  | **Descriptions**       |
|-----------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------------------|:-----------------------|
| nexus.backend.tomcat.accesslog.directory      | /tmp/logs/tomcat-nexus                                                                                                                             | Directory access log   |   
| nexus.backend.tomcat.accesslog.suffix         | .log                                                                                                                                               | The suffix             |   
| nexus.backend.tomcat.accesslog.encoding       | UTF-8                                                                                                                                              | The suffix             |   
| nexus.backend.tomcat.accesslog.pattern        | date time x-threadname c-ip cs-method cs-uri <br/>sc-status bytes x-H(contentLength)time-taken <br/>x-H(authType) cs(Authorization) cs(User-Agent) | The pattern            |   
| nexus.backend.tomcat.accesslog.checkExists    | true                                                                                                                                               | Check if file exists   |   
| nexus.backend.tomcat.accesslog.asyncSupported | true                                                                                                                                               | Support async requests |   
| nexus.backend.tomcat.accesslog.renameOnRotate | true                                                                                                                                               | Rename on rotate       |   
| nexus.backend.tomcat.accesslog.throwOnFailure | true                                                                                                                                               | Throw on failure       |   
| nexus.backend.tomcat.accesslog.maxDay         | -1                                                                                                                                                 | Max day file retention |   

**💡 Noted** the Full access logs are available with the **CommonsRequestLoggingFilter**, included the **RequestBody**.

Already initialized, activated by setting the logback.xml at **level="DEBUG"**.


### 🐱 The Default Tomcat 10.xx Configuration

**Default Custom Tomcat Container**

**💡 Noted: I recommend to override the config for Embedded Tomcat 10.xx**

- External Files config **tomcat-users.xml**:

```
# External config tomcat-users
nexus.backend.tomcat.security.users.file=/apps/apache-tomcat-10.1.54/conf/tomcat-users.xml
```

**Load Tomcat Users from Catalina Base Config or Catalina Home Config**

- catalina.base + "conf/tomcat-users.xml"
- catalina.home + "conf/tomcat-users.xml"

**Default Tomcat Security Constraints and Realm Users**

- Memory Realm Encapsulate in a LockOutRealm (**Protection Brute Force**)
  - FailureCount: 5 failed max
  - Realm LockOutTime: block 300s

- Default ACL (**Access Restrict List**):
  - Authorization Method: "BASIC"
  - Realm Name: "Nexus Backend Realm"

- Default Users Name:
  - **admin-gui**
  - **admin-health**

- Default Name Access Constraint, Allowed Authority and Security Path:

```
Create Security Constraint : Nexus Admin Access Constraint
- Path Security : /actuator/*
- Path Security : /nmt/*
- Role Authority : admin-gui
Create Security Constraint : Nexus Health Access Constraint
- Path Security : /health/*
- Role Authority : admin-health
- Role Authority : admin-gui
```

- Default HTTP Connector:
  - acceptCount=100, 
  - connectionTimeout=20000, 
  - maxPostSize=10485760, 
  - disableUploadTimeout=true,
  - compression=on,  
  - compressableMimeType=text/html,text/xml,text/plain,text/javascript,text/css,application/json, 
  - uriEncoding=UTF-8,
  - maxHttpHeaderSize=10240, 
  - rejectIllegalHeader=true, 
  - serverHeader=git di nexus a

**Default Catalina ErrorReport Valve**

- showReport at false 
- ShowServerInfo at false

**Default Catalina HealthCheck Valve**

- Path: /health


### 💹 Swagger Tests environment

See RestControllerTest is in interaction with the MockController, run the tests with a local Tomcat running on localhost:8082/nexus-backend

The Swagger Mock-Api is only available in Dev mode, added in JVM Options: -Denvironment=development


### 🛰️ Monitor MNT - Native Memory Tracking

See NmtMonitorService is in interaction with the NmtController to reports the Native Memory Tracking:  

- Native: System calls (malloc/mmap) in C++, ONNX model.
- Memory: Tracks the actual RAM footprint.
- Tracking: The -XX:NativeMemoryTracking=summary option enables internal instrumentation, allowing jcmd to generate reports.

Lists the instrumented Java Virtual Machines (JVMs) on the target system, see [Doc Oracle JPS](https://docs.oracle.com/javase/8/docs/technotes/tools/unix/jps.html).

The Nexus-Backend J Monitor MNT is only available in Role **gui-admin**.

```
===============================================================
NEXUS WAF - NMT REPORT (PID: 25768)
===============================================================
Category                  | Reserved (MB)   | Committed (MB)
--------------------------|-----------------|------------------
Total (Global)            | 9655,13      MB | 440,09       MB
--------------------------|-----------------|------------------
Java Heap                 | 8156,00      MB | 200,00       MB
Class                     | 1026,53      MB | 11,53        MB
Thread                    | 51,14        MB | 2,86         MB
Code                      | 49,89        MB | 19,41        MB
GC                        | 209,49       MB | 54,09        MB
GCCardSet                 | 0,10         MB | 0,10         MB
Compiler                  | 0,15         MB | 0,15         MB
Internal (ONNX AI)        | 49,26        MB | 49,26        MB
Other                     | 0,01         MB | 0,01         MB
Symbol                    | 21,86        MB | 21,86        MB
Native Memory Tracking    | 5,65         MB | 5,65         MB
Shared class space        | 16,00        MB | 12,56        MB
Arena Chunk               | 1,25         MB | 1,25         MB
Module                    | 0,27         MB | 0,27         MB
Safepoint                 | 0,01         MB | 0,01         MB
Synchronization           | 0,91         MB | 0,91         MB
Serviceability            | 2,30         MB | 2,30         MB
Metaspace                 | 64,29        MB | 57,85        MB
String Deduplication      | 0,00         MB | 0,00         MB
===============================================================
```

## 🚀 The BackendService API Implementation

This API implementation is used for the communication to a backend server.
It provides methods for all supported http protocols on the backend side. 
Normally, it communicates to an API interface Backend.

### 📃 Available HTTP methods:
 
 * Get
 * Post
 * Post Multipart File
 * Put
 * Put Multipart File
 * Patch
 * Patch Multipart File
 * Delete
 
### 📃 Samples BackendService API

#### ☣️ Prerequisites:

* **RestOperations** should be configured with an Apache-HttpClient5 and a Pooling connection should be properly configured.
* **HttpMessageConverter** are also mandatory, StringHttp, FormHttp, ByteArrayHttp, ResourceHttp and MappingJackson2Http are the minimal.
* **Typed Response** parameter Class Object or a ParameterizedTypeReference are mandatory
* **Object.class** cannot be converted in a Resource or ByteArray directly without a minimal support Typed Response.

####  Initialize the RestApi BackendService

New instance BackendService:

```
BackendService backendService = new BackendServiceImpl();
backendService.setConfig(new BackendConfigProperties());
backendService.setBackendURL("https://postman-echo.com");
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


## 🗒️ Last News

* Last version **2.0.2**, released at 26/04/2026 Stabilization migration Spring 6, New Resilience4j Circuit Breaker, Fix AnalyzerRequestService Sequential Sliding Window and ONNX Tensor Padding.
* Version **2.0.1**, released at 19/04/2026 Fix EnvironmentPostProcessor, RequestAnalyzerService ResourceLoader, Build WAR external/internal.
* Version **2.0.0**, released at 18/04/2026  **Jdk 21, Spring 6, Spring Boot 3.3.0, Tomcat 10.xx Servlet Specification 6.0**:
 Modern WAF Filter Defense, Next-GEN AI WAF Engine, Fine-Tuning DistilBERT Model ONNX.

**Version < 1.0.27 under Jdk 13, Spring 5, Spring Boot 2.7.18, Tomcat 9.xx Servlet Specification 4.0.1**
 
* Version **1.0.26**, Last release in Spring 5 - SpringBoot 2.7.5. Released at 18/04/2026, Fix external Tomcat Initializer.
* Version **1.0.25**, released at 22/03/2026 Modern WAF Defense, XSS, SQL, Google, Command, File, Java RCE, XXE, AI User-Agent.
* Version **1.0.24**, released at 01/09/2025  Forwarded headers Client and Transfer headers Backend Server, Cors headers exposed
* Version **1.0.23**, released at 22/08/2025 Reorganize WAFFilter Multipart, CorsConfiguration, Cookie client stateful
* Version **1.0.22**, released at 31/07/2025 Fix Security RateLimit, Content Security Policy and Referrer-Policy
* Version **1.0.21**, released at 29/07/2025 Fix Predicate for Hostnames, Shared CookieRedirectInterceptor, Postman-Echo performance
* Version **1.0.20**, released at 26/07/2025 Fix Spring Security dependencies, Improve security WAFFilter and WAFPredicate - Bis
* Version **1.0.19**, released at 26/07/2025 Fix Spring Security dependencies, Improve security WAFFilter and WAFPredicate
* Version **1.0.18**, released at 10/05/2025 Fix manage Cookie during a redirection 3xx
* Version **1.0.17**, released at 04/05/2025 Fix manage Cookie, Gateway is stateless!
* Version **1.0.16**, released at 03/11/2024 Fix CORS Security configuration Spring 5/6
* Version **1.0.15**, released at 23/10/2024 Fix missing method addCorsMappings
* Version **1.0.14**, released at 14/10/2024 Support Backend Headers and Support ContentNegotiation Header Strategy for Resources
* Version **1.0.13**, released at 06/10/2024 Full support Response in ByteArray Resource and Streaming Http Response Range Bytes
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
* Version **1.0.2** released at 28/04/2024.
* Version **1.0.1** released at 21/11/2022.
* Initial release **1.0.0** at 03/06/2021.

## 👨‍🚀 Support
If you need help using Nexus-Backend Service feel free to drop an email or create an issue in GitHub.com (preferred).

## 👥 Contributions
To help **Nexus-Backend / ApiBackend / BackendService** development you are encouraged to
* Provide suggestion/feedback/Issue
* pull requests for new features
* Star :star2: the project

## 📃 License

This project is an Open Source Software released under the [GPL-3.0 license](https://github.com/javaguru/nexus-backend/blob/master/LICENSE.txt).

Copyright (c) 2001-2026 JServlet.com [Franck ANDRIANO.](http://jservlet.com)

