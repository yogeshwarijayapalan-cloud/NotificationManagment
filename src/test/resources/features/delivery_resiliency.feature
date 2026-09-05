Feature: Delivery Resiliency

  Scenario: Successfully deliver a queued notification
    Given a notification with a queued EMAIL delivery
    When the delivery batch processor runs
    Then the delivery status should be "DELIVERED"
    And the attempt count should be 1

  Scenario: Retry a transient provider failure
    Given a notification with a queued EMAIL delivery
    And the EMAIL provider returns a transient failure
    When the delivery batch processor runs
    Then the delivery status should be "RETRY_PENDING"
    And the attempt count should be 1
    And a retry should be scheduled

  Scenario: Permanently fail after maximum retries
    Given a notification with a queued EMAIL delivery
    And the EMAIL provider continues to return a transient failure
    When the delivery batch processor runs until the maximum retry count
    Then the delivery status should be "FAILED"
    And the attempt count should be 3
    And the failure type should be "TRANSIENT"

  Scenario: Do not retry a permanent provider failure
    Given a notification with a queued EMAIL delivery
    And the EMAIL provider returns a permanent failure
    When the delivery batch processor runs
    Then the delivery status should be "FAILED"
    And the attempt count should be 1
    And the failure type should be "PERMANENT"
    And the delivery should not be scheduled for retry

  Scenario: Successfully deliver a queued PUSH notification
    Given a notification with a queued PUSH delivery
    When the delivery batch processor runs
    Then the delivery status should be "DELIVERED"
    And the attempt count should be 1

  Scenario: Do not deliver a notification before its scheduled time
    Given a notification scheduled for the future
    When the delivery batch processor runs
    Then the delivery status should be "QUEUED"
    And the attempt count should be 0

  Scenario: Expire a notification past its expiration time
    Given a notification that has expired
    When the delivery batch processor runs
    Then the notification should have status "EXPIRED"
    And the delivery status should be "EXPIRED"
    And the attempt count should be 0