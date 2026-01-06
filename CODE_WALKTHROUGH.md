# 🎓 Code Walkthrough - From Certification to Implementation

This guide maps your certification knowledge to actual working code. For each concept, you'll see:
- ✅ What you learned in certification
- 💻 Where it appears in the code
- 🔍 How to experiment with it

---

## 1. Connection & Session Management

### ✅ What You Learned
"A JCSMPSession represents a connection to the broker. Sessions multiplex multiple logical connections over a single TCP connection."

### 💻 In the Code
**File**: `AdminDashboard.java` lines 51-68

```java
// Create connection properties
final JCSMPProperties properties = new JCSMPProperties();
properties.setProperty(JCSMPProperties.HOST, "localhost:55555");     // Broker URL
properties.setProperty(JCSMPProperties.VPN_NAME, "default");          // Message VPN
properties.setProperty(JCSMPProperties.USERNAME, "admin");            // Credentials
properties.setProperty(JCSMPProperties.PASSWORD, "admin");

// Enable automatic reconnection if connection drops
properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);

// Create and connect session
JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
session.connect();
```

### 🔍 Try This
1. **Test reconnection**: Stop and restart Docker container while dashboard is running
2. **Wrong credentials**: Change password to "wrong" - see what happens
3. **Connection timeout**: Use wrong host "localhost:99999"

**Expected Learning**: Understanding connection lifecycle and error handling

---

## 2. Queue Provisioning

### ✅ What You Learned
"Queues provide guaranteed messaging. They persist messages until consumed and acknowledged. Queues can be exclusive or non-exclusive."

### 💻 In the Code
**File**: `AdminDashboard.java` lines 108-137

```java
private static Queue provisionQueue(JCSMPSession session, String queueName) {
    logger.info("Provisioning queue: {}", queueName);
    
    // STEP 1: Define queue properties
    final EndpointProperties endpointProps = new EndpointProperties();
    
    // Access type: EXCLUSIVE = only one consumer
    endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_NONEXCLUSIVE);
    
    // Permissions
    endpointProps.setPermission(EndpointProperties.PERMISSION_CONSUME);
    
    // Storage: How many MB can queue hold?
    endpointProps.setQuotaMB(100);
    
    // What happens when quota is exceeded?
    endpointProps.setDiscardBehavior(EndpointProperties.DISCARD_NOTIFY_SENDER_ON);
    
    // Message TTL (Time To Live) - 0 means no expiry
    endpointProps.setMaxMsgRedelivery(0);
    
    // STEP 2: Create queue object
    final Queue queue = JCSMPFactory.onlyInstance().createQueue(queueName);
    
    // STEP 3: Provision on broker
    // FLAG_IGNORE_ALREADY_EXISTS: Don't fail if queue already exists
    session.provision(queue, endpointProps, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
    
    logger.info("✓ Queue provisioned: {}", queueName);
    return queue;
}
```

### 🔍 Try This
1. **Change to exclusive**: 
   ```java
   endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
   ```
   Now try running TWO dashboard instances - second will fail!

2. **Reduce quota**: 
   ```java
   endpointProps.setQuotaMB(1);  // Only 1 MB
   ```
   Publish many orders quickly - watch what happens

3. **Check in Web UI**:
   - Go to http://localhost:8080
   - Navigate to Queues → admin-dashboard-orders
   - See quota, message count, access type

**Expected Learning**: Queue configuration directly impacts behavior

---

## 3. Topic Subscriptions & Wildcards

### ✅ What You Learned
"Topics use `/` as hierarchy separator. Wildcards: `*` matches one level, `>` matches multiple levels."

### 💻 In the Code
**File**: `AdminDashboard.java` lines 143-160

```java
private static void addQueueSubscriptions(JCSMPSession session, Queue queue) {
    logger.info("Adding topic subscriptions to queue...");
    
    // Subscribe to ALL orders using > wildcard
    // This matches: order/v1/{anything}/{anything}/{anything}
    Topic allOrders = JCSMPFactory.onlyInstance().createTopic("order/v1/>");
    
    // Add subscription to queue
    // Messages matching this topic will be routed to this queue
    session.addSubscription(queue, allOrders, JCSMPSession.WAIT_FOR_CONFIRM);
    
    logger.info("✓ Subscribed to: order/v1/> (all orders)");
}
```

