package com.solace.practice.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solace.practice.model.Order;
import com.solace.practice.model.OrderStatus;
import com.solacesystems.jcsmp.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Random;
import java.util.UUID;

public class OrderPublisher {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private static final Random random = new Random();

    private static final String[] REGIONS = {"US-EAST", "US-WEST", "EU", "ASIA"};
    private static final String[] PRIORITIES = {"NORMAL", "HIGH", "URGENT"};
    private static final String[] PRODUCTS = {"Laptop", "Mouse", "Keyboard", "Monitor", "Headphones"};

    public static void main(String[] args) {

        System.out.println("=== Order Publisher Starting ===");

        JCSMPSession session = null;

        try {
            // Step 1: Create properties object
            JCSMPProperties properties = new JCSMPProperties();

            // Step 2: Set connection properties
            properties.setProperty(JCSMPProperties.HOST, "localhost:55556");
            properties.setProperty(JCSMPProperties.VPN_NAME, "default");
            properties.setProperty(JCSMPProperties.USERNAME, "admin");
            properties.setProperty(JCSMPProperties.PASSWORD, "admin");

            System.out.println("Connecting to Solace broker...");

            // Step 3: Create session and connect
            session = JCSMPFactory.onlyInstance().createSession(properties);
            session.connect();

            System.out.println("✓ Connected to Solace!");

            // Step 4: Create producer with callback handler
            XMLMessageProducer producer = session.getMessageProducer(
                    new JCSMPStreamingPublishEventHandler() {
                        @Override
                        public void responseReceived(String messageID) {
                            System.out.println("  ✓ Broker confirmed: " + messageID);
                        }

                        @Override
                        public void handleError(String messageID, JCSMPException cause, long timestamp) {
                            System.out.println("  ✗ Error: " + cause.getMessage());
                        }
                    }
            );

            System.out.println("✓ Producer created!");
            System.out.println("\n=== Connection test successful! ===\n");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();

        } finally {
            // Step 5: Cleanup
            if (session != null) {
                session.closeSession();
                System.out.println("Session closed.");
            }
        }
    }
}