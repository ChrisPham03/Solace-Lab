# 🚀 Advanced Solace Scenarios - Real-World Practice

Now that you've passed the certification, let's tackle **real production scenarios** that will make you a confident Solace developer.

---

## 📋 Scenario Overview

| Scenario | Difficulty | Time | Skills Practiced |
|----------|-----------|------|------------------|
| 1. High-Value Order Alerting | ⭐⭐ Easy | 30 min | Message Selectors, Priority Handling |
| 2. Request-Reply Validation | ⭐⭐⭐ Medium | 60 min | Synchronous Patterns, Correlation IDs |
| 3. Multi-Region Load Balancing | ⭐⭐⭐ Medium | 45 min | Non-Exclusive Queues, Consumer Groups |
| 4. Dead Letter Queue Monitoring | ⭐⭐⭐⭐ Hard | 75 min | Error Handling, Replay, DLQ Patterns |
| 5. Circuit Breaker Pattern | ⭐⭐⭐⭐⭐ Expert | 90 min | Flow Control, Backpressure, Resilience |

---

## 🎯 Scenario 1: High-Value Order Alerting System

### **Business Problem**
Your company needs immediate alerts when high-value orders (>$5,000) are placed. These should bypass normal processing and go to a priority queue for manual review.

### **What You'll Build**
- Priority queue for high-value orders
- Message selector filtering by amount
- Separate consumer with faster processing
- SMS/Email notification simulation

### **Architecture**
```
Order Publisher
      │
      ├──> Regular Queue ────> Normal Dashboard
      │    (amount <= 5000)
      │
      └──> Priority Queue ───> High-Value Alert Service
           (amount > 5000)     (YOU'LL BUILD THIS)
```

### **Implementation Steps**

#### Step 1: Create Priority Queue Consumer

Create `/admin-dashboard/src/main/java/com/solace/practice/dashboard/HighValueAlertService.java`:

