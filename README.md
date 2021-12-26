# VERSION

- 2021-12-26 updated by choong
---
## ranger : 1.2.0 + 2.0.0
```
mvn clean compile package assembly:assembly install -DskipTests -Drat.skip=true
```

## hive : 2.3.9
```
mvn clean package -DskipTests
```

## presto : trino-315
```
./mvnw clean install -DskipTests
```
