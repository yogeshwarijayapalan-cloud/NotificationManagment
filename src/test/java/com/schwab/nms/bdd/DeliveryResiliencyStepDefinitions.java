package com.schwab.nms.bdd;

import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.database.entities.enums.FailureType;
import com.schwab.nms.database.entities.AuditEvent;
import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.AuditEventType;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.modules.notification.model.NotificationRequest;
import com.schwab.nms.modules.notification.model.RecipientRequest;
import com.schwab.nms.database.repository.AuditEventRepository;
import com.schwab.nms.database.repository.DeliveryRepository;
import com.schwab.nms.database.repository.NotificationRepository;
import com.schwab.nms.modules.delivery.service.DeliveryProcessor;
import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

public class DeliveryResiliencyStepDefinitions {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private DeliveryProcessor deliveryProcessor;

    @Autowired
    private TestNotificationProvider testNotificationProvider;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;
    
    private MockMvc mockMvc;

    private UUID notificationId;

    private Delivery testDelivery;

    @Autowired
    private TestDatabaseHooks testDatabaseHooks;

    @Before
    public void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();

        testNotificationProvider.setFailTransiently(false);
        testNotificationProvider.setFailPermanently(false);
    }

    @Given("a notification with a queued EMAIL delivery")
    public void a_notification_with_a_queued_email_delivery() throws Exception {

        NotificationRequest request = new NotificationRequest("PAYMENT_SYSTEM", "RESILIENCY-" + UUID.randomUUID(),
                "PAYMENT_ALERT", "LOW", "NORMAL", "Resiliency test notification.",
                List.of(new RecipientRequest("TEST-USER-" + UUID.randomUUID(), "test@example.com", "+12145551234", List.of("EMAIL"))),
                List.of("EMAIL"), null, null);

        String requestJson = objectMapper.writeValueAsString(request);

        var result = mockMvc.perform(post("/api/v1/notifications").header("Idempotency-Key", "RESILIENCY-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(requestJson)).andReturn();

        assertEquals(202, result.getResponse().getStatus());

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

        notificationId = UUID.fromString(response.get("notificationId").asString());
        testDatabaseHooks.trackNotification(notificationId);
        List<Delivery> deliveries = deliveryRepository.findByNotificationId(notificationId);

        assertEquals(1, deliveries.size());

        testDelivery = deliveries.getFirst();

        assertEquals(Channel.EMAIL, testDelivery.getChannel());
        assertEquals(DeliveryStatus.QUEUED, testDelivery.getStatus());
        assertEquals(0, testDelivery.getAttemptCount());
    }

    @When("the delivery batch processor runs")
    public void the_delivery_batch_processor_runs() {
        deliveryProcessor.processQueuedDeliveries();
        testDelivery = deliveryRepository.findById(testDelivery.getId()).orElseThrow();
    }

    @Given("the EMAIL provider returns a transient failure")
    public void the_email_provider_returns_a_transient_failure() {
        testNotificationProvider.setFailTransiently(true);
    }

    @Given("the EMAIL provider returns a permanent failure")
    public void the_email_provider_returns_a_permanent_failure() {
        testNotificationProvider.setFailPermanently(true);
    }

    @Then("the delivery status should be {string}")
    public void the_delivery_status_should_be(String expectedStatus) {
        assertEquals(DeliveryStatus.valueOf(expectedStatus), testDelivery.getStatus());
    }

    @Then("the attempt count should be {int}")
    public void the_attempt_count_should_be(int expectedAttemptCount) {
        assertEquals(expectedAttemptCount, testDelivery.getAttemptCount());
    }

    @Then("a retry should be scheduled")
    public void a_retry_should_be_scheduled() {
        assertNotNull(testDelivery.getNextRetryAt(), "Expected a retry to be scheduled");
    }

    @Then("the delivery should not be scheduled for retry")
    public void the_delivery_should_not_be_scheduled_for_retry() {
        assertNull(testDelivery.getNextRetryAt(), "Delivery should not have a retry scheduled");
    }

    @Given("the EMAIL provider continues to return a transient failure")
    public void the_email_provider_continues_to_return_a_transient_failure() {
        testNotificationProvider.setFailTransiently(true);
    }

    @When("the delivery batch processor runs until the maximum retry count")
    public void the_delivery_batch_processor_runs_until_the_maximum_retry_count() {
        for (int i = 0; i < 3; i++) {
            deliveryProcessor.processQueuedDeliveries();
            testDelivery = deliveryRepository.findById(testDelivery.getId()).orElseThrow();

            // After the third attempt, the delivery should be FAILED.
            if (testDelivery.getStatus() == DeliveryStatus.FAILED) {
                break;
            }

            // For retry attempts, make the retry immediately eligible
            // instead of waiting for the real backoff period.
            testDelivery.setNextRetryAt(java.time.Instant.now());
            deliveryRepository.save(testDelivery);
        }
    }

    @Then("the failure type should be {string}")
    public void the_failure_type_should_be(String expectedFailureType) {
        assertEquals(FailureType.valueOf(expectedFailureType), testDelivery.getFailureType());
    }

    @Then("the audit history should contain {string}")
    public void the_audit_history_should_contain(String expectedEventType) {
        List<AuditEvent> events = auditEventRepository.findByNotificationIdOrderByEventTimeAsc(notificationId);

        boolean found = events.stream().anyMatch(event ->
                event.getEventType().equals(AuditEventType.valueOf(expectedEventType)));

        assertTrue(found, "Expected audit event not found: " + expectedEventType);
    }

    @Given("a notification with a queued PUSH delivery")
    public void a_notification_with_a_queued_push_delivery() throws Exception {
        NotificationRequest request = new NotificationRequest("PAYMENT_SYSTEM", "PUSH-RESILIENCY-" + UUID.randomUUID(),
                "PAYMENT_ALERT", "LOW", "NORMAL", "Push resiliency test notification.",
                List.of(new RecipientRequest("TEST-USER-" + UUID.randomUUID(), "test@example.com", "+12145551234", List.of("PUSH")))
                , List.of("PUSH"), null, null);

        String requestJson = objectMapper.writeValueAsString(request);

        var result = mockMvc.perform(post("/api/v1/notifications").header("Idempotency-Key", "PUSH-RESILIENCY-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(requestJson)).andReturn();
        assertEquals(202, result.getResponse().getStatus());

        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        notificationId = UUID.fromString(response.get("notificationId").asString());
        List<Delivery> deliveries = deliveryRepository.findByNotificationId(notificationId);
        testDatabaseHooks.trackNotification(notificationId);
        assertEquals(1, deliveries.size());

        testDelivery = deliveries.getFirst();
        assertEquals(Channel.PUSH, testDelivery.getChannel());
        assertEquals(DeliveryStatus.QUEUED, testDelivery.getStatus());
    }

    @Given("a notification scheduled for the future")
    public void a_notification_scheduled_for_the_future() throws Exception {
        createNotification(java.time.Instant.now().plusSeconds(3600), null);
    }

    @Given("a notification that has expired")
    public void a_notification_that_has_expired() throws Exception {
        createNotification(java.time.Instant.now().minusSeconds(3600), java.time.Instant.now().minusSeconds(60));
    }
    
    @Then("the notification should have status {string}")
    public void the_notification_should_have_status(String expectedStatus) {
        var notification = notificationRepository.findById(notificationId).orElseThrow();
        assertEquals(expectedStatus, notification.getStatus().name());
    }

    private void createNotification(java.time.Instant scheduledAt, java.time.Instant expiresAt) throws Exception {

        NotificationRequest request = new NotificationRequest("PAYMENT_SYSTEM", "SCHEDULED-" + UUID.randomUUID(),
                "PAYMENT_ALERT", "LOW", "NORMAL", "Scheduled notification test.",
                List.of(new RecipientRequest("TEST-USER-" + UUID.randomUUID(), "test@example.com", "+12145551234", List.of("EMAIL"))),
                List.of("EMAIL"), scheduledAt, expiresAt);

        var result = mockMvc.perform(post("/api/v1/notifications").header("Idempotency-Key", "SCHEDULED-" + UUID.randomUUID()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andReturn();
        assertEquals(202, result.getResponse().getStatus());
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        notificationId = UUID.fromString(response.get("notificationId").asString());
        testDatabaseHooks.trackNotification(notificationId);
        List<Delivery> deliveries = deliveryRepository.findByNotificationId(notificationId);
        assertEquals(1, deliveries.size());

        testDelivery = deliveries.getFirst();
        assertEquals(DeliveryStatus.QUEUED, testDelivery.getStatus());
    }

}