### 🔍 Try This

**Experiment 1: Region-specific subscription**
```java
// Only US-EAST orders
Topic usEast = JCSMPFactory.onlyInstance().createTopic("order/v1/US-EAST/>");
session.addSubscription(queue, usEast, JCSMPSession.WAIT_FOR_CONFIRM);
```

**Experiment 2: Status-specific across all regions**
```java
// Only PAID orders from any region
Topic allPaid = JCSMPFactory.onlyInstance().createTopic("order/v1/*/PAID/*");
session.addSubscription(queue, allPaid, JCSMPSession.WAIT_FOR_CONFIRM);
```

**Experiment 3: Urgent orders only**
```java
// URGENT priority from any region/status
Topic urgent = JCSMPFactory.onlyInstance().createTopic("order/v1/*/*/URGENT");
session.addSubscription(queue, urgent, JCSMPSession.WAIT_FOR_CONFIRM);
```

**Experiment 4: Multiple subscriptions**
```java
// Subscribe to multiple patterns
Topic usOrders = JCSMPFactory.onlyInstance().createTopic("order/v1/US-*/*");
Topic euOrders = JCSMPFactory.onlyInstance().createTopic("order/v1/EU/*");
session.addSubscription(queue, usOrders, JCSMPSession.WAIT_FOR_CONFIRM);
session.addSubscription(queue, euOrders, JCSMPSession.WAIT_FOR_CONFIRM);
```

### 📊 Verify in Web UI
- Go to Queues → admin-dashboard-orders → Subscriptions tab
- You'll see your topic subscriptions listed

**Expected Learning**: Wildcards enable flexible message routing without code changes

---

## 4. Flow Receivers (Consumers)

### ✅ What You Learned
"Flows are used to consume messages from queues. They support acknowledgment modes (AUTO, CLIENT) and flow control."

### 💻 In the Code
**File**: `AdminDashboard.java` lines 167-240

```java
private static FlowReceiver createFlowReceiver(JCSMPSession session, Queue queue) {
    logger.info("Creating flow receiver for queue: {}", queue.getName());
    
    // STEP 1: Configure flow properties
    final ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
    
    // Which queue to consume from
    flowProps.setEndpoint(queue);
    
    // CRITICAL: Acknowledgment mode
    // CLIENT = Manual acknowledgment (more control, recommended)
    // AUTO = Automatic acknowledgment (easier, less reliable)
    flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);
    
    // Flow control: Window size (how many unacknowledged messages allowed)
    // Default is 255
    flowProps.setWindowSize(50);
    
    // STEP 2: Create message listener
    XMLMessageListener messageListener = new XMLMessageListener() {
        @Override
        public void onReceive(BytesXMLMessage message) {
            handleOrderMessage(message);
        }
        
        @Override
        public void onException(JCSMPException e) {
            logger.error("Consumer exception: ", e);
        }
    };
    
    // STEP 3: Create flow event handler
    // Monitors flow lifecycle: ACTIVE, INACTIVE, DOWN
    FlowEventHandler flowEventHandler = new FlowEventHandler() {
        @Override
        public void handleEvent(Object source, FlowEventArgs event) {
            logger.info("Flow event: {} - {}", event.getEvent(), event.getInfo());
        }
    };
    
    // STEP 4: Create flow receiver
    FlowReceiver flowReceiver = session.createFlow(
        messageListener,      // Handles messages
        flowProps,           // Configuration
        null,                // Endpoint properties (null = use queue config)
        flowEventHandler     // Handles flow events
    );
    
    logger.info("✓ Flow receiver created");
    return flowReceiver;
}
```

### 🔍 Try This

**Experiment 1: Change acknowledgment mode**
```java
// Try AUTO mode
flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);
// Now you don't need to call message.ackMessage()
// BUT: Less control over when messages are removed from queue
```

**Experiment 2: Adjust window size**
```java
// Smaller window = more flow control messages, lower throughput
flowProps.setWindowSize(10);

// Larger window = fewer flow control messages, higher throughput  
flowProps.setWindowSize(255);
```

