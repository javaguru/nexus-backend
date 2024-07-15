Nexus-Backend Service
=====================

### Postman Echo Service

Postman Echo is a service you can use to test your REST clients and make sample API calls. 
It provides endpoints for GET, POST, PUT, PATCH various auth mechanisms and other utility endpoints.

The documentation for the endpoints as well as example responses can be found at https://postman-echo.com 
[Mocking with examples](https://learning.postman.com/docs/designing-and-developing-your-api/mocking-data/mocking-with-examples/) 

© 2024 Postman, Inc.

## Url Backend Server configuration

 * nexus.backend.url=https://postman-echo.com
 * nexus.backend.uri.alive=/get

## Method GET parameters

curl --location 'https://postman-echo.com/get?foo1=bar1&foo2=bar2'

curl 'http://localhost:8082/nexus-backend/api/get?foo1=bar1&foo2=bar2'

## Method POST parameters

curl -X POST 'https://postman-echo.com/post?hand=wave'

curl -X POST 'http://localhost:8082/nexus-backend/api/post?hand=wave'

curl -X POST 'http://localhost:8082/nexus-backend/api/post?foo1=bar1&ffo2=bar2'

## Method POST data-urlencode

curl -v --location 'https://postman-echo.com/post' --data-urlencode 'foo1=bar1' --data-urlencode 'foo2=bar2'

curl --location 'http://localhost:8082/nexus-backend/api/post' -H 'Content-Type: application/json' -d '{"foo1": "bar1", "foo2": "bar2"}'

curl -X POST 'http://localhost:8082/nexus-backend/api/post' -H 'Content-Type: application/json' -d '{"foo1": "bar1", "foo2": "bar2"}'

## Method POST Form Data

curl -X POST 'http://localhost:8082/nexus-backend/api/post' -H 'Content-Type: application/x-www-form-urlencoded' -d 'foo1=bar1&foo2=bar2'

curl -X POST 'http://localhost:8082/nexus-backend/api/post' --header 'Content-Type: application/x-www-form-urlencoded' --data-urlencode 'foo1=bar1' --data-urlencode 'foo2=bar2'


## Method POST Raw Text

curl --location 'https://postman-echo.com/post' --header 'Content-Type: text/plain'  --data 'Duis posuere augue vel cursus pharetra'

curl --location 'http://localhost:8082/nexus-backend/api/post' --header 'Content-Type: text/plain' --data 'Duis posuere augue vel cursus pharetra.'

## Method GET Basic Auth

### GET Test 401 UNAUTHORIZED

curl https://postman-echo.com/digest-auth

curl http://localhost:8082/nexus-backend/api/digest-auth
```
{"code":"500","level":"ERROR","source":"ERROR-NEXUS-REST-BACKEND","text":"An internal error occurred on the backend. URI: /digest-auth Reason id '401 UNAUTHORIZED'"}
```

### GET DigestAuth Success  {"authenticated":true}

curl --location 'https://postman-echo.com/basic-auth' --header 'Authorization: Basic cG9zdG1hbjpwYXNzd29yZA=='

curl 'http://localhost:8082/nexus-backend/api/basic-auth' -H 'Authorization: Basic cG9zdG1hbjpwYXNzd29yZA==


## Method PUT

curl --location --request PUT 'https://postman-echo.com/put' --header 'Content-Type: text/plain' --data 'Etiam mi lacus, cursus vitae felis'

curl --location --request PUT 'http://localhost:8082/nexus-backend/api/put' --header 'Content-Type: text/plain' --data 'Etiam mi lacus, cursus vitae felis'


## Method PATCH

curl --location --request PATCH 'https://postman-echo.com/patch' --header 'Content-Type: text/plain' --data 'Curabitur auctor, elit nec'

curl --location --request PATCH 'http://localhost:8082/nexus-backend/api/patch' --header 'Content-Type: text/plain' --data 'Curabitur auctor, elit nec'


## Method DELETE

curl --location --request DELETE 'https://postman-echo.com/delete' --header 'Content-Type: text/plain' --data 'Donec fermentum, nisi sed cursus'

curl --location --request DELETE 'http://localhost:8082/nexus-backend/api/delete' --header 'Content-Type: text/plain' --data 'Donec fermentum, nisi sed cursus'


## Headers

curl --location 'https://postman-echo.com/headers' -header 'my-sample-header: Lorem ipsum dolor sit amet'

curl 'http://localhost:8082/nexus-backend/api/headers' -H 'my-sample-header: Lorem ipsum dolor sit amet'


# Utilities 


## GET GZip Compressed Response 

### Output ByteArray / Binary

curl https://postman-echo.com/gzip --output gzip.echo.txt

### Native ByteArray to Json (Jackson Base64 decoded)

curl http://localhost:8082/nexus-backend/api/gzip --output gzip.echo.txt
```
{"gzipped":true,"headers":{"host":"localhost","x-request-start":"t=1721076856.560","x-forwarded-proto":"https","x-forwarded-port":"443","x-amzn-trace-id":"Root=1-66958c78-235d73c85e60e17479a7e127","origin":"http://localhost:8082/nexus-backend/api/gzip","user-agent":"curl/7.66.0","accept":"*/*","accept-encoding":"gzip,deflate"},"method":"GET"}
```

## GET Deflate Compressed Response

### Output ByteArray / Binary

curl  https://postman-echo.com/deflate --output test-deflate.txt

### Native ByteArray to Json (Jackson Base64 decoded)

curl  http://localhost:8082/nexus-backend/api/deflate  --output test-deflate.txt

```
{"deflated":true,"headers":{"host":"localhost","x-request-start":"t=1721077334.286","x-forwarded-proto":"https","x-forwarded-port":"443","x-amzn-trace-id":"Root=1-66958e56-102ca75c0702fd3d71b5dbf2","origin":"http://localhost:8082/nexus-backend/api/deflate","user-agent":"curl/7.66.0","accept":"*/*","accept-encoding":"gzip,deflate"},"method":"GET"}
```

Copyright (c) 2001-2024 JServlet.com [Franck ANDRIANO.](http://jservlet.com)