```java
package com.solace.practice.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solace.practice.model.Order;
import com.solacesystems.jcsmp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

/**
 * High-Value Order Alert Service
 * 
 * ADVANCED PATTERN: Message Selector with Priority Queue
 * 
 * KEY CONCEPTS:
 * - Message selectors filter at broker level (more efficient)
 * - Priority queues can have different TTL, quota settings
 * - Separate consumers allow independent scaling
 */
public class HighValueAlertService {
    
    private static final Logger logger = LoggerFactory.getLogger(HighValueAlertService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    private static final String PRIORITY_QUEUE = "high-value-orders";
    private static final double HIGH_VALUE_THRESHOLD = 5000.0;
    
    public static void main(String[] args) throws Exception {
        logger.info("=== High-Value Order Alert Service Starting ===");
        
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, "localhost:55555");
        properties.setProperty(JCSMPProperties.VPN_NAME, "default");
        properties.setProperty(JCSMPProperties.USERNAME, "admin");
        properties.setProperty(JCSMPProperties.PASSWORD, "admin");
        
        JCSMPSession session = null;
        FlowReceiver flowReceiver = null;
        
        try {
            session = JCSMPFactory.onlyInstance().createSession(properties);
            session.connect();
            logger.info("✓ Connected to Solace broker");
            
            // Provision priority queue
            Queue queue = provisionPriorityQueue(session);
            
            // Add subscriptions
            addQueueSubscriptions(session, queue);
            
            // Create flow with message selector
            flowReceiver = createFlowWithSelector(session, queue);
            flowReceiver.start();
            
            logger.info("✓ Listening for high-value orders (>${})...\n", HIGH_VALUE_THRESHOLD);
            
            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
            latch.await();
            
        } finally {
            if (flowReceiver != null) flowReceiver.close();
            if (session != null) session.closeSession();
        }
    }
    
    /**
     * LESSON: Priority queues can have different configurations
     * - Lower TTL (messages expire faster)
     * - Higher priority consumers
     * - Different access types
     */
    private static Queue provisionPriorityQueue(JCSMPSession session) throws JCSMPException {
        logger.info("Provisioning priority queue: {}", PRIORITY_QUEUE);
        
        final EndpointProperties endpointProps = new EndpointProperties();
        endpointProps.setPermission(EndpointProperties.PERMISSION_CONSUME);
        endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
        
        // IMPORTANT: Configure for high-priority processing
        endpointProps.setQuotaMB(50);  // Smaller quota - these should process fast
        endpointProps.setMaxMsgRedelivery(1);  // Fewer retries for time-sensitive
        
        final Queue queue = JCSMPFactory.onlyInstance().createQueue(PRIORITY_QUEUE);
        session.provision(queue, endpointProps, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
        
        logger.info("✓ Priority queue provisioned: {}", PRIORITY_QUEUE);
        return queue;
    }
    
    private static void addQueueSubscriptions(JCSMPSession session, Queue queue) throws JCSMPException {
        // Subscribe to all orders - selector will filter
        Topic allOrders = JCSMPFactory.onlyInstance().createTopic("order/v1/>");
        session.addSubscription(queue, allOrders, JCSMPSession.WAIT_FOR_CONFIRM);
        logger.info("✓ Subscribed to: order/v1/> with selector filter");
    }
    
    /**
     * LESSON: Message Selectors
     * 
     * Selectors filter messages at the BROKER level, not client level.
     * This is much more efficient - broker doesn't send messages that don't match.
     * 
     * SQL-like syntax:
     * - Operators: =, <>, <, >, <=, >=, BETWEEN, IN, NOT, AND, OR
     * - String: 'value' (single quotes)
     * - Number: no quotes
     * 
     * PERFORMANCE TIP: Index properties used in selectors for best performance
     */
    private static FlowReceiver createFlowWithSelector(JCSMPSession session, Queue queue) 
            throws JCSMPException {
        
        final ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
        flowProps.setEndpoint(queue);
        flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);
        
        // THE KEY PART: Message selector
        // IMPORTANT: Property name must match what publisher sets
        String selector = String.format("totalAmount > %f", HIGH_VALUE_THRESHOLD);
        flowProps.setSelector(selector);
        
        logger.info("Using selector: {}", selector);
        
        // Message listener
        XMLMessageListener listener = new XMLMessageListener() {
            @Override
            public void onReceive(BytesXMLMessage message) {
                try {
                    // Parse order
                    String payload = new String(message.getBytes());
                    Order order = objectMapper.readValue(payload, Order.class);
                    
                    // Get properties
                    Double totalAmount = message.getProperties().getDouble("totalAmount");
                    String priority = message.getProperties().getString("priority");
                    String region = message.getProperties().getString("region");
                    
                    // ALERT! High-value order
                    logger.warn("🚨 HIGH-VALUE ALERT 🚨");
                    logger.warn("   Order ID: {}", order.getOrderId());
                    logger.warn("   Customer: {}", order.getCustomerId());
                    logger.warn("   Amount: ${}", totalAmount);
                    logger.warn("   Region: {}", region);
                    logger.warn("   Priority: {}", priority);
                    logger.warn("   Product: {} x{}", order.getProductId(), order.getQuantity());
                    
                    // Simulate sending alert
                    sendAlert(order, totalAmount);
                    
                    // Acknowledge
                    message.ackMessage();
                    logger.info("✓ Alert processed and acknowledged\n");
                    
                } catch (Exception e) {
                    logger.error("Error processing high-value order: ", e);
                    // In production: send to DLQ or alert ops team
                }
            }
            
            @Override
            public void onException(JCSMPException e) {
                logger.error("Flow exception: ", e);
            }
        };
        
        return session.createFlow(listener, flowProps, null);
    }
    
    /**
     * LESSON: Real-world alert simulation
     * In production, this would:
     * - Send email via SendGrid/SES
     * - Send SMS via Twilio
     * - Create Slack notification
     * - Create PagerDuty alert
     */
    private static void sendAlert(Order order, Double amount) {
        // Simulate delay
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info("📧 Email sent to: fraud-team@company.com");
        logger.info("📱 SMS sent to: +1-555-FRAUD");
        logger.info("💬 Slack notification posted to: #high-value-orders");
    }
}
```

#### Step 2: Run the Scenario

**Terminal 1**: Start your high-value alert service
```bash
cd admin-dashboard
mvn compile
mvn exec:java -Dexec.mainClass="com.solace.practice.dashboard.HighValueAlertService"
```

**Terminal 2**: Keep regular dashboard running (optional)
```bash
mvn exec:java -Dexec.mainClass="com.solace.practice.dashboard.AdminDashboard"
```

