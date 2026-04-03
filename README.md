# Spring Boot Kafka Avro — Producer

A beginner-friendly Spring Boot application that **sends Employee data to Apache Kafka** using **Avro** serialization and **Schema Registry**.

> **New to Kafka?** Read the ["What is Kafka?" section](#-what-is-kafka-the-60-second-version) below before touching the code.

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
| **Offset** | A sequential ID for each message in a partition — the "bookmark" of what has been read |
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

## Project Structure — Read the Code in This Order

```
springboot-kafka-avro-producer/
  |
  |-- docker-compose.yml          1. START HERE: spins up Kafka + Schema Registry
  |
  |-- src/main/avro/
  |     +-- employee.avsc          2. The Avro schema — defines Employee fields
  |
  |-- src/main/java/com/kafkaPrac/kafka/
  |     |-- avro/
  |     |     +-- Employee.java    3. AUTO-GENERATED from employee.avsc (do NOT edit)
  |     |
  |     |-- dto/
  |     |     +-- EmployeeRequest.java   4. Simple POJO — what the REST API receives
  |     |
  |     |-- config/
  |     |     |-- KafkaTopicConfig.java          5. Creates the topic at startup
  |     |     +-- KafkaAvroProducerConfig.java   6. Configures HOW to serialize & send
  |     |
  |     |-- service/
  |     |     +-- KafkaAvroMessagePublisher.java 7. The actual "send to Kafka" logic
  |     |
  |     |-- controller/
  |     |     +-- EmployeeController.java        8. REST endpoints that trigger sends
  |     |
  |     +-- KafkaAvroProducerApplication.java    9. Spring Boot main class
  |
  |-- src/main/resources/
  |     +-- application.yaml      10. Ports, broker address, topic name
  |
  +-- pom.xml                     11. Dependencies (Spring Kafka, Avro, Confluent)
```

### Recommended reading order explained

| Step | File | Why read it? |
|------|------|-------------|
| 1 | `docker-compose.yml` | Understand what infrastructure you need: a Kafka broker (port 9092) and a Schema Registry (port 8081) |
| 2 | `employee.avsc` | This is the "contract" — the shape of an Employee message. Fields like `id`, `name`, `email`, `salary`. Optional fields (`phoneNumber`, `address`) show how schemas can evolve without breaking consumers |
| 3 | `Employee.java` (avro/) | Auto-generated from the `.avsc`. You never write this. It gives you a type-safe builder: `Employee.newBuilder().setName("John").build()` |
| 4 | `EmployeeRequest.java` | A plain Java class for the REST API. The controller receives this, then converts it to the Avro `Employee` |
| 5 | `KafkaTopicConfig.java` | Creates `employee-avro-topic` with 3 partitions and 1 replica when the app starts |
| 6 | `KafkaAvroProducerConfig.java` | **THE CORE CONFIG.** Sets up: (a) broker address, (b) `KafkaAvroSerializer` for values, (c) Schema Registry URL, (d) auto-register schemas. This is where the magic happens |
| 7 | `KafkaAvroMessagePublisher.java` | Calls `kafkaTemplate.send(topic, key, employee)`. Logs success (topic, partition, offset) or failure |
| 8 | `EmployeeController.java` | Three REST endpoints: send employee, send to specific partition, send with only required fields |
| 9 | `application.yaml` | Connects everything: broker at `localhost:9092`, schema registry at `localhost:8081`, topic name `employee-avro-topic` |

---

## What Happens When You Send a Message (Step by Step)

```
1. You call:  POST http://localhost:9393/avro-producer/employee  { "name": "John", ... }

2. EmployeeController receives the JSON as EmployeeRequest

3. Controller converts it to Avro Employee using:
     Employee.newBuilder().setName("John").setEmail("john@co.com") ... .build()

4. Controller calls publisher.sendEmployee(employee)

5. KafkaAvroMessagePublisher calls kafkaTemplate.send("employee-avro-topic", "1", employee)

6. KafkaAvroSerializer (configured in KafkaAvroProducerConfig):
     a. Checks Schema Registry — "does this schema exist?"
     b. If not, registers it automatically (AUTO_REGISTER_SCHEMAS = true)
     c. Converts the Employee object to compact Avro BINARY bytes

7. The binary bytes are sent to Kafka broker on topic "employee-avro-topic"

8. Kafka stores the message in one of the 3 partitions and assigns it an offset

9. The CompletableFuture callback logs:
     "Employee sent successfully - Topic: employee-avro-topic, Partition: 1, Offset: 42"
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

- **Consumer:** [springboot-kafka-avro-consumer](https://github.com/yunussid/springboot-kafka-avro-consumer) — reads Employee messages from the same topic