**Experiment 3: Add multiple flows**
```java
// Create 3 flow receivers on same queue
FlowReceiver flow1 = session.createFlow(listener1, flowProps, null, handler);
FlowReceiver flow2 = session.createFlow(listener2, flowProps, null, handler);
FlowReceiver flow3 = session.createFlow(listener3, flowProps, null, handler);
// Messages will be load-balanced across all 3!
```

**Expected Learning**: Flow configuration affects performance and reliability

---

## 5. Message Acknowledgment

### ✅ What You Learned
"CLIENT_ACK mode requires explicit acknowledgment. Messages stay in queue until acknowledged. NACK returns message to queue."

### 💻 In the Code
**File**: `AdminDashboard.java` lines 242-310

```java
private static void handleOrderMessage(BytesXMLMessage message) {
    try {
        // STEP 1: Extract message details
        String payload = new String(message.getBytes());
        String topic = message.getDestination().getName();
        
        // Get message properties
        SDTMap properties = message.getProperties();
        String orderId = properties.getString("orderId");
        Double totalAmount = properties.getDouble("totalAmount");
        
        // STEP 2: Deserialize payload
        Order order = objectMapper.readValue(payload, Order.class);
        
        // STEP 3: Process order
        logger.info("📥 Received: {} from topic: {}", order, topic);
        metrics.recordOrder(order, totalAmount);
        
        // STEP 4: ACKNOWLEDGE MESSAGE
        // CRITICAL: This removes message from queue
        // If you don't ACK:
        // - Message stays in queue
        // - Eventually redelivered (if max redelivery set)
        // - Queue fills up
        message.ackMessage();
        
    } catch (Exception e) {
        logger.error("Error processing message: ", e);
        
        // PRODUCTION PATTERN: Decide what to do
        // Option 1: ACK anyway (discard bad message)
        // Option 2: Don't ACK (retry later)
        // Option 3: NACK (explicit reject, requeue immediately)
        
        // For now: ACK to prevent queue backup
        message.ackMessage();
    }
}
```

### 🔍 Try This

**Experiment 1: Don't acknowledge**
```java
// Comment out the ACK
// message.ackMessage();

// What happens?
// 1. Messages stay in queue
// 2. Queue depth increases
// 3. Eventually hits window size limit
// 4. No more messages delivered until you ACK
```

**Experiment 2: Selective acknowledgment**
```java
if (order.getTotalAmount() > 1000) {
    // ACK high-value orders
    message.ackMessage();
} else {
    // Don't ACK low-value (they'll be redelivered)
    logger.info("Skipping low-value order");
}
```

**Experiment 3: Negative acknowledgment**
```java
// Reject and requeue
flowReceiver.nack(message.getMessageId());
// Message goes back to head of queue for immediate retry
```

**Experiment 4: Settlement**
```java
// Settle: ACK without processing (for poison messages)
message.settle();
// Use when message is corrupt and should be discarded
```

### 📊 Monitor in Web UI
- Queues → admin-dashboard-orders
- Watch "Msgs Spooled" (messages waiting)
- Watch "Msgs Acknowledged" counter
- Watch "Msgs Redelivered" if you don't ACK

**Expected Learning**: Acknowledgment is critical for guaranteed messaging

---

## 6. Message Selectors

### ✅ What You Learned
"Message selectors filter messages at the broker using SQL-like expressions. Only matching messages are delivered to consumer."

### 💻 In the Code
**File**: `AdminDashboard.java` lines 167-240 (add to flow properties)

```java
// Add this to createFlowReceiver() method
final ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
flowProps.setEndpoint(queue);
flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

// ADD MESSAGE SELECTOR HERE
flowProps.setSelector("totalAmount > 1000");
```

### 🔍 Try This

**Experiment 1: Amount-based filtering**
```java
// High-value orders only
flowProps.setSelector("totalAmount > 5000");

// Orders between $100 and $1000
flowProps.setSelector("totalAmount BETWEEN 100 AND 1000");
```

**Experiment 2: String matching**
```java
// US regions only
flowProps.setSelector("region LIKE 'US%'");

// Specific region
flowProps.setSelector("region = 'EU'");
```