**Terminal 3**: Publish orders (including high-value ones)
```bash
cd ../order-publisher
mvn exec:java -Dexec.mainClass="com.solace.practice.publisher.OrderPublisher"
```

### **Expected Output**

```
🚨 HIGH-VALUE ALERT 🚨
   Order ID: ORD-7734
   Customer: CUST-1045
   Amount: $7999.98
   Region: US-EAST
   Priority: URGENT
   Product: Enterprise-License x1
   
📧 Email sent to: fraud-team@company.com
📱 SMS sent to: +1-555-FRAUD
💬 Slack notification posted to: #high-value-orders
✓ Alert processed and acknowledged
```

### **Key Learnings**
- ✅ Message selectors reduce network traffic (filtering at broker)
- ✅ Multiple queues allow independent scaling
- ✅ Properties must be set by publisher to use in selectors
- ✅ Different queues can have different configurations

### **Challenge Extensions**
1. Add multiple selector criteria (amount AND region)
2. Implement escalation (if not processed in 5 mins, page someone)
3. Add rate limiting (max 10 alerts per minute)
4. Create alert dashboard showing recent high-value orders

---

## 🎯 Scenario 2: Request-Reply Order Validation

### **Business Problem**
Before accepting an order, you need synchronous validation from the inventory service. If items aren't in stock, reject the order immediately.

### **What You'll Build**
- Synchronous request-reply pattern
- Correlation ID tracking
- Timeout handling
- Inventory validation service

### **Architecture**
```
Order Publisher                    Inventory Validator
      │                                   │
      │──────► Request (with ReplyTo) ───>│
      │         (order/validate)           │
      │                                   │
      │◄───────────── Reply ──────────────│
      │         (order/reply/{correlationId})
      │
      └─────> Proceed or Reject
```

### **Implementation Steps**

#### Step 1: Create Inventory Validator Service

Create `/admin-dashboard/src/main/java/com/solace/practice/validation/InventoryValidator.java`:

