# 🚀 Solace Patterns Quick Reference

A cheat sheet for common Solace patterns demonstrated in this project.

---

## 📡 Connection & Session Management

### Basic Connection
```java
JCSMPProperties properties = new JCSMPProperties();
properties.setProperty(JCSMPProperties.HOST, "localhost:55555");
properties.setProperty(JCSMPProperties.VPN_NAME, "default");
properties.setProperty(JCSMPProperties.USERNAME, "admin");
properties.setProperty(JCSMPProperties.PASSWORD, "admin");

JCSMPSession session = JCSMPFactory.onlyInstance().createSession(properties);
session.connect();
```

### With Reconnection
```java
properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);
```

---

## 📤 Publishing Messages

### Persistent (Guaranteed) Publishing
```java
XMLMessageProducer producer = session.getMessageProducer(
    new PublisherEventHandler()
);

TextMessage message = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
message.setText(jsonPayload);
message.setDeliveryMode(DeliveryMode.PERSISTENT);

Topic destination = JCSMPFactory.onlyInstance().createTopic("order/v1/US-EAST/CREATED");
producer.send(message, destination);
```

### Direct (Best-Effort) Publishing
```java
message.setDeliveryMode(DeliveryMode.DIRECT);
producer.send(message, destination);
```

### With Publisher Confirmations
```java
class PublisherEventHandler implements JCSMPStreamingPublishEventHandler {
    @Override
    public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
        // Message publish failed
        logger.error("Publish failed: {}", cause.getMessage());
    }
    
    // If no error callback, message was successfully spooled
}
```

### Adding Message Properties
```java
SDTMap userProperties = JCSMPFactory.onlyInstance().createMap();
userProperties.putString("customerId", "CUST-123");
userProperties.putDouble("totalAmount", 999.99);
userProperties.putBoolean("isUrgent", true);
message.setProperties(userProperties);
```

---

## 📥 Consuming Messages

### Queue-Based Consumer (Guaranteed)
```java
// 1. Provision queue
Queue queue = JCSMPFactory.onlyInstance().createQueue("my-queue");
EndpointProperties endpointProps = new EndpointProperties();
endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_NONEXCLUSIVE);
session.provision(queue, endpointProps, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);

// 2. Add topic subscriptions
Topic topic = JCSMPFactory.onlyInstance().createTopic("order/v1/>");
session.addSubscription(queue, topic, JCSMPSession.WAIT_FOR_CONFIRM);

// 3. Create flow receiver
ConsumerFlowProperties flowProps = new ConsumerFlowProperties();
flowProps.setEndpoint(queue);
flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

FlowReceiver flow = session.createFlow(
    new MyMessageListener(),
    flowProps,
    null,
    new MyFlowEventHandler()
);

// 4. Start receiving
flow.start();
```

### Message Listener Implementation
```java
class MyMessageListener implements XMLMessageListener {
    @Override
    public void onReceive(BytesXMLMessage message) {
        try {
            // Process message
            String text = ((TextMessage) message).getText();
            
            // Acknowledge (removes from queue)
            message.ackMessage();
            
        } catch (Exception e) {
            // On error, don't ACK (message will be redelivered)
            logger.error("Processing failed", e);
        }
    }
    
    @Override
    public void onException(JCSMPException exception) {
        logger.error("Consumer exception: ", exception);
    }
}
```

### Direct Topic Subscriber (Best-Effort)
```java
XMLMessageConsumer consumer = session.getMessageConsumer(new MyMessageListener());

Topic topic = JCSMPFactory.onlyInstance().createTopic("order/v1/US-EAST/>");
session.addSubscription(topic);

consumer.start();
```

---

## 🔍 Topic Hierarchies & Wildcards

### Topic Structure Best Practices
```
{domain}/{version}/{region}/{entity}/{action}/{priority}

Examples:
• order/v1/US-EAST/payment/processed/HIGH
• inventory/v2/EU/stock/updated/NORMAL
• notification/v1/ASIA/email/sent/URGENT
```

### Wildcard Subscriptions
```java
// Single-level wildcard (*)
"order/v1/*/CREATED/*"          // Any region, any priority
"order/v1/US-EAST/*/NORMAL"     // US-EAST, any status

// Multi-level wildcard (>)
"order/v1/>"                     // Everything under order/v1/
"order/v1/US-EAST/>"            // All US-EAST events
"order/>"                        // All order events (any version)

// Combining
"order/v1/US-*/PAID/>"          // US-EAST and US-WEST paid orders
```

