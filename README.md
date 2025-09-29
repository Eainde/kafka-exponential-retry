# Kafka Retry Spring Boot Starter

A reusable Spring Boot library that provides a **cluster-safe, exponential backoff retry mechanism** for both producer and consumer Kafka messages.

This library allows any microservice to systematically handle message processing failures without hammering external services or writing duplicate retry logic. It funnels all failures into a single, unified persistence and retry workflow.

---

## 🚀 How It Works

The library intercepts Kafka failures from two primary sources:

- **Consumer Failures**  
  Via a global error channel (`@ServiceActivator`) that catches exceptions from any `@KafkaListener`.

- **Producer Failures**  
  Via a `ProducerListener` that is automatically attached to your application's `KafkaTemplate`.

In both cases:
- The failed message's topic is matched against configurable patterns.
- A corresponding logical handler name is found.
- The message payload is stored in a `failed_messages` database table.

A single, cluster-safe scheduler (`@Scheduled` + `@SchedulerLock`):
- Periodically queries this table.
- Finds messages due for a retry based on an **exponential backoff algorithm**.
- Invokes the correct `RetryMessageHandler` Spring bean to re-process the message.

---

## ✨ Features

- **Unified Retry Logic**: Handles both producer and consumer failures.
- **Topic-to-Handler Routing**: Map dynamic topic names to specific handler beans using `application.yml`.
- **Exponential Backoff**: Increases delay between retries (e.g., 5m → 10m → 20m...).
- **Cluster Safe**: Uses **ShedLock** to ensure only one instance of the scheduler runs in multi-node environments.
- **Configurable**: Control retry limits, intervals, batch size via `application.yml`.
- **Direct Logic Invocation**: Retries call your Java business logic directly — not by re-publishing to Kafka.
- **Auto-Configurable**: Just add the dependency and configure properties.

---

## ⚙️ Setup and Configuration

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>kafka-retry-spring-boot-starter</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```
### 2. Properties
```yml
kafka:
  retry:
    # Enable or disable retry mechanism
    enabled: true

    # First retry interval (minutes)
    initial-interval-minutes: 5

    # Max retries before marking permanent failure
    max-retries: 5

    # Max records processed in a scheduler run
    batch-size: 100

    # (Optional) Scheduler cron expression
    cron: "0 */1 * * * *" # every minute

    # --- Handler Mappings: Routing Logic ---
    handler-mappings:
      orderEventsHandler: "cl.uk.*.order-events.rt"
      shipmentNotificationHandler: "cl.uk.*.shipment-notifications.rt"

```
### 3. Implementing Interface
```java
import com.example.kafkaretry.service.RetryMessageHandler;
import org.springframework.stereotype.Component;

@Component("orderEventsHandler") // must match key in application.yml
public class OrderEventsMessageHandler implements RetryMessageHandler {

    @Override
    public void handle(String payload) throws Exception {
        // Original business logic for processing an order event
        // Called for both consumer and producer retries
    }
}


```