```java
package com.solace.practice.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solace.practice.model.Order;
import com.solacesystems.jcsmp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

/**
 * Inventory Validator Service
 * 
 * ADVANCED PATTERN: Request-Reply (Synchronous Messaging)
 * 
 * KEY CONCEPTS:
 * - ReplyTo header specifies where to send response
 * - Correlation ID links request and response
 * - Timeout handling prevents hanging clients
 * - This is synchronous behavior over async transport
 */
public class InventoryValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(InventoryValidator.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final Random random = new Random();
    
    // Simulated inventory database
    private static final Map<String, Integer> INVENTORY = new HashMap<>();
    
    static {
        INVENTORY.put("Laptop", 50);
        INVENTORY.put("Monitor", 100);
        INVENTORY.put("Keyboard", 200);
        INVENTORY.put("Mouse", 150);
        INVENTORY.put("Headphones", 75);
        INVENTORY.put("Webcam", 30);
        INVENTORY.put("USB-Cable", 500);
        INVENTORY.put("Dock-Station", 25);
        INVENTORY.put("External-SSD", 60);
        INVENTORY.put("Enterprise-License", 10);
    }
    
    public static void main(String[] args) throws Exception {
        logger.info("=== Inventory Validator Service Starting ===");
        
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, "localhost:55555");
        properties.setProperty(JCSMPProperties.VPN_NAME, "default");
        properties.setProperty(JCSMPProperties.USERNAME, "admin");
        properties.setProperty(JCSMPProperties.PASSWORD, "admin");
        
        JCSMPSession session = null;
        XMLMessageConsumer consumer = null;
        XMLMessageProducer producer = null;
        
        try {
            session = JCSMPFactory.onlyInstance().createSession(properties);
            session.connect();
            logger.info("✓ Connected to Solace broker");
            
            // Create producer for sending replies
            producer = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
                @Override
                public void responseReceived(String messageID) {
                    logger.debug("Reply sent: {}", messageID);
                }
                
                @Override
                public void handleError(String messageID, JCSMPException e, long timestamp) {
                    logger.error("Error sending reply: ", e);
                }
            });
            
            // Subscribe to validation requests
            final Topic validationTopic = JCSMPFactory.onlyInstance()
                    .createTopic("order/validate");
            
            final XMLMessageProducer finalProducer = producer;
            
            consumer = session.getMessageConsumer(new XMLMessageListener() {
                @Override
                public void onReceive(BytesXMLMessage request) {
                    handleValidationRequest(request, finalProducer);
                }
                
                @Override
                public void onException(JCSMPException e) {
                    logger.error("Consumer exception: ", e);
                }
            });
            
            consumer.addSubscription(validationTopic);
            consumer.start();
            
            logger.info("✓ Listening for validation requests on: order/validate\n");
            
            CountDownLatch latch = new CountDownLatch(1);
            Runtime.getRuntime().addShutdownHook(new Thread(latch::countDown));
            latch.await();
            
        } finally {
            if (consumer != null) consumer.close();
            if (producer != null) producer.close();
            if (session != null) session.closeSession();
        }
    }
    
    /**
     * LESSON: Request-Reply Pattern
     * 
     * 1. Extract ReplyTo destination from request
     * 2. Extract CorrelationId for tracking
     * 3. Process request
     * 4. Send reply to ReplyTo with same CorrelationId
     * 5. Requestor matches response by CorrelationId
     */
    private static void handleValidationRequest(BytesXMLMessage request, 
                                                 XMLMessageProducer producer) {
        try {
            // STEP 1: Extract request details
            Destination replyTo = request.getReplyTo();
            String correlationId = request.getCorrelationId();
            
            if (replyTo == null) {
                logger.warn("⚠️ Request without ReplyTo - cannot send response");
                return;
            }
            
            // STEP 2: Parse order
            String payload = new String(request.getBytes());
            Order order = objectMapper.readValue(payload, Order.class);
            
            logger.info("📝 Validation request received:");
            logger.info("   Order ID: {}", order.getOrderId());
            logger.info("   Product: {} x{}", order.getProductId(), order.getQuantity());
            logger.info("   Correlation ID: {}", correlationId);
            logger.info("   Reply To: {}", replyTo);
            
            // STEP 3: Validate inventory
            boolean isValid = validateInventory(order);
            
            // STEP 4: Create reply message
            TextMessage reply = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
            
            // CRITICAL: Set correlation ID so requestor can match response
            reply.setCorrelationId(correlationId);
            
            // Set validation result
            if (isValid) {
                reply.setText("APPROVED");
                reply.getProperties().putString("status", "APPROVED");
                reply.getProperties().putString("message", "Inventory available");
                logger.info("   ✅ Validation: APPROVED");
            } else {
                reply.setText("REJECTED");
                reply.getProperties().putString("status", "REJECTED");
                reply.getProperties().putString("message", "Insufficient inventory");
                logger.warn("   ❌ Validation: REJECTED");
            }
            
            reply.getProperties().putString("orderId", order.getOrderId());
            reply.getProperties().putInteger("availableStock", 
                    INVENTORY.getOrDefault(order.getProductId(), 0));
            
            // STEP 5: Send reply to the specified destination
            producer.send(reply, replyTo);
            logger.info("   📤 Reply sent to: {}\n", replyTo);
            
        } catch (Exception e) {
            logger.error("Error processing validation request: ", e);
        }
    }
    
    /**
     * LESSON: Business logic
     * In real systems, this might:
     * - Query database
     * - Call external API
     * - Apply complex rules
     * - Reserve inventory
     */
    private static boolean validateInventory(Order order) {
        // Simulate processing time
        try {
            Thread.sleep(50 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        Integer available = INVENTORY.get(order.getProductId());
        if (available == null) {
            logger.warn("   ⚠️ Product not found: {}", order.getProductId());
            return false;
        }
        
        boolean sufficient = available >= order.getQuantity();
        logger.info("   Stock check: {} available, {} requested", 
                available, order.getQuantity());
        
        return sufficient;
    }
}
```

#### Step 2: Modify Publisher to Use Request-Reply

Create `/order-publisher/src/main/java/com/solace/practice/publisher/RequestReplyPublisher.java`:

```java
package com.solace.practice.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.solace.practice.model.Order;
import com.solace.practice.model.OrderStatus;
import com.solacesystems.jcsmp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Request-Reply Order Publisher
 * 
 * ADVANCED PATTERN: Synchronous Request-Reply over Async Transport
 * 
 * KEY CONCEPTS:
 * - Requestor sets ReplyTo topic
 * - Uses correlation ID to match responses
 * - Implements timeout for reliability
 * - Handles response asynchronously
 */
public class RequestReplyPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(RequestReplyPublisher.class);
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    
    private static final int TIMEOUT_SECONDS = 5;
    
    // Track pending requests by correlation ID
    private static final ConcurrentHashMap<String, CompletableFuture<ValidationResponse>> 
            pendingRequests = new ConcurrentHashMap<>();
    
    public static void main(String[] args) throws Exception {
        logger.info("=== Request-Reply Order Publisher ===\n");
        
        final JCSMPProperties properties = new JCSMPProperties();
        properties.setProperty(JCSMPProperties.HOST, "localhost:55555");
        properties.setProperty(JCSMPProperties.VPN_NAME, "default");
        properties.setProperty(JCSMPProperties.USERNAME, "admin");
        properties.setProperty(JCSMPProperties.PASSWORD, "admin");
        
        JCSMPSession session = null;
        XMLMessageProducer producer = null;
        XMLMessageConsumer replyConsumer = null;
        
        try {
            session = JCSMPFactory.onlyInstance().createSession(properties);
            session.connect();
            logger.info("✓ Connected to Solace broker\n");
            
            // Create producer
            producer = session.getMessageProducer(new JCSMPStreamingPublishEventHandler() {
                @Override
                public void responseReceived(String messageID) {}
                
                @Override
                public void handleError(String messageID, JCSMPException e, long timestamp) {
                    logger.error("Error publishing: ", e);
                }
            });
            
            // IMPORTANT: Subscribe to reply topic
            final Topic replyTopic = JCSMPFactory.onlyInstance()
                    .createTopic("order/reply/>");
            
            replyConsumer = session.getMessageConsumer(new XMLMessageListener() {
                @Override
                public void onReceive(BytesXMLMessage message) {
                    handleReply(message);
                }
                
                @Override
                public void onException(JCSMPException e) {
                    logger.error("Reply consumer exception: ", e);
                }
            });
            
            replyConsumer.addSubscription(replyTopic);
            replyConsumer.start();
            
            logger.info("✓ Listening for replies on: order/reply/>\n");
            
            // Publish test orders with validation
            publishOrdersWithValidation(producer, session);
            
            // Wait for all responses
            Thread.sleep(10000);
            
        } finally {
            if (replyConsumer != null) replyConsumer.close();
            if (producer != null) producer.close();
            if (session != null) session.closeSession();
        }
    }
    
    /**
     * LESSON: Publishing with Request-Reply
     * 
     * 1. Generate unique correlation ID
     * 2. Set ReplyTo destination
     * 3. Send message
     * 4. Wait for reply (with timeout)
     * 5. Match reply by correlation ID
     */
    private static void publishOrdersWithValidation(XMLMessageProducer producer, 
                                                      JCSMPSession session) {
        String[] products = {"Laptop", "Monitor", "UnknownProduct", "Mouse", "Webcam"};
        int[] quantities = {2, 1, 100, 3, 50};  // Some will fail validation
        
        for (int i = 0; i < products.length; i++) {
            try {
                Order order = createOrder(products[i], quantities[i]);
                
                logger.info("📤 Publishing order: {}", order.getOrderId());
                logger.info("   Product: {} x{}", order.getProductId(), order.getQuantity());
                
                // Send request and wait for reply
                ValidationResponse response = sendValidationRequest(
                        order, producer, session, TIMEOUT_SECONDS);
                
                if (response.isApproved()) {
                    logger.info("   ✅ Order approved - proceeding with processing");
                    // In production: send to order/v1/{region}/{status}/{priority}
                } else {
                    logger.warn("   ❌ Order rejected: {}", response.getMessage());
                    logger.warn("   Available stock: {}", response.getAvailableStock());
                    // In production: notify customer, suggest alternatives
                }
                
                logger.info("");
                Thread.sleep(1000);
                
            } catch (Exception e) {
                logger.error("Error processing order: ", e);
            }
        }
    }
    
    /**
     * LESSON: Core Request-Reply Logic
     */
    private static ValidationResponse sendValidationRequest(Order order, 
                                                            XMLMessageProducer producer,
                                                            JCSMPSession session,
                                                            int timeoutSeconds) 
            throws JCSMPException {
        
        // STEP 1: Generate unique correlation ID
        String correlationId = UUID.randomUUID().toString();
        
        // STEP 2: Create reply-to topic (unique per order)
        Topic replyTo = JCSMPFactory.onlyInstance()
                .createTopic("order/reply/" + correlationId);
        
        // STEP 3: Create CompletableFuture to wait for response
        CompletableFuture<ValidationResponse> futureResponse = new CompletableFuture<>();
        pendingRequests.put(correlationId, futureResponse);
        
        try {
            // STEP 4: Create and send request
            BytesMessage request = JCSMPFactory.onlyInstance().createMessage(BytesMessage.class);
            String json = objectMapper.writeValueAsString(order);
            request.setData(json.getBytes());
            
            // CRITICAL: Set ReplyTo and CorrelationId
            request.setReplyTo(replyTo);
            request.setCorrelationId(correlationId);
            
            // Set delivery mode
            request.setDeliveryMode(DeliveryMode.PERSISTENT);
            
            // Send to validation topic
            Topic validationTopic = JCSMPFactory.onlyInstance()
                    .createTopic("order/validate");
            producer.send(request, validationTopic);
            
            logger.info("   📨 Validation request sent (correlation: {})", 
                    correlationId.substring(0, 8));
            logger.info("   ⏳ Waiting for reply (timeout: {}s)...", timeoutSeconds);
            
            // STEP 5: Wait for response with timeout
            ValidationResponse response = futureResponse.get(timeoutSeconds, TimeUnit.SECONDS);
            
            logger.info("   ✓ Reply received");
            return response;
            
        } catch (java.util.concurrent.TimeoutException e) {
            logger.error("   ⏰ TIMEOUT: No response after {}s", timeoutSeconds);
            throw new JCSMPException("Validation timeout");
        } catch (Exception e) {
            logger.error("   ❌ Request failed: {}", e.getMessage());
            throw new JCSMPException("Request failed", e);
        } finally {
            pendingRequests.remove(correlationId);
        }
    }
    
    /**
     * LESSON: Handling Replies
     * 
     * Match reply by correlation ID and complete the future
     */
    private static void handleReply(BytesXMLMessage message) {
        try {
            String correlationId = message.getCorrelationId();
            
            if (correlationId == null) {
                logger.warn("⚠️ Reply without correlation ID - ignoring");
                return;
            }
            
            CompletableFuture<ValidationResponse> future = pendingRequests.get(correlationId);
            
            if (future == null) {
                logger.warn("⚠️ Reply for unknown correlation ID: {}", correlationId);
                return;
            }
            
            // Extract response
            String status = message.getProperties().getString("status");
            String responseMessage = message.getProperties().getString("message");
            Integer availableStock = message.getProperties().getInteger("availableStock");
            
            ValidationResponse response = new ValidationResponse(
                    "APPROVED".equals(status),
                    responseMessage,
                    availableStock != null ? availableStock : 0
            );
            
            // Complete the future
            future.complete(response);
            
        } catch (Exception e) {
            logger.error("Error handling reply: ", e);
        }
    }
    
    private static Order createOrder(String product, int quantity) {
        return new Order(
                "ORD-" + UUID.randomUUID().toString().substring(0, 8),
                "CUST-" + (1000 + (int)(Math.random() * 1000)),
                product,
                quantity,
                999.99,
                OrderStatus.CREATED,
                "US-EAST",
                "NORMAL",
                LocalDateTime.now()
        );
    }
    
    /**
     * Helper class for validation response
     */
    static class ValidationResponse {
        private final boolean approved;
        private final String message;
        private final int availableStock;
        
        public ValidationResponse(boolean approved, String message, int availableStock) {
            this.approved = approved;
            this.message = message;
            this.availableStock = availableStock;
        }
        
        public boolean isApproved() { return approved; }
        public String getMessage() { return message; }
        public int getAvailableStock() { return availableStock; }
    }
}
```