---

## 🎯 Message Selectors (SQL-like Filtering)

### On Queue Subscriptions
```java
flowProps.setSelector("totalAmount > 1000");
flowProps.setSelector("priority = 'URGENT'");
flowProps.setSelector("totalAmount > 500 AND region = 'US-EAST'");
flowProps.setSelector("status IN ('PAID', 'SHIPPED', 'DELIVERED')");
```

### Selector Syntax
```sql
-- Comparison operators
totalAmount > 1000
quantity <= 5
status = 'PAID'
status != 'CANCELLED'

-- Logical operators
totalAmount > 500 AND priority = 'HIGH'
region = 'US-EAST' OR region = 'US-WEST'

-- IN clause
status IN ('CREATED', 'VALIDATED', 'PAID')

-- LIKE pattern matching
customerId LIKE 'VIP-%'

-- IS NULL / IS NOT NULL
discount IS NOT NULL
```

---

## 🔄 Message Acknowledgment Patterns

### Client Acknowledgment (Manual)
```java
flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);

// In message listener:
message.ackMessage();           // Success - remove from queue
```

### Auto Acknowledgment
```java
flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);
// Automatically ACKed when onReceive() returns normally
```

### Reject (Requeue for Retry)
```java
// Message goes back to queue for redelivery
// Not available in JCSMP - use don't-ACK pattern instead
```

---

## ⚙️ Queue Configuration

### Exclusive Queue (Single Consumer)
```java
EndpointProperties props = new EndpointProperties();
props.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
props.setPermission(EndpointProperties.PERMISSION_CONSUME);
props.setQuota(100); // 100 MB
```

### Non-Exclusive Queue (Load Balancing)
```java
props.setAccessType(EndpointProperties.ACCESSTYPE_NONEXCLUSIVE);
// Multiple consumers can connect and share load
```

### Durable vs Non-Durable
```java
// Durable (survives broker restart)
Queue queue = JCSMPFactory.onlyInstance().createQueue("my-queue");

// Non-durable (deleted when all consumers disconnect)
// Set during provisioning with appropriate flags
```

### Queue Limits
```java
props.setQuota(500);                           // Max size: 500 MB
props.setMaxMsgRedelivery(3);                  // Retry 3 times, then DLQ
props.setMaxMsgSize(10 * 1024 * 1024);         // 10 MB per message
props.setRespectsTTL(true);                    // Honor message TTL
```

---

## 🚦 Flow Control

### Window Size (In-Flight Messages)
```java
flowProps.setActiveFlowIndication(true);
// Controls how many unacknowledged messages can be in flight
```

### Stopping/Starting Flow
```java
flow.stop();    // Pause receiving messages
flow.start();   // Resume receiving
```

---

## ⚠️ Error Handling Patterns

### Publisher Error Handling
```java
@Override
public void handleErrorEx(Object key, JCSMPException cause, long timestamp) {
    if (cause instanceof JCSMPTransportException) {
        // Connection lost - will auto-reconnect
        logger.warn("Connection lost, reconnecting...");
    } else if (cause instanceof ClosedFacilityException) {
        // Producer was closed
        logger.error("Producer closed");
    } else {
        // Other errors (quota exceeded, etc.)
        logger.error("Publish error: {}", cause.getMessage());
    }
}
```

### Consumer Error Handling
```java
@Override
public void onReceive(BytesXMLMessage message) {
    try {
        processMessage(message);
        message.ackMessage(); // Success
        
    } catch (TransientException e) {
        // Don't ACK - will be redelivered
        logger.warn("Transient error, will retry: {}", e.getMessage());
        
    } catch (PermanentException e) {
        // ACK to discard (or send to DLQ)
        message.ackMessage();
        logger.error("Permanent error, discarding: {}", e.getMessage());
    }
}
```

