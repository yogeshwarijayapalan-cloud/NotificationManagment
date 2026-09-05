Feature: Notification Management API

  Scenario: Successfully submit a notification
    Given a valid notification request
    When I submit the notification with idempotency key "BDD-PAY-001"
    Then the HTTP response status should be 202
    And the response should contain a notification id
    And the notification status should be "QUEUED"
    And delivery records should exist for the notification

  Scenario: Retrieve notification status
    Given a valid notification request
    When I submit the notification with idempotency key "BDD-STATUS-001"
    Then the HTTP response status should be 202
    And the response should contain a notification id
    When I retrieve the notification status
    Then the HTTP response status should be 200
    And the response should contain the notification status
    And the response should contain delivery information

  Scenario: Duplicate notification submission is idempotent
    Given a valid notification request
    When I submit the notification with idempotency key "BDD-DUP-001"
    Then the HTTP response status should be 202
    And I save the notification id
    And I submit the same notification again with idempotency key "BDD-DUP-001"
    Then the HTTP response status should be 202
    And the notification id should be the same
    And no duplicate delivery records should be created

  Scenario: Reject an invalid notification request
    Given an invalid notification request
    When I submit the notification with idempotency key "BDD-INVALID-001"
    Then the HTTP response status should be 400

  Scenario: Queue deliveries for requested channels
    Given a valid notification request
    When I submit the notification with idempotency key "BDD-QUEUE-001"
    Then the HTTP response status should be 202
    And the response should contain a notification id
    And the notification should have 2 queued deliveries
    And the queued delivery channels should be EMAIL and SMS

  Scenario: Queue PUSH delivery when PUSH channel is requested
    Given a valid notification request with PUSH channel
    When I submit the notification with idempotency key "BDD-PUSH-001"
    Then the HTTP response status should be 202
    And the response should contain a notification id
    And the notification status should be "QUEUED"
    And the delivery channel should be "PUSH"

  Scenario: Reject idempotency key reuse with a different request
    Given a valid notification request
    When I submit the notification with idempotency key "BDD-CONFLICT-001"
    Then the HTTP response status should be 202
    And I submit a different notification with idempotency key "BDD-CONFLICT-001"
    Then the HTTP response status should be 409