# ❓ FAQ & Troubleshooting Guide

Common questions and solutions for the Solace Admin Dashboard project.

---

## 🚀 Getting Started Issues

### Q: How do I know if Solace broker is running?

**A:** Run this command:
```bash
docker ps | grep solace-broker
```

You should see output like:
```
solace-broker   solace/solace-pubsub-standard:latest   ...   Up 2 minutes
```

If not running:
```bash
docker-compose up -d
```

### Q: Broker starts but I can't connect

**A:** Wait 30-60 seconds for broker to fully initialize. Check logs:
```bash
docker logs solace-broker
```

Look for: `"Running pre-startup checks... OK"`

### Q: Maven build fails with dependency errors

**A:** Clear local Maven cache:
```bash
mvn clean install -U
```

The `-U` flag forces Maven to update dependencies.

---

## 📡 Connection Issues

### Q: "Connection refused" error

**Symptoms:**
```
com.solacesystems.jcsmp.JCSMPTransportException: Error communicating with the router
```

**Solutions:**
1. Verify broker is running: `docker ps`
2. Check port 55555 is not blocked: `telnet localhost 55555`
3. Verify broker health: `docker logs solace-broker`
4. Ensure no other process is using port 55555

### Q: "Unable to create session" error

**Symptoms:**
```
com.solacesystems.jcsmp.JCSMPException: Error Response (503) - Service Unavailable
```

**Solutions:**
1. Check VPN name is "default" (case-sensitive)
2. Verify credentials: admin/admin
3. Wait for broker to fully start
4. Restart broker: `docker-compose restart`

### Q: Connection works but no messages received

**Checklist:**
- ✅ Is the publisher running?
- ✅ Are topic subscriptions correct?
- ✅ Is flow receiver started? (`flow.start()`)
- ✅ Check logs for exceptions
- ✅ Verify queue name matches

---

## 📨 Message Publishing Issues

### Q: Messages published but not received

**Debug Steps:**

1. **Check Topic Match:**
   ```
   Published to: order/v1/US-EAST/CREATED/NORMAL
   Subscribed to: order/v1/>
   ✓ Matches!
   
   Published to: order/US-EAST/CREATED
   Subscribed to: order/v1/>
   ✗ Doesn't match! (missing /v1/)
   ```

2. **Check Queue Subscriptions:**
   ```bash
   # In Solace Web UI (http://localhost:8080)
   # Navigate to: Queues → admin-dashboard-orders → Subscriptions
   # Verify: order/v1/> is listed
   ```

3. **Verify Queue Has Messages:**
   ```bash
   # In Solace Web UI
   # Queues → admin-dashboard-orders
   # Check: "Messages Queued" counter
   ```

### Q: "Quota Exceeded" error

**Symptoms:**
```
JCSMPException: Quota Exceeded
```

**Solutions:**
1. Increase queue quota:
   ```java
   endpointProps.setQuota(500); // 500 MB instead of 100
   ```

2. Delete and re-provision queue:
   ```bash
   # In Solace Web UI, delete the queue
   # Or in code, remove FLAG_IGNORE_ALREADY_EXISTS
   ```

3. Ensure messages are being acknowledged:
   ```java
   message.ackMessage(); // Don't forget this!
   ```

---

## 🎯 Topic & Subscription Issues

### Q: Wildcard subscriptions not working

**Common Mistakes:**

❌ **Wrong:**
```java
Topic topic = JCSMPFactory.onlyInstance().createTopic("order/v1/*");
// Single * at end matches NOTHING
```

✅ **Correct:**
```java
Topic topic = JCSMPFactory.onlyInstance().createTopic("order/v1/>");
// Multi-level > matches everything under order/v1/
```

❌ **Wrong:**
```java
"order/v1/>/*"  // Can't have anything after >
```

✅ **Correct:**
```java
"order/v1/>"    // > must be at the end
"order/v1/*/*"  // Or use specific wildcards
```

### Q: How do I subscribe to multiple specific topics?

**A:** Add multiple subscriptions to the queue:
```java
session.addSubscription(queue, 
    JCSMPFactory.onlyInstance().createTopic("order/v1/US-EAST/>"), 
    JCSMPSession.WAIT_FOR_CONFIRM);
    
session.addSubscription(queue, 
    JCSMPFactory.onlyInstance().createTopic("order/v1/EU/>"), 
    JCSMPSession.WAIT_FOR_CONFIRM);
```

---

## 🔄 Message Acknowledgment Issues

### Q: Messages keep being redelivered

**Cause:** Not acknowledging messages

