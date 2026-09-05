# Notification Management Service

A Spring Boot-based Notification Management Service that accepts notification requests, determines eligible delivery channels, queues deliveries for asynchronous processing, handles delivery failures and retries, and provides notification status and audit history.

The project demonstrates a production-oriented approach to notification management while remaining simple enough to run locally without external messaging infrastructure.

## Technology Stack

* Java 21
* Spring Boot 4.1.1
* Spring Web
* Spring Data JPA
* PostgreSQL
* Maven
* JUnit 5
* Mockito
* Cucumber / Gherkin

## Architecture

The service follows a modular layered architecture.

```text
Business / Technical Systems
            |
            v
   Notification REST API
            |
            v
   Notification Service
            |
            +-------------------+
            |                   |
            v                   v
     Routing Service        PostgreSQL
            |                   |
            v                   |
     Delivery Records <---------+
            |
            v
    Delivery Processor
            |
            v
   Notification Provider
      /       |       \
   Email     SMS      Push
            |
            v
     Delivery Status
            |
            v
       Audit History
```

### Package Structure

```text
com.schwab.nms
├── config
│   └── SchedulingConfig
├── controller
│   ├── NotificationController
│   └── exception
│       ├── GlobalExceptionHandler
│       ├── IdempotencyConflictException
│       └── NotificationNotFoundException
├── database
│   ├── entities
│   │   ├── AuditEvent
│   │   ├── Delivery
│   │   ├── Notification
│   │   ├── Recipient
│   │   └── RecipientPreference
│   ├── entities.enums
│   └── repository
├── modules
│   ├── audit
│   │   └── AuditService
│   ├── delivery
│   │   ├── model
│   │   │   ├── DeliveryResult
│   │   │   ├── DeliveryStatusResponse
│   │   │   └── RecipientRequest
│   │   └── service
│   │       ├── DeliveryFailureHandler
│   │       ├── DeliveryProcessor
│   │       └── NotificationStatusService
│   ├── notification
│   │   ├── model
│   │   │   ├── NotificationRequest
│   │   │   ├── NotificationResponse
│   │   │   └── NotificationStatusResponse
│   │   └── service
│   │       └── NotificationService
│   ├── provider
│   │   ├── NotificationProvider
│   │   ├── EmailNotificationProvider
│   │   ├── SmsNotificationProvider
│   │   └── PushNotificationProvider
│   └── routing
│       ├── DefaultRoutingService
│       └── RoutingService
└── NotificationManagementServiceApplication
```

## Request Flow

1. A client submits a notification through the REST API.
2. `NotificationService` validates the request and checks the idempotency key.
3. Notification, recipient, and preference information is persisted.
4. `DefaultRoutingService` determines eligible delivery channels.
5. A delivery record is created for each selected recipient/channel combination with `QUEUED` status.
6. `DeliveryProcessor` periodically processes queued deliveries.
7. The appropriate `NotificationProvider` handles the delivery.
8. Successful deliveries are marked `DELIVERED`.
9. Retryable failures are scheduled for retry.
10. Permanent failures or exhausted retries are marked `FAILED`.
11. `NotificationStatusService` updates the overall notification status.
12. `AuditService` records significant notification lifecycle events.

## API

### Submit Notification

**POST** `/api/v1/notifications`

The API accepts a notification request and queues eligible deliveries for asynchronous processing.

Required header:

```text
Idempotency-Key: <unique-key>
```

Example request:

```json
{
  "sourceSystem": "PAYMENT_SYSTEM",
  "eventId": "PAY-12345",
  "notificationType": "PAYMENT_ALERT",
  "severity": "HIGH",
  "priority": "HIGH",
  "message": "A payment requires attention.",
  "recipients": [
    {
      "recipientKey": "USER-123",
      "email": "user@example.com",
      "phone": "+12145551234",
      "preferredChannels": [
        "EMAIL",
        "SMS"
      ]
    }
  ],
  "requestedChannels": [
    "EMAIL",
    "SMS"
  ],
  "scheduledAt": null,
  "expiresAt": null
}
```

Response:

**HTTP 202 Accepted**

```json
{
  "notificationId": "8b7c4e2d-...",
  "status": "QUEUED",
  "createdAt": "2026-09-05T18:30:00Z"
}
```

The response also provides a `Location` header for retrieving notification status.

### Retrieve Notification Status

**GET** `/api/v1/notifications/{notificationId}`

Returns the overall notification status and delivery status for each recipient/channel combination.

Example:

