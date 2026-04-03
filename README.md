# Spring Boot Kafka Avro -- Producer

A beginner-friendly Spring Boot application that **sends Employee data to Apache Kafka** using **Avro** serialization and **Schema Registry**.

> **New to Kafka?** Read the "What is Kafka?" section below before touching the code.

---

## What is Kafka? (The 60-Second Version)

Imagine two apps: a **Booking Service** and a **Payment Service**. The Booking Service needs to tell Payment "hey, someone just booked." In a naive setup, Booking calls Payment directly via REST. But what if Payment is down? The data is lost.

**Kafka is a middleman (message broker).** Instead of App A talking directly to App B:

```
WITHOUT Kafka:
  Booking Service ---REST---> Payment Service     (if Payment is down, data is LOST)

WITH Kafka:
  Booking Service ---> [ KAFKA ] ---> Payment Service
                        (stores the message until Payment is ready to read it)
```

Think of Kafka as an **indestructible mailbox**. The producer drops a letter in, and the consumer picks it up whenever it is ready. The letter is never lost.

### Key Terms (Glossary)

| Term | Plain English |
|------|--------------|
| **Producer** | The app that SENDS messages (this project) |
| **Consumer** | The app that READS messages (the consumer project) |
| **Broker** | A single Kafka server that stores messages |
| **Topic** | A named mailbox / channel (like `employee-avro-topic`) |
| **Partition** | A topic is split into partitions for parallel processing. 3 partitions = 3 threads can read simultaneously |
| **Offset** | A sequential ID for each message in a partition -- the "bookmark" of what has been read |
| **Avro** | A compact binary format for data (much smaller & faster than JSON) |
| **Schema Registry** | A server that stores the "shape" of your data (the schema). Producers and consumers agree on the schema so data never gets corrupted |
| **KRaft** | Kafka 3.x+ runs its own coordination (no more Zookeeper needed) |

---

## How the Whole System Fits Together

```
                         +-------------------+
  YOU (curl/Postman)     |  Schema Registry  |
        |                |   (port 8081)     |
        | POST JSON      |  Stores the Avro  |
        v                |  schema so both   |
  +-------------+        |  sides agree on   |
  |  PRODUCER   |------->|  the data shape   |
  | (this app)  |        +-------------------+
  | port 9393   |                |
  +------+------+                |
         |                       |
         | Avro binary           | fetch schema
         v                       v
  +------------------------------------------+
  |            KAFKA BROKER                  |
  |         (port 9092)                      |
  |                                          |
  |  Topic: employee-avro-topic              |
  |  [ Partition 0 ] [ Partition 1 ] [ P2 ]  |
  +------------------------------------------+
         |
         | Avro binary
         v
  +-------------+
  |  CONSUMER   |
  | (other app) |
  | port 9494   |
  +-------------+
```

---

## Code Reading Guide -- Follow This Path

### Step 1: Infrastructure -- What do you need running?

**File:** `docker-compose.yml` (project root)

Open this first. It spins up two Docker containers:

| Container | Port | Purpose |
|-----------|------|---------|
| `kafka` | 9092 | The Kafka broker -- stores messages |
| `schema-registry` | 8081 | Stores Avro schemas so producer & consumer agree on data shape |

> Without these two running, nothing works.

---

### Step 2: The Data Contract -- What does an Employee look like?

**File:** `src/main/avro/employee.avsc`

This is the **single source of truth** for the Employee data shape. Both the producer and consumer must have this identical file.

```
Fields defined here:
  id          : int       (required)
  name        : string    (required)
  email       : string    (required)
  department  : string    (required)
  salary      : double    (required)
  phoneNumber : string    (OPTIONAL -- can be null)
  address     : string    (OPTIONAL -- can be null)
```

> The optional fields with `"default": null` are what enable **schema evolution** -- you can add new optional fields later without breaking existing consumers.

---

### Step 3: The Generated Class -- DO NOT EDIT

**File:** `src/main/java/com/kafkaPrac/kafka/avro/Employee.java`

This Java class is **auto-generated** from `employee.avsc` by the Avro Maven Plugin during `mvn compile`. You never write or edit this file. It gives you:
- A type-safe builder: `Employee.newBuilder().setName("John").build()`
- Getters: `employee.getName()`, `employee.getSalary()`
- Built-in Avro serialization/deserialization

---

### Step 4: The REST DTO -- What does the API accept?

**File:** `src/main/java/com/kafkaPrac/kafka/dto/EmployeeRequest.java`

A plain Java POJO. When you POST JSON to the API, Spring deserializes it into this class. It has the same fields as the Avro schema but is a simple Java class (no Avro dependency).

> **Why not use the Avro `Employee` directly in the controller?** Separation of concerns. The REST layer should not depend on your serialization format. `EmployeeRequest` is the API contract; `Employee` is the Kafka contract.

