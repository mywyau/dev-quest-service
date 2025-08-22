# dev-quest-service

This Backend service is responsible for business domain data e.g. businesses, offices and desks.

### Order of setup scripts:

To set up the postgres db please run scripts from repo:

### dev-irl-database-setup

1. ./setup_postgres.sh
2. ./setup_flyway_migrations.sh

Then (this can be ran whenever):

```
 ./setup_app.sh
```

### connect to redis

### run redis test container for integration testing, port: 6380

```
docker run --name redis-test-container -p 6380:6379 -d redis
```

#### Run redis-server on port 6379:

```
redis-server
```

#### Run redis-cli to enter cli

```
redis-cli
```

```
keys *
```

```
get <keyId>
```

```
del <keyId>
```

### To run the app locally

```
./run.sh
```

### To run the tests locally

```
./run_tests.sh
```

### To run only a single test suite in the integration tests:

Please remember to include the package/path for the shared resources,
the shared resources is needed to help WeaverTests locate the shared resources needed for the tests

```
./itTestOnly QuestRegistrationControllerISpec controllers.ControllerSharedResource
```

---

### To clear down docker container for app and orphans

```
docker-compose down --volumes --remove-orphans
```

---

### To connect to postgresql database

```
psql -h localhost -p 5432 -U dev_quest_user -d dev_quest_db
```

#### App Database Password:

```
turnip
```

### To connect to TEST postgresql Database

```
psql -h localhost -p 5431 -U dev_quest_test_user -d dev_quest_test_db
```

#### TEST Database Password:

```
turnip
```

---

### Set base search path for schema

••• Only needed if using multiple schemas in the db. At the moment we are using public so no need beforehand
accidentally set a new schema in flyway conf

```
ALTER ROLE shared_user SET search_path TO share_schema, public;
```

### Httpie requests

We can use httpie instead of curl to trigger our endpoints.

```

```

### TODO: WIP

```
sbt docker:publishLocal
```

### Make s3 test bucket for it tests

aws --endpoint-url=http://localhost:4566 s3 mb s3://test-bucket
aws --endpoint-url=http://localhost:4566 s3 mb s3://dev-submissions
aws --endpoint-url=http://localhost:4566 s3 ls s3://test-bucket

---

### Inspect local stack bucket contents from testing

aws --endpoint-url=http://localhost:4566 s3 cp s3://test-bucket/integration-test/hello.txt - | cat

aws --endpoint-url=http://localhost:4566 s3api get-object \
 --bucket test-bucket \
 --key integration-test/hello.txt \
 output.txt

cat output.txt

---

```
aws --endpoint-url=http://localhost:4566 s3 ls s3://test-bucket/uploads/
```

```
aws --endpoint-url=http://localhost:4566 s3api get-object \
  --bucket test-bucket \
  --key uploads/<UUID>-test.txt \
  output.txt

cat output.txt
```

http -f POST http://localhost:8080/dev-quest-service/upload file@./src/main/scala/controllers/UploadController.scala

aws --endpoint-url=http://localhost:4566 s3 ls s3://dev-submissions/uploads/

aws --endpoint-url=http://localhost:4566 s3api get-object \
 --bucket dev-submissions \
 --key uploads/ed677064-ac83-4bf5-9885-e834df6c80bc-UploadController.scala \
 output.txt

cat output.txt

### some useful http commands to testing and reminding

```
http --download "http://localhost:4566/dev-submissions/uploads/ed677064-ac83-4bf5-9885-e834df6c80bc-UploadController.scala?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20250611T174401Z&X-Amz-SignedHeaders=host&X-Amz-Credential=AKIA46ZDE3YHIZ4M2JBR%2F20250611%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Expires=900&X-Amz-Signature=0abf1491fa2c893969b0e37c8c68f221ef1dd98a7d90285f8ab713dcecd99d1f"
http POST http://localhost:8080/dev-quest-service/s3/presign-download key="uploads/ed677064-ac83-4bf5-9885-e834df6c80bc-UploadController.scala"
http POST http://localhost:8080/dev-quest-service/s3/presign-download key="uploads/ed677064-ac83-4bf5-9885-e834df6c80bc-UploadController.scala"
http -f POST http://localhost:8080/dev-quest-service/upload file@./src/main/scala/controllers/UploadController.scala
```


 uploads/b508ce06-258e-4d0b-b314-0671cbd6c982-DevQuestBackendAuthController.test.ts


### Download example

```
http POST http://localhost:8080/dev-quest-service/s3/presign-download key=uploads/b508ce06-258e-4d0b-b314-0671cbd6c982-DevQuestBackendAuthController.test.ts
```


http --download GET "http://localhost:4566/dev-submissions/uploads/b508ce06-258e-4d0b-b314-0671cbd6c982-DevQuestBackendAuthController.test.ts?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20250612T151211Z&X-Amz-SignedHeaders=host&X-Amz-Credential=AKIA46ZDE3YHIZ4M2JBR%2F20250612%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Expires=900&X-Amz-Signature=40b039fb31d064e800a8280d25693d1acfdb1a45be613ba40d3b50fb46fcfd67"


### 

#### Request to get signed download url

```
 http POST http://localhost:8080/dev-quest-service/s3/presign-download key=uploads/b508ce06-258e-4d0b-b314-0671cbd6c982-DevQuestBackendAuthController.test.ts
```

#### response for useful signed download url
```
HTTP/1.1 200 OK
Connection: keep-alive
Content-Length: 396
Content-Type: application/json
Date: Thu, 12 Jun 2025 15:12:11 GMT
Vary: Origin

{
    "url": "http://localhost:4566/dev-submissions/uploads/b508ce06-258e-4d0b-b314-0671cbd6c982-DevQuestBackendAuthController.test.ts?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20250612T151211Z&X-Amz-SignedHeaders=host&X-Amz-Credential=AKIA46ZDE3YHIZ4M2JBR%2F20250612%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Expires=900&X-Amz-Signature=40b039fb31d064e800a8280d25693d1acfdb1a45be613ba40d3b50fb46fcfd67"
}
```

### Download the file using signed url example

```
http --download GET  "http://localhost:4566/dev-submissions/uploads/b508ce06-258e-4d0b-b314-0671cbd6c982-DevQuestBackendAuthController.test.ts?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20250612T151211Z&X-Amz-SignedHeaders=host&X-Amz-Credential=AKIA46ZDE3YHIZ4M2JBR%2F20250612%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Expires=900&X-Amz-Signature=40b039fb31d064e800a8280d25693d1acfdb1a45be613ba40d3b50fb46fcfd67"
```



```
aws --endpoint-url=http://localhost:4566 s3 rm s3://dev-submissions --recursive
```

### Stripe webhook local testing

```
stripe login
```

```
stripe listen --forward-to localhost:8080/dev-quest-service/billing/webhook
```