**Experiment 3: Priority filtering**
```java
// Urgent orders only
flowProps.setSelector("priority = 'URGENT'");

// High or urgent priority
flowProps.setSelector("priority IN ('HIGH', 'URGENT')");
```

**Experiment 4: Compound conditions**
```java
// High-value US urgent orders
flowProps.setSelector("totalAmount > 1000 AND region = 'US-EAST' AND priority = 'URGENT'");

// Large quantity orders
flowProps.setSelector("quantity > 10 OR totalAmount > 5000");
```

### ⚠️ Important Notes
- **Property names** must match what publisher sets
- **Filtering happens at broker** (more efficient than client-side)
- **Syntax is SQL-like** but limited
- **Only works with message properties**, not payload

### 📊 Performance Impact
- Broker filters BEFORE sending
- Reduces network traffic
- Client receives only relevant messages
- But: Broker does work to evaluate selector

**Expected Learning**: Selectors enable efficient message filtering

---

## 7. Publishing Messages

### ✅ What You Learned
"Publishers can send persistent (guaranteed) or direct (best-effort) messages. Persistent messages require acknowledgment from broker."

### 💻 In the Code
**File**: `OrderPublisher.java` lines 75-150

```java
private static XMLMessageProducer createProducer(JCSMPSession session) {
    logger.info("Creating message producer...");
    
    // STEP 1: Create producer event handler
    // Handles async acknowledgments from broker
    JCSMPStreamingPublishEventHandler eventHandler = new JCSMPStreamingPublishEventHandler() {
        @Override
        public void responseReceived(String messageID) {
            // Message successfully spooled by broker
            logger.debug("✓ Broker confirmed: {}", messageID);
        }
        
        @Override
        public void handleError(String messageID, JCSMPException cause, long timestamp) {
            // Message rejected by broker
            logger.error("✗ Broker rejected {}: {}", messageID, cause.getMessage());
        }
    };
    
    // STEP 2: Create producer
    XMLMessageProducer producer = session.getMessageProducer(eventHandler);
    
    logger.info("✓ Message producer created");
    return producer;
}

private static void publishOrder(Order order, XMLMessageProducer producer) {
    try {
        // STEP 1: Create message
        BytesMessage message = JCSMPFactory.onlyInstance().createMessage(BytesMessage.class);
        
        // STEP 2: Set payload (JSON)
        String json = objectMapper.writeValueAsString(order);
        message.setData(json.getBytes());
        
        // STEP 3: Set delivery mode
        // PERSISTENT = Guaranteed delivery (message survives broker restart)
        // DIRECT = Best effort (faster but can be lost)
        message.setDeliveryMode(DeliveryMode.PERSISTENT);
        
        // STEP 4: Set message properties
        // These can be used in message selectors!
        SDTMap properties = message.getProperties();
        properties.putString("orderId", order.getOrderId());
        properties.putString("customerId", order.getCustomerId());
        properties.putDouble("totalAmount", order.getTotalAmount());
        properties.putString("region", order.getRegion());
        properties.putString("priority", order.getPriority());
        
        // STEP 5: Create topic
        String topicString = String.format("order/v1/%s/%s/%s",
                order.getRegion(),
                order.getStatus(),
                order.getPriority());
        Topic topic = JCSMPFactory.onlyInstance().createTopic(topicString);
        
        // STEP 6: Publish!
        producer.send(message, topic);
        
        logger.info("📤 Published: {} → {}", order, topicString);
        
    } catch (Exception e) {
        logger.error("Error publishing order: ", e);
    }
}
```

### 🔍 Try This

**Experiment 1: Change delivery mode**
```java
// Try direct messaging (best effort)
message.setDeliveryMode(DeliveryMode.DIRECT);
// Now: Faster, but messages can be lost if broker crashes
```

**Experiment 2: Add custom properties**
```java
// Add your own properties
properties.putString("source", "web-app");
properties.putLong("timestamp", System.currentTimeMillis());
properties.putBoolean("vipCustomer", true);

// Use in selector:
flowProps.setSelector("vipCustomer = true");
```

