Feature: Notification Audit History

  Scenario: Record audit history for successful delivery
    Given a notification with a queued EMAIL delivery
    When the delivery batch processor runs
    Then the audit history should contain "NOTIFICATION_ACCEPTED"
    And the audit history should contain "DELIVERY_ATTEMPTED"
    And the audit history should contain "DELIVERY_SUCCEEDED"