**Solution:**
```java
@Override
public void onReceive(BytesXMLMessage message) {
    try {
        processMessage(message);
        message.ackMessage(); // ← Don't forget this!
    } catch (Exception e) {
        logger.error("Error", e);
        // If you don't ACK here, message will be redelivered
    }
}
```

### Q: How do I reject a message?

**A:** In JCSMP, just don't call `ackMessage()`. Message will be redelivered after timeout.

For permanent rejection (move to DLQ), configure max redelivery:
```java
endpointProps.setMaxMsgRedelivery(3); // After 3 retries → DLQ
```

### Q: Messages acknowledged but still in queue

**Cause:** Looking at wrong metric in Solace Web UI

**Explanation:**
- "Messages Queued" = Waiting to be delivered
- "Messages Redelivered" = Being redelivered
- "Messages Acknowledged" = Successfully processed

If acknowledged, they're gone from queue (check "Messages Queued").

---

## 🚦 Flow Control Issues

### Q: Consumer stops receiving messages

**Symptoms:**
```
Flow Event: FLOW_INACTIVE
```

**Solutions:**

1. **Check if flow was stopped:**
   ```java
   flow.start(); // Make sure you call this
   ```

2. **Window size filled:**
   - Too many unacknowledged messages
   - Solution: Acknowledge messages faster
   ```java
   message.ackMessage(); // Don't batch too many
   ```

3. **Connection lost:**
   - Check network
   - Session will auto-reconnect if configured:
   ```java
   properties.setProperty(JCSMPProperties.REAPPLY_SUBSCRIPTIONS, true);
   ```

### Q: How do I pause/resume consumption?

**A:**
```java
flow.stop();   // Pause - messages stay in queue
// ... do something ...
flow.start();  // Resume
```

---

## 💾 Queue Configuration Issues

### Q: "Queue already exists" error

**A:** This is usually fine! Use this flag:
```java
session.provision(queue, endpointProps, JCSMPSession.FLAG_IGNORE_ALREADY_EXISTS);
```

To force re-creation:
1. Delete via Web UI: http://localhost:8080
2. Or don't use FLAG_IGNORE_ALREADY_EXISTS

### Q: Want to reset queue to empty state

**Options:**

1. **Delete and recreate** (loses configuration):
   - Delete in Web UI
   - Restart app to recreate

2. **Purge messages** (keeps configuration):
   ```bash
   # In Solace CLI or Web UI
   # Queues → admin-dashboard-orders → Actions → Clear Messages
   ```

### Q: Exclusive vs Non-Exclusive queue?

**Exclusive:**
- ✅ Guarantees message ordering
- ❌ Only ONE consumer allowed
- Use for: Sequential processing

```java
endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE);
```

**Non-Exclusive:**
- ✅ Multiple consumers (load balancing)
- ❌ No ordering guarantee
- Use for: Parallel processing, high throughput

```java
endpointProps.setAccessType(EndpointProperties.ACCESSTYPE_NONEXCLUSIVE);
```

---

## 🔍 Message Selector Issues

### Q: Selector not filtering messages

**Common Issues:**

1. **Wrong syntax:**
   ```java
   // ❌ Wrong
   flowProps.setSelector("amount > 1000");  // Property is "totalAmount" not "amount"
   
   // ✅ Correct
   flowProps.setSelector("totalAmount > 1000");
   ```

2. **Property not set by publisher:**
   ```java
   // Publisher MUST set the property
   SDTMap props = JCSMPFactory.onlyInstance().createMap();
   props.putDouble("totalAmount", order.getTotalAmount().doubleValue());
   message.setProperties(props);
   ```

3. **Type mismatch:**
   ```java
   // ❌ Wrong
   props.putString("totalAmount", "999.99");  // String
   flowProps.setSelector("totalAmount > 1000"); // Expects number
   
   // ✅ Correct
   props.putDouble("totalAmount", 999.99);  // Number
   ```

### Q: Can I combine selectors with topic wildcards?

**A:** Yes! Both work together:
```java
// Subscribe to topic
session.addSubscription(queue, 
    JCSMPFactory.onlyInstance().createTopic("order/v1/US-EAST/>"), 
    JCSMPSession.WAIT_FOR_CONFIRM);

// AND filter by selector
flowProps.setSelector("totalAmount > 1000");

// Result: Only US-EAST orders over $1000
```

---

## 📊 Performance Issues

### Q: Slow message processing

**Solutions:**

1. **Batch acknowledgments:**
   ```java
   private List<BytesXMLMessage> batch = new ArrayList<>();
   
   public void onReceive(BytesXMLMessage message) {
       batch.add(message);
       if (batch.size() >= 100) {
           processBatch(batch);
           batch.forEach(BytesXMLMessage::ackMessage);
           batch.clear();
       }
   }
   ```