```json
{
  "notificationId": "8b7c4e2d-...",
  "sourceSystem": "PAYMENT_SYSTEM",
  "eventId": "PAY-12345",
  "notificationType": "PAYMENT_ALERT",
  "severity": "HIGH",
  "priority": "HIGH",
  "status": "PARTIALLY_DELIVERED",
  "createdAt": "2026-09-05T18:30:00Z",
  "scheduledAt": null,
  "expiresAt": null,
  "deliveries": [
    {
      "recipientKey": "USER-123",
      "channel": "EMAIL",
      "status": "DELIVERED",
      "attemptCount": 1,
      "lastAttemptAt": "2026-09-05T18:30:05Z",
      "completedAt": "2026-09-05T18:30:05Z",
      "failureType": null,
      "failureReason": null,
      "nextRetryAt": null
    }
  ]
}
```

### Error Responses

| HTTP Status | Condition                                       |
| ----------- | ----------------------------------------------- |
| 400         | Invalid request or validation failure           |
| 404         | Notification does not exist                     |
| 409         | Idempotency key reused with a different request |

Example:

```json
{
  "code": "IDEMPOTENCY_CONFLICT",
  "message": "Idempotency key already exists with a different request"
}
```

## Routing Rules

Routing is implemented by `DefaultRoutingService`.

The prototype uses the following routing policy:

1. Requested channels are considered first.
2. If recipient preferences are configured, only enabled preferred channels are eligible.
3. The recipient must have the required destination information for a channel.
4. `HIGH` and `CRITICAL` severity add SMS when SMS is eligible.
5. `CRITICAL` severity additionally adds PUSH when PUSH is eligible.
6. Duplicate channels are removed.
7. Explicitly disabled recipient channels are not selected.

The routing policy is intentionally documented because some aspects of the requirement were ambiguous.

## Idempotency and Deduplication

Each notification submission requires an `Idempotency-Key`.

The key is stored with a unique database constraint.

When the same key is submitted again with the same request, the existing notification is returned and no additional notification or delivery records are created.

If the same key is reused with a different request, the service returns:

```text
HTTP 409 Conflict
```

This prevents duplicate logical notifications and uncontrolled duplicate delivery records.

## Delivery Processing

The `deliveries` table acts as a durable database-backed work queue for the prototype.

`DeliveryProcessor` runs periodically and processes:

* `QUEUED` deliveries
* `RETRY_PENDING` deliveries whose retry time has been reached

The processor selects the provider based on the delivery channel.

The provider abstraction keeps channel-specific behavior separate from the delivery workflow.

Supported channels:

* EMAIL
* SMS
* PUSH

## Retry and Failure Handling

The prototype distinguishes retryable and non-retryable failures.

Retryable failures include:

* `TRANSIENT`
* `RATE_LIMITED`
* `TIMEOUT`

Non-retryable failures include:

* `PERMANENT`
* `INVALID_RECIPIENT`
* `AUTHORIZATION_ERROR`

Retry attempts are bounded to a maximum of three attempts.

Retry delays use increasing delays between attempts.

When the maximum number of attempts is exhausted, the delivery is marked `FAILED`.

## Scheduling and Expiration

Notifications can optionally specify:

* `scheduledAt` — earliest time at which delivery can begin.
* `expiresAt` — time after which the notification should no longer be delivered.

The delivery processor evaluates these values before attempting delivery.

Scheduled notifications remain queued until their scheduled time.

Expired notifications are marked `EXPIRED` and are not delivered.

## Audit History

Significant notification lifecycle events are recorded in the `audit_events` table.

Examples include:

* Notification accepted
* Routing started
* Notification queued
* Delivery attempted
* Delivery succeeded
* Delivery failed
* Retry scheduled
* Notification expired

Audit records provide a history of important processing decisions without storing unnecessary sensitive information.

## Assignment Scenarios

### Scenario 1 — Greenfield: Notification Submission and Delivery

The initial implementation decomposed the requirement into:

1. REST API
2. Request validation
3. Idempotency
4. Recipient and preference persistence
5. Channel routing
6. Durable delivery queue
7. Asynchronous delivery processing
8. Provider abstraction
9. Retry and failure handling
10. Status tracking
11. Audit history

The resulting flow accepts a notification request, creates delivery records, processes those deliveries asynchronously, and exposes delivery status.

The implementation was validated using Cucumber scenarios covering submission, routing, delivery, failure, retry, status, and idempotency.

### Scenario 2 — Brownfield: PUSH Notification Support

The initial notification system was treated as an existing implementation.

PUSH support was then introduced as a cross-cutting enhancement.

The change required:

* Adding `PUSH` to the channel model.
* Adding `PushNotificationProvider`.
* Extending routing logic.
* Integrating PUSH with the existing delivery processor.
* Extending critical-severity routing behavior.
* Adding BDD coverage for PUSH submission and delivery.

The provider abstraction allowed the new channel to be added without changing the core delivery-processing workflow.

### Scenario 3 — Ambiguous Requirement: Routing Decisions

The requirements specify that routing should consider requested channels, recipient preferences, severity, and routing policy, but do not completely define how conflicting inputs should be resolved.

For example:

* The caller requests EMAIL.
* The recipient supports EMAIL and SMS.
* The notification has HIGH severity.
* HIGH severity may require an additional channel.

A routing policy was explicitly defined for the prototype rather than leaving the behavior implicit.

The policy gives precedence to recipient preferences while allowing severity-based eligible channels to be added.

The resulting behavior was validated through focused unit tests covering requested channels, preferences, unsupported channels, HIGH severity, CRITICAL severity, and explicitly disabled channels.

## Testing

The project uses both BDD and unit testing.

### BDD

Cucumber/Gherkin scenarios cover:

* Notification submission
* Status retrieval
* Idempotency
* Idempotency conflicts
* Request validation
* EMAIL/SMS routing
* PUSH routing
* Successful delivery
* Retryable failures
* Permanent failures
* Maximum retry attempts
* Scheduling
* Expiration
* Delivery processing

### Unit Tests

Focused unit tests cover the core business rules:

* `DefaultRoutingServiceTest`
* `DeliveryFailureHandlerTest`
* `NotificationStatusServiceTest`

Current test suite:

**24 tests passing**

Run all tests:

```bash
mvn test
```

## Local Setup

### Prerequisites

* Java 21
* Maven
* PostgreSQL 18 or compatible PostgreSQL version

### Database

Create the database:

```sql
CREATE DATABASE notification_db;
```

Run database/schema.sql against notification_db.

Configure the application:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/notification_db
spring.datasource.username=postgres
spring.datasource.password=YOUR_POSTGRES_PASSWORD
```

Hibernate schema validation is enabled:

```properties
spring.jpa.hibernate.ddl-auto=validate
```

The application validates the existing database schema rather than automatically modifying it.

### Run the Application

```bash
mvn spring-boot:run
```

The application starts on:

```text
http://localhost:8080
```

### Run Tests

```bash
mvn test
```

The test profile disables scheduled background processing so that BDD scenarios can explicitly control delivery processing.

## Limitations and Trade-offs

This implementation is intentionally scoped as a runnable prototype rather than a production deployment.

### Database-backed Queue

The `deliveries` table is used as a durable queue.

This avoids introducing Kafka or another external messaging platform and keeps the prototype easy to run locally.

A production implementation could use Kafka, Amazon SQS, or another managed messaging solution for higher scalability and operational resilience.

### Provider Integrations

EMAIL, SMS, and PUSH providers are prototype implementations and do not call external notification providers.

They simulate provider behavior so that delivery, retry, and failure workflows can be tested.

### Concurrent Processing

The prototype uses a scheduled processor but does not implement distributed worker coordination or row-level work claiming.

A production implementation would require concurrency control to prevent multiple service instances from processing the same delivery simultaneously.

### Retry Configuration

Retry limits and retry delays are currently implemented in application code.

A production implementation could externalize these values into configuration and use exponential backoff with jitter.

### Idempotency Retention

Idempotency keys are stored with notification records.

The prototype does not implement automated idempotency-key cleanup or retention management.

A production implementation should define a retention period based on business requirements.

### Security

Authentication, authorization, secret management, encryption configuration, and production network security are outside the scope of this prototype.

Sensitive notification content and credentials should be handled according to organizational security and data-retention requirements.

### Observability

The prototype provides business-level audit events but does not implement full production observability such as centralized metrics, distributed tracing, dashboards, or alerting.

## AI-Assisted Engineering Approach

AI tools were used as an engineering accelerator while keeping architecture, requirements interpretation, implementation decisions, code review, and validation under developer control.

AI assistance was used for:

* Requirement decomposition
* Identifying implementation considerations and edge cases
* Generating and refining Java/Spring Boot code
* Creating unit tests
* Creating Cucumber scenarios
* Reviewing database and API design
* Debugging implementation issues
* Identifying additional validation scenarios

Examples of AI-assisted debugging included:

* Diagnosing Hibernate lazy-loading issues in delivery processing.
* Identifying duplicate Cucumber step definitions.
* Resolving scheduling behavior during BDD execution.
* Separating HTTP-level status assertions from processor-level status validation.
* Reviewing retry and expiration behavior.

The development process followed an iterative cycle:

```text
Requirement
    ↓