**Experiment 3: Message expiration**
```java
// Message expires after 60 seconds
message.setTimeToLive(60000);
```

**Experiment 4: Message priority**
```java
// Not available in standard Solace
// But you can simulate with topic hierarchy
// order/v1/{region}/{status}/CRITICAL
// order/v1/{region}/{status}/NORMAL
```

**Expected Learning**: Message configuration affects delivery guarantees

---

## 8. Error Handling Patterns

### ✅ What You Learned
"Dead Letter Queues (DLQ) store messages that fail processing. Max redelivery count prevents infinite retries."

### 💻 In the Code

**Pattern 1: Transient vs Permanent Errors**
```java
try {
    processOrder(order);
    message.ackMessage();
} catch (TemporaryException e) {
    // Don't ACK - let it retry
    logger.warn("Temporary failure, will retry: {}", e.getMessage());
} catch (PermanentException e) {
    // ACK to prevent retry loop
    logger.error("Permanent failure, discarding: {}", e.getMessage());
    message.ackMessage();
}
```

**Pattern 2: Max Redelivery with DLQ**
```java
// In queue provisioning
endpointProps.setMaxMsgRedelivery(3);  // Try 3 times
// After 3 failures, message goes to DLQ automatically

// Monitor DLQ
Queue dlq = JCSMPFactory.onlyInstance().createQueue("#DEAD_MSG_QUEUE");
// Messages here need manual review/replay
```

**Pattern 3: Circuit Breaker**
```java
private static class CircuitBreaker {
    private int failureCount = 0;
    private static final int THRESHOLD = 5;
    private boolean isOpen = false;
    
    public void recordSuccess() {
        failureCount = 0;
        isOpen = false;
    }
    
    public void recordFailure() {
        failureCount++;
        if (failureCount >= THRESHOLD) {
            isOpen = true;
            logger.error("Circuit breaker OPEN - too many failures");
        }
    }
    
    public boolean shouldAllowRequest() {
        return !isOpen;
    }
}
```

### 🔍 Try This

**Experiment 1: Simulate failures**
```java
// In handleOrderMessage(), add:
if (order.getProductId().equals("FailMe")) {
    throw new RuntimeException("Simulated failure");
}
// Don't ACK in catch block
// Watch message get redelivered
```

**Experiment 2: Monitor redelivery**
```java
// Check redelivery count
if (message.getRedelivered()) {
    int count = message.getRedeliveryCount();
    logger.warn("Message redelivered {} times", count);
}
```

**Expected Learning**: Error handling prevents message loss and loops

---

## 🎯 Complete Learning Exercise

### Task: Build High-Value Order Filter

**Goal**: Modify the dashboard to only process orders > $1000 and from US regions.

**Steps**:

1. **Add message selector** (AdminDashboard.java line 203)
   ```java
   flowProps.setSelector("totalAmount > 1000 AND region LIKE 'US%'");
   ```

2. **Update topic subscription** (AdminDashboard.java line 149)
   ```java
   Topic usOrders = JCSMPFactory.onlyInstance().createTopic("order/v1/US-*/*");
   session.addSubscription(queue, usOrders, JCSMPSession.WAIT_FOR_CONFIRM);
   ```

3. **Run and test**
   ```bash
   # Terminal 1: Dashboard
   cd admin-dashboard
   mvn exec:java -Dexec.mainClass="com.solace.practice.dashboard.AdminDashboard"
   
   # Terminal 2: Publisher
   cd order-publisher
   mvn exec:java -Dexec.mainClass="com.solace.practice.publisher.OrderPublisher"
   ```

4. **Expected result**: Only high-value US orders appear in dashboard

5. **Verify in Web UI**:
   - Go to Queues → admin-dashboard-orders
   - Check subscriptions tab
   - Check selector on flow

---

## 📚 Next Steps

1. **Read SOLACE_PATTERNS.md** - Quick reference for common patterns
2. **Try ADVANCED_SCENARIOS.md** - Real-world implementations
3. **Check FAQ.md** - When you get stuck

**Remember**: Theory + Practice = Mastery

You know the concepts from certification. This code shows you how to implement them. Now experiment, break things, and learn by doing!

🚀 **Happy Coding!**
