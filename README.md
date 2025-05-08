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