---

### Step 5: Topic Creation -- Where do messages go?

**File:** `src/main/java/com/kafkaPrac/kafka/config/KafkaTopicConfig.java`

When the app starts, Spring Boot auto-creates the topic:

```
Topic name : employee-avro-topic    (from application.yaml)
Partitions : 3                      (3 parallel lanes for messages)
Replicas   : 1                      (1 copy -- fine for local dev)
```

This file also creates a `KafkaAdmin` bean that connects to the broker to manage topics.

---

### Step 6: Producer Configuration -- THE CORE

**File:** `src/main/java/com/kafkaPrac/kafka/config/KafkaAvroProducerConfig.java`

**This is the most important file in the project.** It configures HOW messages are sent:

```
producerConfigs()           -- settings map:
  BOOTSTRAP_SERVERS         --> localhost:9092 (where is Kafka?)
  KEY_SERIALIZER            --> StringSerializer (keys are strings like "1", "2")
  VALUE_SERIALIZER          --> KafkaAvroSerializer (values are Avro binary)
  SCHEMA_REGISTRY_URL       --> http://localhost:8081 (where are schemas stored?)
  AUTO_REGISTER_SCHEMAS     --> true (register new schemas automatically)
  ACKS                      --> "all" (wait for all replicas to confirm)
  RETRIES                   --> 3 (retry up to 3 times on failure)
          |
          v
producerFactory()           -- creates Kafka producer instances using above config
          |
          v
kafkaTemplate()             -- the high-level API you use in your service code
```

**Read this file line by line.** Every property is commented.

---

### Deep Dive: What is KafkaTemplate?

**Where it is created:** `src/main/java/com/kafkaPrac/kafka/config/KafkaAvroProducerConfig.java`
**Where it is used:** `src/main/java/com/kafkaPrac/kafka/service/KafkaAvroMessagePublisher.java`

#### The Problem it Solves

To send a message to Kafka without Spring, you would need to:
1. Create a `Properties` object with 10+ settings
2. Instantiate a `KafkaProducer`
3. Build a `ProducerRecord`
4. Call `producer.send(record)`
5. Handle callbacks, errors, and flushing manually
6. Close the producer when done

That is a lot of boilerplate for every message. **KafkaTemplate wraps all of that into one simple method call.**

#### Plain English

Think of it like this:

```
KafkaProducer (raw Kafka API)  =  Driving a manual car (clutch, gear shifts, everything yourself)
KafkaTemplate (Spring wrapper)  =  Driving an automatic car (just press the gas pedal)
```

`KafkaTemplate` is Spring's high-level wrapper around the raw Kafka Producer API. You just call `.send()` and it handles connection pooling, serialization, error handling, and async callbacks for you.

#### How it is Built (3 Layers)

The config file creates KafkaTemplate in 3 steps -- each layer wraps the previous one:

```
LAYER 1: producerConfigs()              -- a Map of settings (what to do)
  |
  |  Settings like:
  |    - where is the broker?           (localhost:9092)
  |    - how to serialize the key?      (StringSerializer)
  |    - how to serialize the value?    (KafkaAvroSerializer)
  |    - where is Schema Registry?      (http://localhost:8081)
  |
  v
LAYER 2: producerFactory()              -- a factory that creates producer instances
  |
  |  new DefaultKafkaProducerFactory<>(producerConfigs())
  |  This factory uses your settings to create actual Kafka producer connections.
  |  It also manages connection pooling -- reuses connections instead of creating new ones.
  |
  v
LAYER 3: kafkaTemplate()               -- the thing you actually use in your code
  |
  |  new KafkaTemplate<>(producerFactory())
  |  This is what gets @Autowired into your service classes.
  |
  v
YOUR CODE: kafkaTemplate.send(topic, key, value)    -- one line to send a message
```

In the actual Java code (`KafkaAvroProducerConfig.java`):

```java
@Bean
public Map<String, Object> producerConfigs() {       // LAYER 1: settings
    Map<String, Object> props = new HashMap<>();
    props.put(BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    props.put(VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
    props.put(SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    props.put(AUTO_REGISTER_SCHEMAS, true);
    props.put(ACKS_CONFIG, "all");
    props.put(RETRIES_CONFIG, 3);
    return props;
}

@Bean
public ProducerFactory<String, Object> producerFactory() {   // LAYER 2: factory
    return new DefaultKafkaProducerFactory<>(producerConfigs());
}

@Bean
public KafkaTemplate<String, Object> kafkaTemplate() {      // LAYER 3: template
    return new KafkaTemplate<>(producerFactory());
}
```

#### The send() Methods -- What You Can Do With KafkaTemplate

Once you have the template, here are the ways to send a message:

```
// 1. Just topic + value (Kafka picks partition, no key)
kafkaTemplate.send("employee-avro-topic", employee);

// 2. Topic + key + value (Kafka hashes the key to pick a partition)
//    Same key always goes to the same partition -- guarantees ordering per key
kafkaTemplate.send("employee-avro-topic", "employee-1", employee);

// 3. Topic + partition + key + value (YOU pick the exact partition)
kafkaTemplate.send("employee-avro-topic", 0, "employee-1", employee);

// 4. Send and get a result back (async)
CompletableFuture<SendResult<String, Object>> future =
    kafkaTemplate.send("employee-avro-topic", "employee-1", employee);

future.whenComplete((result, ex) -> {
    if (ex == null) {
        // SUCCESS -- message was stored
        log.info("Partition: {}", result.getRecordMetadata().partition());
        log.info("Offset: {}", result.getRecordMetadata().offset());
    } else {
        // FAILURE -- broker down, serialization error, etc.
        log.error("Failed: {}", ex.getMessage());
    }
});
```

#### What Happens Inside kafkaTemplate.send()

```
kafkaTemplate.send("employee-avro-topic", "1", employee)
       |
       |  1. KafkaTemplate takes your topic, key, and value
       |
       |  2. Serializes the KEY using StringSerializer
       |     "1" --> bytes [49]
       |
       |  3. Serializes the VALUE using KafkaAvroSerializer
       |     Employee object --> registers schema in Schema Registry
       |                     --> converts to compact Avro binary bytes
       |
       |  4. Builds a ProducerRecord(topic, key-bytes, value-bytes)
       |
       |  5. Sends to Kafka broker asynchronously (non-blocking)
       |
       |  6. Returns CompletableFuture<SendResult> so you can
       |     handle success/failure in a callback
       v
  Message lands in Kafka broker
```

#### Why CompletableFuture (Async)?

`send()` does **not block** your thread. It returns immediately with a `CompletableFuture`. The actual network call to Kafka happens in the background. This is critical for high throughput -- your API can accept the next request while the previous message is still being delivered to Kafka.

In this project, the publisher uses `.whenComplete()` to log the result:

```java
// From KafkaAvroMessagePublisher.java:
CompletableFuture<SendResult<String, Object>> future =
        kafkaTemplate.send(employeeTopic, String.valueOf(employee.getId()), employee);

future.whenComplete((result, ex) -> {
    if (ex == null) {
        log.info("Topic: {}, Partition: {}, Offset: {}",
                result.getRecordMetadata().topic(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());
    } else {
        log.error("Failed to send: {}", ex.getMessage());
    }
});
```

#### KafkaTemplate vs Raw KafkaProducer -- Summary

| | Raw KafkaProducer | KafkaTemplate |
|-|-------------------|---------------|
| Setup | 15+ lines of boilerplate | Configured once in a @Bean |
| Connection management | Manual open/close | Auto-managed (pooled) |
| Serialization | Manual | Auto (configured in producerConfigs) |
| Error handling | Manual callbacks | Built-in CompletableFuture |
| Spring integration | None | @Autowired, works with @Transactional |
| Thread safety | You must manage | Thread-safe out of the box |

---

### Step 7: The Publisher Service -- Sending messages

**File:** `src/main/java/com/kafkaPrac/kafka/service/KafkaAvroMessagePublisher.java`

This service has two methods:

```
sendEmployee(employee)
  --> kafkaTemplate.send(topic, key, employee)
  --> CompletableFuture callback logs success or failure

sendEmployeeToPartition(employee, partition)
  --> kafkaTemplate.send(topic, partition, key, employee)
  --> sends to a SPECIFIC partition instead of letting Kafka decide
```

The `@Value("${app.kafka.topics.employee}")` pulls the topic name from `application.yaml`.

---

### Step 8: The REST Controller -- Entry point for requests

**File:** `src/main/java/com/kafkaPrac/kafka/controller/EmployeeController.java`

Three endpoints:

```
POST /avro-producer/employee
  --> Converts EmployeeRequest to Avro Employee
  --> Calls publisher.sendEmployee(employee)
  --> Kafka decides which partition

POST /avro-producer/employee/partition/{partition}
  --> Same, but YOU choose the partition (0, 1, or 2)
  --> Calls publisher.sendEmployeeToPartition(employee, partition)

POST /avro-producer/employee/minimal
  --> Only sets required fields (id, name, email, department, salary)
  --> phoneNumber and address are null
  --> Demonstrates schema evolution -- consumer still works fine
```

---

### Step 9: Application Config -- Connecting the dots

**File:** `src/main/resources/application.yaml`