Decomposition
    ↓
Implementation
    ↓
AI-assisted review/debugging
    ↓
Automated tests
    ↓
Developer validation
    ↓
Refinement
```

AI-generated suggestions were reviewed and adapted to the application's architecture, database model, package structure, and business rules.

Final decisions regarding architecture, routing policy, retry behavior, testing scope, and production trade-offs were made based on engineering judgment.

## Future Enhancements

Potential production enhancements include:

* External message broker or managed queue
* Distributed delivery workers
* Persistent provider integrations
* Configurable retry policies
* Distributed locking / work claiming
* Authentication and authorization
* Secrets management
* Metrics and distributed tracing
* Dead-letter queue
* Idempotency retention and cleanup
* Provider rate-limit management
* Horizontal scaling

The following sequence diagram illustrates the notification lifecycle from API submission through routing, asynchronous delivery processing, provider invocation, retry/failure handling, status updates, and audit recording.

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant NC as NotificationController
    participant NS as NotificationService
    participant NR as NotificationRepository
    participant AR as AuditService
    participant RR as RecipientRepository
    participant PR as RecipientPreferenceRepository
    participant RS as RoutingService
    participant DR as DeliveryRepository
    participant DP as DeliveryProcessor
    participant NP as NotificationProvider
    participant FH as DeliveryFailureHandler
    participant NSS as NotificationStatusService

    C->>NC: POST /api/v1/notifications
    NC->>NS: submitNotification(request, idempotencyKey)

    NS->>NR: findByIdempotencyKey(key)

    alt Existing notification found
        alt Same request
            NS->>AR: record(DUPLICATE_SUPPRESSED)
            NS-->>NC: NotificationResponse
        else Different request
            NS->>AR: record(NOTIFICATION_REJECTED)
            NS-->>NC: 409 Conflict
        end
    else New notification
        NS->>NR: save(Notification)
        NS->>AR: record(NOTIFICATION_ACCEPTED)

        loop Each recipient
            NS->>RR: save(Recipient)

            loop Each preferred channel
                NS->>PR: save(RecipientPreference)
            end
        end

        NS->>RR: findByNotificationId(notificationId)
        NS->>NR: update status = ROUTING
        NS->>AR: record(ROUTING_STARTED)

        loop Each recipient
            NS->>RS: determineChannels(notification, recipient, requestedChannels)
            RS->>PR: findByRecipientId(recipientId)
            PR-->>RS: RecipientPreferences
            RS-->>NS: Selected channels

            loop Each selected channel
                NS->>DR: save(Delivery)
            end
        end

        NS->>NR: update status = QUEUED
        NS->>AR: record(NOTIFICATION_QUEUED)
        NS-->>NC: NotificationResponse (202 Accepted)
    end

    Note over DP: Scheduled processing every 5 seconds

    DP->>DR: findByStatus(QUEUED)
    DR-->>DP: Queued deliveries

    DP->>DR: findByStatus(RETRY_PENDING)
    DR-->>DP: Retry-pending deliveries

    loop Each ready delivery
        alt Notification expired
            DP->>DR: save(Delivery = EXPIRED)
            DP->>NSS: markExpired(notification)
            NSS->>NR: save(status = EXPIRED)
            DP->>AR: record(NOTIFICATION_EXPIRED)
        else Scheduled for delivery
            DP->>DR: save(Delivery = IN_PROGRESS)
            DP->>AR: record(DELIVERY_ATTEMPTED)

            DP->>NP: send(delivery)

            alt Delivery successful
                NP-->>DP: DeliveryResult(success)
                DP->>DR: save(Delivery = DELIVERED)
                DP->>AR: record(DELIVERY_SUCCEEDED)
                DP->>NSS: update(delivery)
                NSS->>DR: findByNotificationId(notificationId)
                DR-->>NSS: Deliveries
                NSS->>NR: save(updated notification status)
            else Delivery failed
                NP-->>DP: DeliveryResult(failure)
                DP->>FH: handle(delivery, result)

                alt Retryable and attempts remaining
                    FH->>DR: save(RETRY_PENDING)
                    FH->>AR: record(RETRY_SCHEDULED)
                else Non-retryable or max attempts reached
                    FH->>DR: save(FAILED)
                    FH->>AR: record(DELIVERY_FAILED)
                    FH->>NSS: update(delivery)
                    NSS->>DR: findByNotificationId(notificationId)
                    DR-->>NSS: Deliveries
                    NSS->>NR: save(updated notification status)
                end
            end
        end
    end

    NC-->>C: HTTP Response