package com.aamir.service;


import com.aamir.entity.InventoryItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final JavaMailSender mailSender;

    /**
     * Used by a DELAYED job: e.g. "remind customer 24h after dispatch to leave feedback".
     */
    public void sendOrderConfirmationEmail(Long orderId, String customerEmail) {
        log.info("Sending order confirmation email for order {} to {}", orderId, customerEmail);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(customerEmail);
        message.setSubject("Your order #" + orderId + " has been confirmed");
        message.setText("Hi, your order is confirmed and will be processed shortly.");
        try {
            mailSender.send(message);
        } catch (Exception e) {
            // JobRunr will retry automatically based on the @Job(retries=) policy;
            // rethrow so the job is marked FAILED and retried, not silently swallowed.
            log.error("Failed to send confirmation email, will be retried by JobRunr", e);
            throw new RuntimeException(e);
        }
    }

    public void sendLowStockAlert(List<InventoryItem> lowStockItems) {
        log.info("ALERT: {} SKUs are below reorder threshold: {}",
                lowStockItems.size(),
                lowStockItems.stream().map(InventoryItem::getSku).toList());
        // In production: push to Slack webhook / PagerDuty / email distribution list.
    }

    public void sendDispatchReport(String reportSummary) {
        log.info("Midday dispatch report generated:\n{}", reportSummary);
    }
}