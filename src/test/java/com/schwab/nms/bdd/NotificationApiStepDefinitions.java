package com.schwab.nms.bdd;

import com.schwab.nms.database.entities.Delivery;
import com.schwab.nms.database.entities.enums.Channel;
import com.schwab.nms.database.entities.enums.DeliveryStatus;
import com.schwab.nms.modules.notification.model.NotificationRequest;
import com.schwab.nms.modules.notification.model.RecipientRequest;
import com.schwab.nms.database.repository.DeliveryRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

public class NotificationApiStepDefinitions {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private TestDatabaseHooks testDatabaseHooks;

    private MockMvc mockMvc;
    private NotificationRequest request;
    private String responseBody;
    private int responseStatus;
    private UUID notificationId;
    private UUID originalNotificationId;
    private int originalDeliveryCount;

    @Before
    public void setUp() {
        mockMvc = webAppContextSetup(webApplicationContext).build();
    }

    // -------------------------------------------------------------------------
    // Request setup
    // -------------------------------------------------------------------------

    @Given("a valid notification request")
    public void a_valid_notification_request() {
        request = new NotificationRequest("PAYMENT_SYSTEM", "PAY-123", "PAYMENT_ALERT", "HIGH",
                "HIGH", "Payment requires your attention.",
                List.of(new RecipientRequest("USER-1001", "user@example.com", "+12145551234",
                        List.of("EMAIL", "SMS"))), List.of("EMAIL", "SMS"), null, null);
    }

    @Given("an invalid notification request")
    public void an_invalid_notification_request() {
        request = new NotificationRequest("", "", "", "HIGH", "HIGH", ""
                , List.of(), List.of(), null, null);
    }

    @Given("a valid notification request with PUSH channel")
    public void a_valid_notification_request_with_push_channel() {
        request = new NotificationRequest("PAYMENT_SYSTEM", "PUSH-" + UUID.randomUUID(), "PAYMENT_ALERT", "HIGH", "HIGH", "Push notification test.", List.of(new RecipientRequest("USER-1001", "user@example.com", "+12145551234", List.of("PUSH"))), List.of("PUSH"), null, null);
    }

    // -------------------------------------------------------------------------
    // API actions
    // -------------------------------------------------------------------------

    @When("I submit the notification with idempotency key {string}")
    public void i_submit_the_notification_with_idempotency_key(String idempotencyKey) throws Exception {
        submitRequest(request, idempotencyKey);
    }

    @When("I submit the same notification again with idempotency key {string}")
    public void i_submit_the_same_notification_again_with_idempotency_key(String idempotencyKey) throws Exception {
        submitRequest(request, idempotencyKey);
    }

    @When("I submit a different notification with idempotency key {string}")
    public void i_submit_a_different_notification_with_idempotency_key(String idempotencyKey) throws Exception {
        NotificationRequest differentRequest = new NotificationRequest("DIFFERENT_SYSTEM", "DIFFERENT-EVENT",
                "DIFFERENT_ALERT", "LOW", "NORMAL","This is a different notification.",
                request.recipients(), request.requestedChannels(), request.scheduledAt(), request.expiresAt());
        submitRequest(differentRequest, idempotencyKey);
    }

    @When("I retrieve the notification status")
    public void i_retrieve_the_notification_status() throws Exception {
        assertNotNull(notificationId, "Notification ID must be available");
        var result = mockMvc.perform(get("/api/v1/notifications/" + notificationId)).andReturn();
        responseStatus = result.getResponse().getStatus();
        responseBody = result.getResponse().getContentAsString();
    }

    // -------------------------------------------------------------------------
    // Response assertions
    // -------------------------------------------------------------------------

    @Then("the HTTP response status should be {int}")
    public void the_http_response_status_should_be(int expectedStatus) {
        assertEquals(expectedStatus, responseStatus);
    }

    @Then("the response should contain a notification id")
    public void the_response_should_contain_a_notification_id() {
        JsonNode response = objectMapper.readTree(responseBody);
        assertTrue(response.hasNonNull("notificationId"));
        notificationId = UUID.fromString(response.get("notificationId").asString());
    }

    @Then("the notification status should be {string}")
    public void the_notification_status_should_be(String expectedStatus) {
        JsonNode response = objectMapper.readTree(responseBody);
        assertEquals(expectedStatus, response.get("status").asString());
    }

