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

    // JSON converter - turns Order objects into JSON strings
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    // Random generator for test data
    private static final Random random = new Random();
    
    // Test data arrays
    private static final String[] REGIONS = {"US-EAST", "US-WEST", "EU", "ASIA"};
    private static final String[] PRIORITIES = {"NORMAL", "HIGH", "URGENT"};
    private static final String[] PRODUCTS = {"Laptop", "Mouse", "Keyboard", "Monitor", "Headphones"};


    public static void main(String[] args) {
        System.out.println("=== Order Publisher Starting ===");

        JCSMPSession session = null;

        try {
            // ============================================================
            // STEP 1: CREATE CONNECTION PROPERTIES
            // ============================================================
            // This tells Solace WHERE to connect and WHO you are

            JCSMPProperties properties = new JCSMPProperties();
            properties.setProperty(JCSMPProperties.HOST, "localhost:55556");  // Our Docker port
            properties.setProperty(JCSMPProperties.VPN_NAME, "default");      // Virtual broker
            properties.setProperty(JCSMPProperties.USERNAME, "admin");
            properties.setProperty(JCSMPProperties.PASSWORD, "admin");

            System.out.println("Connecting to Solace broker...");

            // ============================================================
            // STEP 2: CREATE AND CONNECT SESSION
            // ============================================================
            // Session = your connection to the broker

            session = JCSMPFactory.onlyInstance().createSession(properties);
            session.connect();

            System.out.println("✓ Connected successfully!");

            // ============================================================
            // STEP 3: CREATE MESSAGE PRODUCER
            // ============================================================
            // Producer = the thing that sends messages

            XMLMessageProducer producer = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
                @Override
                public void responseReceivedEx(Object messageID) {
                    // Broker confirmed it received our message
                    System.out.println("  ✓ Broker confirmed: " + messageID);
                }

                @Override
                public void handleErrorEx(Object messageID, JCSMPException cause, long timestamp) {
                    // Something went wrong
                    System.out.println("  ✗ Error: " + cause.getMessage());
                }
            });

            System.out.println("✓ Producer created!");
            System.out.println("\nPublishing orders (Ctrl+C to stop)...\n");

            // ============================================================
            // STEP 4: PUBLISH MESSAGES IN A LOOP
            // ============================================================

            for (int i = 0; i < 10; i++) {
                Order order = createRandomOrder();
                publishOrder(producer, order);
                Thread.sleep(2000);  // Wait 2 seconds between orders
            }

            System.out.println("\n=== Published 10 orders! ===");

        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // ============================================================
            // STEP 5: CLEANUP - Always close your connection!
            // ============================================================
            if (session != null) {
                session.closeSession();
                System.out.println("Session closed.");
            }
        }
    }
}
