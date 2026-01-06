# 🎓 Solace Real-Time Admin Dashboard - Learning Project

A hands-on project to master Solace PubSub+ through building a real-time order monitoring system.

## 📚 What You'll Learn

This project covers **all key concepts** for Solace Developer Practitioner certification:

### Core Concepts
- ✅ **Pub/Sub Messaging** - Asynchronous event-driven communication
- ✅ **Guaranteed Messaging** - Persistent, reliable message delivery
- ✅ **Topic Hierarchy** - Organizing events with meaningful structure
- ✅ **Queue-based Consumption** - Durable message storage and load balancing
- ✅ **Message Acknowledgment** - Ensuring reliable processing
- ✅ **Wildcards** - Flexible topic subscriptions (* and >)
- ✅ **Message Properties** - Metadata and filtering
- ✅ **Flow Control** - Managing consumer backpressure
- ✅ **Error Handling** - Graceful failure and recovery

### Advanced Patterns
- ✅ **Queue Provisioning** - Creating and configuring broker resources
- ✅ **Topic-to-Queue Mapping** - Routing messages to queues
- ✅ **Message Selectors** - SQL-like filtering
- ✅ **Publisher Confirmations** - Async acknowledgment of published messages
- ✅ **Flow Events** - Monitoring consumer health
- ✅ **Non-Exclusive Queues** - Load balancing across consumers

---

## 🏗️ Project Architecture

```
┌─────────────┐          ┌──────────────┐          ┌─────────────────┐
│   Order     │  Pub/Sub │    Solace    │  Queue   │     Admin       │
│  Publisher  │─────────>│   PubSub+    │─────────>│   Dashboard     │
│             │  Topics  │    Broker    │  Subscribe│                 │
└─────────────┘          └──────────────┘          └─────────────────┘
                                │
                         Topic Hierarchy:
                    order/v1/{region}/{status}/{priority}
                    
                    Examples:
                    • order/v1/US-EAST/CREATED/NORMAL
                    • order/v1/EU/PAID/URGENT
                    • order/v1/ASIA/SHIPPED/HIGH
```

### Topic Hierarchy Design

```
order/v1/{region}/{status}/{priority}
    │    │     │       │        │
    │    │     │       │        └─ NORMAL | HIGH | URGENT
    │    │     │       └─ CREATED | PAID | SHIPPED | DELIVERED ...
    │    │     └─ US-EAST | US-WEST | EU | ASIA
    │    └─ API version (allows future changes)
    └─ Domain/entity
```

**Why this structure?**
- **Hierarchical**: Enables wildcard subscriptions
- **Versioned**: `/v1/` allows backward-compatible changes
- **Filterable**: Subscribers choose what to receive
- **Scalable**: Easy to add new dimensions

---

## 🚀 Quick Start

### Prerequisites
- Java 11 or higher
- Maven 3.6+
- Docker & Docker Compose

### Step 1: Start Solace Broker

```bash
# Start the broker
docker-compose up -d

# Verify it's running
docker ps

# Access Web UI (optional)
# http://localhost:8080
# Username: admin
# Password: admin
```

The broker will be ready when you see:
```
solace-broker | Running pre-startup checks... OK
solace-broker | Starting Solace PubSub+ Standard...
```

### Step 2: Build the Project

```bash
# Build all modules
mvn clean install

# Or build specific module
cd order-publisher && mvn clean install
cd admin-dashboard && mvn clean install
```

### Step 3: Run the Dashboard (Consumer)

```bash
# Terminal 1: Start the admin dashboard
cd admin-dashboard
mvn exec:java -Dexec.mainClass="com.solace.practice.dashboard.AdminDashboard"
```

You'll see:
```
=== Solace Admin Dashboard Starting ===
✓ Connected to Solace broker
✓ Queue provisioned: admin-dashboard-orders
✓ Subscribed to: order/v1/> (all orders)
✓ Flow receiver started - listening for orders...
Dashboard is running. Press Ctrl+C to stop.
```

### Step 4: Publish Test Orders (Producer)

```bash
# Terminal 2: Run the order publisher
cd order-publisher
mvn exec:java -Dexec.mainClass="com.solace.practice.publisher.OrderPublisher"
```

You'll see orders being published and received in real-time!

---

## 📊 Understanding the Output

### Publisher Output
```
📤 Published: Order[id=abc123, ...] → order/v1/US-EAST/CREATED/NORMAL
```

### Dashboard Output
```
📥 Received: Order[id=abc123, ...] from topic: order/v1/US-EAST/CREATED/NORMAL

================================================================================
📊 REAL-TIME ADMIN DASHBOARD
================================================================================

┌─ OVERALL METRICS
│ Total Orders: 42
│ Total Revenue: $45,678.90
│ Orders/min: 12

├─ BY REGION
│ US-EAST: 15 orders, $18,234.50 revenue
│ US-WEST: 10 orders, $12,445.20 revenue
│ EU: 12 orders, $10,234.10 revenue
│ ASIA: 5 orders, $4,765.10 revenue

├─ BY STATUS
│ CREATED: 8 orders
│ PAID: 12 orders
│ SHIPPED: 15 orders
│ DELIVERED: 7 orders

└─ RECENT ORDERS (Last 5)
   • Order[id=xyz789, customer=CUST-1045, product=Laptop, ...]
```

---

## 🎯 Learning Exercises

### Exercise 1: Modify Topic Subscriptions (Easy)

**Goal**: Practice wildcard subscriptions

In `AdminDashboard.java`, modify `addQueueSubscriptions()`:

```java
// Currently subscribes to ALL orders
Topic allOrders = JCSMPFactory.onlyInstance().createTopic("order/v1/>");

// Try these variations:

// 1. Only US-EAST region
Topic usEast = JCSMPFactory.onlyInstance().createTopic("order/v1/US-EAST/>");

// 2. Only URGENT priority across all regions
Topic urgent = JCSMPFactory.onlyInstance().createTopic("order/v1/*/*/URGENT");

// 3. Only DELIVERED status
Topic delivered = JCSMPFactory.onlyInstance().createTopic("order/v1/*/*/DELIVERED/*");
```

**Expected Result**: Dashboard only receives matching orders

---

### Exercise 2: Add Message Selectors (Medium)

**Goal**: Filter messages based on properties

In `AdminDashboard.java`, add selector to `createFlowReceiver()`:

```java
// Only receive high-value orders
flowProps.setSelector("totalAmount > 1000");

// Or combine conditions
flowProps.setSelector("totalAmount > 500 AND priority = 'URGENT'");
```

**Expected Result**: Only orders matching criteria are received

---

### Exercise 3: Implement Request-Reply Pattern (Advanced)

**Goal**: Add synchronous order validation

Create a new service that:
1. Subscribes to `order/v1/*/CREATED/*`
2. Validates the order
3. Replies to the publisher with validation result

```java
// In publisher - set reply-to topic
message.setReplyTo(JCSMPFactory.onlyInstance().createTopic("order/reply/" + orderId));

// In validator - send reply
producer.sendReply(requestMessage, replyMessage);
```

---

### Exercise 4: Add Dead Letter Queue (Advanced)

**Goal**: Handle failed messages

1. Create a DLQ: `admin-dashboard-orders-dlq`
2. Configure max redelivery count
3. Monitor failed messages

```java
endpointProps.setMaxMsgRedelivery(3); // Retry 3 times, then to DLQ
```

---

## 🔧 Configuration Options

### Queue Types

```java
// Exclusive Queue (only one consumer)
endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);

// Non-Exclusive Queue (load balancing)
endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_NONEXCLUSIVE);
```

### Acknowledgment Modes

```java
// Auto-acknowledge (easier but less control)
flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_AUTO);

// Client-acknowledge (more control, recommended)
flowProps.setAckMode(JCSMPProperties.SUPPORTED_MESSAGE_ACK_CLIENT);
```

### Message Delivery Mode

```java
// Persistent (guaranteed delivery)
message.setDeliveryMode(DeliveryMode.PERSISTENT);

// Direct (best effort, faster)
message.setDeliveryMode(DeliveryMode.DIRECT);
```

---

## 🐛 Troubleshooting

### Issue: Connection Refused
```
Solution: Ensure Docker is running and broker is started
docker-compose up -d
docker logs solace-broker
```

### Issue: Queue Already Exists Error
```
Solution: This is normal - code uses FLAG_IGNORE_ALREADY_EXISTS
The queue persists between runs
```

### Issue: No Messages Received
```
Solution: Check topic subscriptions match published topics
- Publisher sends to: order/v1/{region}/{status}/{priority}
- Dashboard subscribes to: order/v1/>
```

### Issue: Messages Not Acknowledged
```
Solution: Ensure you're calling message.ackMessage()
Without ACK, messages pile up in the queue
```

---

## 📖 Key Solace Concepts Explained

### 1. Pub/Sub vs Queue

**Pub/Sub (Direct Messaging)**
- ❌ Lost if no subscriber listening
- ✅ Ultra-low latency
- ✅ One-to-many (fan-out)
- Use for: Real-time updates, telemetry

**Queue (Guaranteed Messaging)**
- ✅ Persisted until consumed
- ✅ Load balancing across consumers
- ✅ Guaranteed delivery
- Use for: Transactions, commands, critical events

### 2. Topic Wildcards

```
*  = Exactly one level
>  = Zero or more levels

Examples:
order/v1/US-EAST/>          matches all US-EAST orders
order/v1/*/CREATED/*        matches all CREATED orders
order/v1/US-*/PAID/>        matches US-EAST and US-WEST PAID orders
```

### 3. Message Acknowledgment

```java
// Option 1: Explicit ACK (recommended)
message.ackMessage();

// Option 2: Reject (requeue for retry)
// Message goes back to queue
flowReceiver.nack(message);

// Option 3: Settle (remove without processing)
// Used for poison messages
message.settle();
```

### 4. Publisher Confirmations

```java
// Callback when message is accepted by broker
public void handleError(String messageID, JCSMPException cause, long timestamp) {
    // Message was rejected - handle error
}

// Message successfully spooled
// (implicit - no error callback)
```

---

## 🎓 Next Steps

1. **Extend the Project**:
   - Add inventory service
   - Add payment service  
   - Implement request-reply for validation
   - Add monitoring/alerting

2. **Explore Advanced Features**:
   - Message replay
   - Last-value-queues
   - Partitioned queues
   - Message TTL (Time To Live)

3. **Performance Tuning**:
   - Window size (flow control)
   - Batch acknowledgment
   - Compression
   - Multiple flows for parallel processing

4. **Production Readiness**:
   - HA configuration
   - Security (TLS, OAuth)
   - Monitoring with Prometheus
   - DR/backup strategies

---

## 📚 Additional Resources

- [Solace Developer Portal](https://docs.solace.com/)
- [Java API Reference](https://docs.solace.com/API-Developer-Online-Ref-Documentation/java/index.html)
- [PubSub+ Best Practices](https://docs.solace.com/Best-Practices/Best-Practices.htm)
- [Solace Community](https://solace.community/)

---

## 🤝 Need Help?

- Check the comments in the code - they explain every concept
- Review the console output - it shows what's happening
- Experiment! Change values and see what happens
- Questions? The code is designed to be self-documenting

**Happy Learning! 🚀**
