# dev-quest-service

This Backend service is responsible for business domain data e.g. businesses, offices and desks.

### Order of setup scripts:

1. ./setup_postgres.sh
2. ./setup_flyway_migrations.sh
3. ./setup_app.sh (this can be ran whenever)

### To run the app

```
./run.sh
```

### To run the tests

```
./run_tests.sh
```

### To run only a single test suite in the integration tests:

Please remember to include the package/path for the shared resources,
the shared resources is needed to help WeaverTests locate the shared resources needed for the tests

```
./itTestOnly RegistrationControllerISpec controllers.ControllerSharedResource
```

---

### To Set up the database

```
./setup_postgres.sh
```

### To populate the postgresql database using flyway

Please run the docker compose scripts

```
./setup_flyway_migrations.sh
```

### To clear down the database

```
./clear_down_postgres.sh
```

### To clear down the flyway container

```
./clear_down_flyway.sh
```

### To clear down docker container for app and orphans

```
docker-compose down --volumes --remove-orphans
```

---

### To connect to postgresql database

```
psql -h localhost -p 5432 -U shared_user -d shared_db
```

#### App Database Password:

```
share
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
http POST http://localhost:8080/dev-quest-service/business/offices/address/create Content-Type:application/json businessId="BUS12345" officeId="OFF12345" buildingName="Example Building" floorNumber="12" street="123 Example Street" city="Example City" country="Example Country" county="Example County" postcode="12345" latitude:=12.345678 longitude:=-98.765432
```

http PUT http://localhost:8080/dev-quest-service/business/offices/address/OFF-3fc560b7-c039-4267-9de3-023a10077a5f \
buildingName="New Building" \
floorNumber=3 \
street="123 Main St" \
city="ExampleCity" \
country="ExampleCountry" \
county="ExampleCounty" \
postcode="12345" \
latitude=12.34 \
longitude=56.78 \
updatedAt="2025-01-01T12:00:00"

http GET http://localhost:8080/dev-quest-service/business/businesses/listing/cards/find/all

http GET http://localhost:8080/dev-quest-service/business/office/listing/cards/find/all/BUS-4d50bd78-fe03-4dcd-a9ab-b2dabe7e9bd3

http PUT http://localhost:8080/dev-quest-service/business/offices/contact/details/update/OFF-9573ca68-737e-47c2-97f1-c639c7b0daca \
primaryContactFirstName="Mikey" \
primaryContactLastName="Yau" \
contactEmail="mikey@gmail.com" \
contactNumber="07402205071" \
updatedAt="2025-01-01T12:00:00"

### TODO: WIP

```


```


sbt docker:publishLocal

docker run -p 8080:8080 dev-quest-service:0.1.0-SNAPSHOT

❯ psql -h localhost -p 5431 -U dev_quest_test_user -d dev_quest_test_db