    @Then("the response should contain the notification status")
    public void the_response_should_contain_the_notification_status() {
        JsonNode response = objectMapper.readTree(responseBody);
        assertTrue(response.hasNonNull("status"));
    }

    @Then("the response should contain delivery information")
    public void the_response_should_contain_delivery_information() {
        JsonNode response = objectMapper.readTree(responseBody);
        assertTrue(response.has("deliveries"));
        assertTrue(response.get("deliveries").isArray());
        assertFalse(response.get("deliveries").isEmpty());
    }

    @Then("delivery records should exist for the notification")
    public void delivery_records_should_exist_for_the_notification() {
        assertNotNull(notificationId);
        List<Delivery> deliveries = deliveryRepository.findByNotificationId(notificationId);
        assertFalse(deliveries.isEmpty(), "Expected delivery records to exist");
    }

    // -------------------------------------------------------------------------
    // Idempotency assertions
    // -------------------------------------------------------------------------

    @Then("I save the notification id")
    public void i_save_the_notification_id() {
        JsonNode response = objectMapper.readTree(responseBody);
        originalNotificationId = UUID.fromString(response.get("notificationId").asString());
        originalDeliveryCount = deliveryRepository.findByNotificationId(originalNotificationId).size();
    }

    @Then("the notification id should be the same")
    public void the_notification_id_should_be_the_same() {
        JsonNode response = objectMapper.readTree(responseBody);
        UUID returnedNotificationId = UUID.fromString(response.get("notificationId").asString());
        assertEquals(originalNotificationId, returnedNotificationId);
    }

    @Then("no duplicate delivery records should be created")
    public void no_duplicate_delivery_records_should_be_created() {
        int currentDeliveryCount = deliveryRepository.findByNotificationId(originalNotificationId).size();
        assertEquals(originalDeliveryCount, currentDeliveryCount);
    }

    // -------------------------------------------------------------------------
    // Delivery assertions
    // -------------------------------------------------------------------------

    @Then("the notification should have {int} queued deliveries")
    public void the_notification_should_have_queued_deliveries(int expectedCount) {
        assertNotNull(notificationId);
        List<Delivery> deliveries = deliveryRepository.findByNotificationId(notificationId);
        assertEquals(expectedCount, deliveries.size());
        assertTrue(deliveries.stream().allMatch(d -> d.getStatus() == DeliveryStatus.QUEUED), "Expected all deliveries to be QUEUED, but actual statuses were: "
                + deliveries.stream().map(d -> d.getChannel() + "=" + d.getStatus()).toList());
    }

    @Then("the queued delivery channels should be EMAIL and SMS")
    public void the_queued_delivery_channels_should_be_email_and_sms() {
        assertNotNull(notificationId);
        List<Delivery> deliveries = deliveryRepository.findByNotificationId(notificationId);
        assertEquals(2, deliveries.size());
        assertTrue(deliveries.stream().anyMatch(d -> d.getChannel() == Channel.EMAIL), "Expected an EMAIL delivery");
        assertTrue(deliveries.stream().anyMatch(d -> d.getChannel() == Channel.SMS), "Expected an SMS delivery");
        assertTrue(deliveries.stream().allMatch(d -> d.getStatus() == DeliveryStatus.QUEUED), "Expected all deliveries to be QUEUED");
    }

    @Then("the delivery channel should be {string}")
    public void the_delivery_channel_should_be(String expectedChannel) {
        assertNotNull(notificationId);
        List<Delivery> deliveries = deliveryRepository.findByNotificationId(notificationId);
        assertEquals(1, deliveries.size());
        assertEquals(Channel.valueOf(expectedChannel), deliveries.getFirst().getChannel());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void submitRequest(NotificationRequest request, String idempotencyKey) throws Exception {
        String requestJson = objectMapper.writeValueAsString(request);
        var result = mockMvc.perform(post("/api/v1/notifications").header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON).content(requestJson)).andReturn();

        responseStatus = result.getResponse().getStatus();
        responseBody = result.getResponse().getContentAsString();

        if (responseStatus == 202) {
            JsonNode response = objectMapper.readTree(responseBody);
            notificationId = UUID.fromString(response.get("notificationId").asString());
            testDatabaseHooks.trackNotification(notificationId);
        }
    }
}