### Flow Events
```java
class MyFlowEventHandler implements FlowEventHandler {
    @Override
    public void handleEvent(Object source, FlowEventArgs event) {
        if (event.getEvent() == FlowEvent.FLOW_ACTIVE) {
            logger.info("Flow is active");
        } else if (event.getEvent() == FlowEvent.FLOW_INACTIVE) {
            logger.warn("Flow is inactive");
        } else if (event.getEvent() == FlowEvent.FLOW_DOWN) {
            logger.error("Flow is down");
        }
    }
}
```

---

## 🔐 Request-Reply Pattern

### Publisher (Requester)
```java
// Set reply-to topic
Topic replyTopic = JCSMPFactory.onlyInstance().createTopic("reply/" + requestId);
message.setReplyTo(replyTopic);
message.setCorrelationId(requestId);

// Send request
producer.send(message, requestTopic);

// Listen for reply on reply topic
```

### Consumer (Replier)
```java
@Override
public void onReceive(BytesXMLMessage request) {
    // Process request
    Object response = processRequest(request);
    
    // Create reply
    TextMessage reply = JCSMPFactory.onlyInstance().createMessage(TextMessage.class);
    reply.setCorrelationId(request.getCorrelationId());
    reply.setText(toJson(response));
    
    // Send reply
    producer.sendReply(request, reply);
}
```

---

## 📊 Message Properties & Metadata

### Common Message Properties
```java
// Message ID (auto-generated)
String msgId = message.getApplicationMessageId();

// Correlation ID (for request-reply)
message.setCorrelationId("request-123");
String corrId = message.getCorrelationId();

// Timestamp
long timestamp = message.getSenderTimestamp();

// Priority (0-255, higher = more important)
message.setPriority(200);

// Time to Live (milliseconds)
message.setTimeToLive(60000); // 1 minute

// Destination
Destination dest = message.getDestination();
String topic = dest.getName();
```

### User Properties (Custom Metadata)
```java
// Set properties
SDTMap props = JCSMPFactory.onlyInstance().createMap();
props.putString("userId", "user123");
props.putInteger("retryCount", 3);
props.putBoolean("isPriority", true);
message.setProperties(props);

// Read properties
SDTMap receivedProps = message.getProperties();
String userId = receivedProps.getString("userId");
int retries = receivedProps.getInteger("retryCount");
```

---

## 🧹 Resource Cleanup

### Always Close Resources
```java
try {
    // Use resources
} finally {
    if (flow != null) flow.close();
    if (producer != null) producer.close();
    if (consumer != null) consumer.close();
    if (session != null) session.closeSession();
}
```

### Graceful Shutdown
```java
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    logger.info("Shutting down...");
    cleanup();
}));
```

---

## 🎯 Common Use Cases

### Fan-Out (1 Publisher → Many Subscribers)
```java
// Publisher: Publish to topic
producer.send(message, topic);

// Multiple subscribers each get a copy
// Subscriber 1: Direct topic subscription
// Subscriber 2: Direct topic subscription
// Subscriber 3: Direct topic subscription
```

### Work Queue (Load Balancing)
```java
// Multiple consumers on same non-exclusive queue
// Each message goes to exactly one consumer
// Round-robin distribution

EndpointProperties props = new EndpointProperties();
props.setAccessType(EndpointProperties.ACCESSTYPE_NONEXCLUSIVE);
```

### Priority Queue
```java
// Publish with priority
message.setPriority(200); // High priority

// Consumer receives high-priority messages first
```

### Message Replay
```java
// Queue retains messages even after ACK
// Can replay from specific point in time
// (Requires queue configuration on broker)
```

---

## 💡 Best Practices

1. **Always use try-finally for cleanup**
2. **Use CLIENT_ACK for critical messages**
3. **Design meaningful topic hierarchies**
4. **Use non-exclusive queues for scalability**
5. **Implement proper error handling**
6. **Set appropriate queue quotas**
7. **Use message selectors to reduce network traffic**
8. **Monitor flow events for health checks**
9. **Version your topic structure (/v1/, /v2/)**
10. **Use correlation IDs for request-reply**

---

## 🎓 Study Tips

- ✅ Understand the difference between direct and guaranteed messaging
- ✅ Master topic hierarchy design
- ✅ Practice wildcard subscriptions
- ✅ Know when to use queues vs direct subscriptions
- ✅ Understand message acknowledgment patterns
- ✅ Learn error handling and retry strategies
- ✅ Familiarize with queue configuration options

**This reference covers 90% of common Solace patterns!**