#### Step 3: Run the Scenario

**Terminal 1**: Start inventory validator (responds to requests)
```bash
cd admin-dashboard
mvn compile
mvn exec:java -Dexec.mainClass="com.solace.practice.validation.InventoryValidator"
```

**Terminal 2**: Run request-reply publisher
```bash
cd order-publisher
mvn compile
mvn exec:java -Dexec.mainClass="com.solace.practice.publisher.RequestReplyPublisher"
```

### **Expected Output**

**Publisher:**
```
📤 Publishing order: ORD-abc123
   Product: Laptop x2
   📨 Validation request sent (correlation: 7a9e3f12)
   ⏳ Waiting for reply (timeout: 5s)...
   ✓ Reply received
   ✅ Order approved - proceeding with processing

📤 Publishing order: ORD-def456
   Product: UnknownProduct x100
   📨 Validation request sent (correlation: 3c8d1a45)
   ⏳ Waiting for reply (timeout: 5s)...
   ✓ Reply received
   ❌ Order rejected: Insufficient inventory
   Available stock: 0
```

**Validator:**
```
📝 Validation request received:
   Order ID: ORD-abc123
   Product: Laptop x2
   Correlation ID: 7a9e3f12-4b5c-6d7e-8f9a-0b1c2d3e4f5g
   Reply To: order/reply/7a9e3f12-4b5c-6d7e-8f9a-0b1c2d3e4f5g
   Stock check: 50 available, 2 requested
   ✅ Validation: APPROVED
   📤 Reply sent to: order/reply/7a9e3f12-4b5c-6d7e-8f9a-0b1c2d3e4f5g
```