2. **Multiple flows (parallel processing):**
   ```java
   // Create 4 flows on same queue
   for (int i = 0; i < 4; i++) {
       FlowReceiver flow = session.createFlow(...);
       flow.start();
   }
   ```

3. **Increase window size:**
   ```java
   flowProps.setWindowSize(255); // Default is 50
   ```

### Q: Publisher too slow

**Solutions:**

1. **Send without waiting for confirmation:**
   - Persistent messages are async already
   - Don't call `Thread.sleep()` between messages

2. **Use direct messaging if appropriate:**
   ```java
   message.setDeliveryMode(DeliveryMode.DIRECT);
   // Much faster, but no guarantee
   ```

3. **Batch similar operations:**
   - Don't create new Topic objects for each message
   ```java
   // ❌ Inefficient
   for (Order order : orders) {
       Topic t = JCSMPFactory.onlyInstance().createTopic(getTopicFor(order));
       producer.send(message, t);
   }
   
   // ✅ Better
   Map<String, Topic> topicCache = new HashMap<>();
   for (Order order : orders) {
       String topicStr = getTopicFor(order);
       Topic t = topicCache.computeIfAbsent(topicStr, 
           k -> JCSMPFactory.onlyInstance().createTopic(k));
       producer.send(message, t);
   }
   ```

---

## 🐛 Common Code Issues

### Q: "ClosedFacilityException"

**Cause:** Trying to use a closed session/producer/flow

**Solution:**
```java
// Don't close in one thread while using in another
// Always check if session is connected:
if (session != null && !session.isClosed()) {
    // Use session
}
```

### Q: "NullPointerException" on message properties

**Cause:** Properties not set by publisher

**Solution:**
```java
SDTMap props = message.getProperties();
if (props != null && props.containsKey("totalAmount")) {
    double amount = props.getDouble("totalAmount");
}
```

### Q: JSON deserialization fails

**Solutions:**

1. **Register JavaTimeModule:**
   ```java
   ObjectMapper mapper = new ObjectMapper()
       .registerModule(new JavaTimeModule());
   ```

2. **Match field names:**
   ```java
   @JsonProperty("orderId")  // Must match JSON key
   private String orderId;
   ```

3. **Handle unknown properties:**
   ```java
   @JsonIgnoreProperties(ignoreUnknown = true)
   public class Order { ... }
   ```

---

## 🔐 Security Issues

### Q: Can't connect with different credentials

**A:** Default broker accepts any credentials. For testing, use admin/admin.

For production, configure:
1. Create client-username in Solace
2. Set ACL profiles
3. Update connection properties:
   ```java
   properties.setProperty(JCSMPProperties.USERNAME, "myapp");
   properties.setProperty(JCSMPProperties.PASSWORD, "secret");
   ```

---

## 🌐 Web UI Issues

### Q: Can't access http://localhost:8080

**Solutions:**
1. Check port mapping: `docker-compose.yml` has `8080:8080`
2. Try: http://127.0.0.1:8080
3. Check if port is in use: `lsof -i :8080`
4. Try different port in docker-compose.yml: `"8081:8080"`

### Q: Web UI credentials?

**A:** 
- Username: `admin`
- Password: `admin`
- (As configured in docker-compose.yml)

---

## 💡 Best Practices

### ✅ DO:
- Always close resources in finally blocks
- Use CLIENT_ACK for important messages
- Set reasonable queue quotas
- Implement error handling
- Use meaningful topic hierarchies
- Monitor flow events
- Log extensively during development

### ❌ DON'T:
- Use > wildcard in the middle of topic
- Forget to acknowledge messages
- Hard-code connection properties
- Ignore publisher/flow events
- Create topic objects in tight loops
- Use exclusive queues unless needed
- Publish to topics with spaces

---

## 🆘 Still Stuck?

1. **Check the logs** - Both application and broker logs
2. **Use Solace Web UI** - Monitor queues, messages, connections
3. **Enable debug logging:**
   ```java
   System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");
   ```

4. **Simplify** - Remove message selectors, try direct subscriptions
5. **Start fresh** - Delete queue, restart broker
6. **Read the code comments** - They explain concepts in detail

**Still need help?**
- Solace Community: https://solace.community/
- Documentation: https://docs.solace.com/

---

**Remember: Most issues are due to:**
1. Topic mismatch (95% of cases!)
2. Forgot to acknowledge messages
3. Broker not fully started
4. Wrong queue subscription

**Happy debugging! 🐛→✨**