```yaml
server.port: 9393                           # This app runs on port 9393

spring.kafka.bootstrap-servers: localhost:9092   # Where Kafka is running
schema.registry.url: http://localhost:8081       # Where Schema Registry is running

app.kafka.topics.employee: employee-avro-topic   # Topic name used everywhere
```

Every `@Value(...)` annotation in the Java code pulls from this file.

---

### Step 10: Dependencies

**File:** `pom.xml` (project root)

Key dependencies:
- `spring-boot-starter-web` -- REST API
- `spring-kafka` -- Kafka integration
- `avro` -- Avro data format
- `kafka-avro-serializer` -- converts Java objects to Avro binary
- `kafka-schema-registry-client` -- talks to Schema Registry
- `avro-maven-plugin` -- generates `Employee.java` from `employee.avsc`

---

## The Complete Runtime Flow -- File by File

```
YOU send:  curl POST localhost:9393/avro-producer/employee  {"name":"John",...}
               |
               v
  EmployeeController.java                    [controller/]
    |  receives JSON as EmployeeRequest
    |  converts to Avro Employee using Employee.newBuilder()...build()
    |  calls publisher.sendEmployee(employee)
               |
               v
  KafkaAvroMessagePublisher.java             [service/]
    |  calls kafkaTemplate.send("employee-avro-topic", "1", employee)
    |  kafkaTemplate was created by...
               |
               v
  KafkaAvroProducerConfig.java               [config/]
    |  kafkaTemplate --> ProducerFactory --> producerConfigs
    |  VALUE_SERIALIZER = KafkaAvroSerializer
    |  SCHEMA_REGISTRY_URL = http://localhost:8081
               |
               v
  KafkaAvroSerializer (Confluent library, not your code)
    |  Step A: checks Schema Registry -- "does this schema exist?"
    |  Step B: if new, registers it (AUTO_REGISTER_SCHEMAS = true)
    |  Step C: serializes Employee into compact Avro binary bytes
               |
               v
  Kafka Broker (localhost:9092)
    |  stores message in employee-avro-topic, Partition 1, Offset 42
               |
               v
  Consumer app (port 9494) picks it up later
```

---

## What Happens at App Startup (Before Any Request)

```
Spring Boot starts
       |
       v
  KafkaAvroProducerConfig.java          creates KafkaTemplate bean
       |
       v
  KafkaTopicConfig.java                 creates "employee-avro-topic" (3 partitions)
       |
       v
  application.yaml                      provides all the config values via @Value
       |
       v
  App is ready on port 9393             waiting for POST requests
```

---

## Quick Start

### Prerequisites
- Java 17+
- Maven
- Docker & Docker Compose

### 1. Start Kafka + Schema Registry
```bash
docker-compose up -d

# Verify everything is running:
docker-compose ps

# Test Schema Registry:
curl http://localhost:8081/subjects
```

### 2. Build & Run the Producer
```bash
mvn clean compile
mvn spring-boot:run
```
The app starts on **port 9393**.

### 3. Send an Employee
```bash
curl -X POST http://localhost:9393/avro-producer/employee \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "name": "John Doe",
    "email": "john@company.com",
    "department": "Engineering",
    "salary": 75000.00,
    "phoneNumber": "+1-555-0123",
    "address": "123 Main St"
  }'
```

### 4. Send with only required fields (schema evolution demo)
```bash
curl -X POST http://localhost:9393/avro-producer/employee/minimal \
  -H "Content-Type: application/json" \
  -d '{
    "id": 2,
    "name": "Jane Smith",
    "email": "jane@company.com",
    "department": "Design",
    "salary": 80000.00
  }'
```

### 5. Send to a specific partition
```bash
curl -X POST http://localhost:9393/avro-producer/employee/partition/0 \
  -H "Content-Type: application/json" \
  -d '{
    "id": 3,
    "name": "Bob Wilson",
    "email": "bob@company.com",
    "department": "Sales",
    "salary": 65000.00
  }'
```

---

## Why Avro Instead of JSON?

| | JSON | Avro |
|-|------|------|
| Format | Text (human-readable) | Binary (compact) |
| Size | Larger | ~30-50% smaller |
| Speed | Slower parsing | Faster serialization |
| Schema | No enforcement | **Enforced by Schema Registry** |
| Evolution | Break consumers easily | Add/remove optional fields safely |

In production with millions of messages per second, Avro saves significant bandwidth and CPU.

---

## Useful Docker Commands

```bash
# List topics
docker exec kafka kafka-topics --list --bootstrap-server localhost:9092

# See registered schemas
curl http://localhost:8081/subjects

# View schema details
curl http://localhost:8081/subjects/employee-avro-topic-value/versions/latest

# Stop everything
docker-compose down
```

---

## Related Project

- **Consumer:** [springboot-kafka-avro-consumer](https://github.com/yunussid/springboot-kafka-avro-consumer) -- reads Employee messages from the same topic