### **Key Learnings**
- ✅ Request-Reply enables synchronous patterns over async messaging
- ✅ Correlation IDs link requests with responses
- ✅ Timeout handling prevents hanging requests
- ✅ ReplyTo destinations can be unique per request
- ✅ CompletableFutures enable clean async/await patterns

### **Challenge Extensions**
1. Implement retry logic (exponential backoff)
2. Add request caching (don't revalidate same order twice)
3. Implement request batching (validate multiple orders together)
4. Add circuit breaker (stop sending if validator is down)

---

## 🎯 Scenario 3: Multi-Region Load Balancing

### **Business Problem**
Your dashboard runs in 3 regions (US, EU, ASIA). Orders should be load-balanced across all running instances for high availability.

### **What You'll Build**
- Non-exclusive queue configuration
- Multiple consumer instances
- Round-robin load distribution
- Failover demonstration

### **Implementation Steps**

See SCENARIO_3.md for complete implementation...

---

## 🎯 Scenario 4: Dead Letter Queue Monitoring

### **Business Problem**
Some orders fail processing (corrupt data, integration failures). These should go to a DLQ for manual review and replay.

### **What You'll Build**
- DLQ configuration
- Redelivery count handling
- DLQ monitoring dashboard
- Message replay mechanism

### **Implementation Steps**

See SCENARIO_4.md for complete implementation...

---

## 🎯 Scenario 5: Circuit Breaker Pattern

### **Business Problem**
When downstream services are slow/unavailable, protect your system with circuit breaker pattern.

### **What You'll Build**
- Flow control and backpressure
- Circuit breaker states (CLOSED → OPEN → HALF_OPEN)
- Health monitoring
- Graceful degradation

### **Implementation Steps**

See SCENARIO_5.md for complete implementation...

---

## 📊 Progress Tracking

Track your learning journey:

- [ ] Scenario 1: High-Value Alerts - **Started: _____**
- [ ] Scenario 2: Request-Reply - **Started: _____**
- [ ] Scenario 3: Load Balancing - **Started: _____**
- [ ] Scenario 4: DLQ Monitoring - **Started: _____**
- [ ] Scenario 5: Circuit Breaker - **Started: _____**

---

## 🎓 Real-World Applications

These patterns are used in:

- **E-commerce**: Order processing, fraud detection
- **Finance**: Trading systems, payment processing
- **IoT**: Sensor data processing, device management
- **Gaming**: Player events, matchmaking
- **Healthcare**: Patient monitoring, lab results

---

## 💡 Pro Tips

1. **Start Simple**: Complete Scenario 1 before moving to Scenario 2
2. **Read the Code**: Every example has detailed comments explaining why
3. **Experiment**: Change values, break things, see what happens
4. **Test Failures**: Intentionally cause errors to see how systems react
5. **Monitor**: Use Solace Web UI to see queues, flows, and message rates

---

## 🚀 Ready to Start?

Begin with **Scenario 1: High-Value Order Alerting**. It's the perfect introduction to message selectors and will take about 30 minutes.

Good luck! 🎯
