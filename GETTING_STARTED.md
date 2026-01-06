# 🚀 Quick Start Guide - For Certified Practitioners

Congratulations on passing the Solace Developer Practitioner certification! Now let's get your hands dirty with real code.

---

## ⚡ 5-Minute Setup

### Step 1: Start Solace Broker (1 min)

```bash
cd solace-admin-dashboard
docker-compose up -d

# Wait 30-60 seconds for broker to be ready
docker logs -f solace-broker
# Look for: "Solace PubSub+ Standard starting..."
```

Access Web UI: **http://localhost:8080** (admin/admin)

---

### Step 2: Build the Project (1 min)

```bash
# Build everything
mvn clean install
```

---

### Step 3: See It In Action (3 min)

**Terminal 1**: Start the dashboard (consumer)
```bash
cd admin-dashboard
mvn exec:java -Dexec.mainClass="com.solace.practice.dashboard.AdminDashboard"
```

**Terminal 2**: Publish test orders (producer)
```bash
cd order-publisher
mvn exec:java -Dexec.mainClass="com.solace.practice.publisher.OrderPublisher"
```

**You'll see real-time orders flowing!** 🎉

---

## 🎯 What You Have

### Project Structure
```
solace-admin-dashboard/
├── order-publisher/          ← Publishes test orders
├── admin-dashboard/          ← Monitors orders in real-time
├── docker-compose.yml        ← Local Solace broker
├── README.md                 ← Full documentation
├── SOLACE_PATTERNS.md        ← Copy-paste code patterns
├── ADVANCED_SCENARIOS.md     ← Real-world exercises
└── FAQ.md                    ← Troubleshooting guide
```

### What's Already Built

✅ **Complete Working System**
- Real-time order monitoring dashboard
- Topic hierarchy: `order/v1/{region}/{status}/{priority}`
- Guaranteed messaging with queues
- Publisher with confirmations
- Consumer with acknowledgments

✅ **All Key Patterns**
- Pub/Sub messaging
- Queue-based consumption
- Wildcards and selectors
- Flow control
- Error handling

✅ **Production-Ready Code**
- Heavily commented explaining every Solace concept
- Metrics tracking
- Proper error handling
- Connection management

---

## 📚 Your Learning Path

### Week 1: Foundation
**Goal**: Understand the existing code

1. **Read the Code** (2 hours)
   - Start with `AdminDashboard.java` - it has detailed comments
   - Then `OrderPublisher.java` - see how messages are sent
   - Check `SOLACE_PATTERNS.md` - your reference guide

2. **Experiment** (1 hour)
   - Modify topic subscriptions (line 145 in AdminDashboard)
   - Change delivery modes (PERSISTENT vs DIRECT)
   - Adjust acknowledgment modes (CLIENT vs AUTO)

3. **Monitor** (30 min)
   - Use Solace Web UI to watch queues fill and drain
   - Check message rates and queue depth
   - View topic subscriptions

**Checkpoint**: Can you explain why we use queues instead of direct pub/sub?

---

### Week 2: Advanced Patterns
**Goal**: Add new features

**Scenario 1: High-Value Alerts** (2 hours)
- Implement message selectors
- Create priority queue
- Filter orders > $5,000
- See: `ADVANCED_SCENARIOS.md` Section 1

**Checkpoint**: Messages are filtered at broker, not client. Why is this important?

---

### Week 3: Request-Reply
**Goal**: Synchronous patterns

**Scenario 2: Order Validation** (3 hours)
- Implement request-reply
- Use correlation IDs
- Handle timeouts
- See: `ADVANCED_SCENARIOS.md` Section 2

**Checkpoint**: Can you explain how async transport enables sync behavior?

---

### Week 4-5: Production Patterns
**Goal**: Real-world scenarios

- **Scenario 3**: Multi-region load balancing (2 hours)
- **Scenario 4**: Dead Letter Queue monitoring (3 hours)
- **Scenario 5**: Circuit breaker pattern (4 hours)

---

## 🔧 Common Commands

### Broker Management
```bash
# Start broker
docker-compose up -d

# View logs
docker logs -f solace-broker

# Stop broker
docker-compose down

# Stop and remove data
docker-compose down -v
```

### Build and Run
```bash
# Build specific module
cd admin-dashboard && mvn clean install

# Run with different log level
mvn exec:java -Dexec.mainClass="..." -Dorg.slf4j.simpleLogger.defaultLogLevel=DEBUG

# Package as JAR
mvn clean package
java -jar target/admin-dashboard-1.0-SNAPSHOT.jar
```

### Monitoring
```bash
# Check Solace is running
curl http://localhost:8080/health-check/guaranteed-active

# View queue details (requires solacectl CLI)
solacectl queue show admin-dashboard-orders
```

---

## 🐛 Troubleshooting

### Issue: "Connection refused"
```bash
# Solution: Broker not ready yet
docker logs solace-broker | grep "Ready"
# Wait for: "PubSub+ Standard is ready!"
```

### Issue: "No messages received"
```bash
# Solution: Check topic subscription
# Publisher sends: order/v1/US-EAST/CREATED/NORMAL
# Dashboard must subscribe: order/v1/>  (matches everything)
```

### Issue: "Queue already exists"
```bash
# Solution: This is NORMAL
# Code uses FLAG_IGNORE_ALREADY_EXISTS
# Queue persists between runs
```

### Issue: Maven build fails
```bash
# Solution: Clean and rebuild
mvn clean
rm -rf ~/.m2/repository/com/solacesystems
mvn install
```

---

## 💡 Pro Tips for Certification Holders

You know the theory - now connect it to practice:

### Topic Design
**Theory**: Hierarchical topic structure
**Practice**: See `order/v1/{region}/{status}/{priority}` in OrderPublisher.java
**Why**: Enables flexible subscriptions without changing code

### Wildcards
**Theory**: `*` = one level, `>` = multiple levels
**Practice**: Try `order/v1/US-EAST/>` vs `order/v1/*/CREATED/*`
**Why**: Subscribe to exactly what you need

### Guaranteed Messaging
**Theory**: Messages persist until acknowledged
**Practice**: Stop dashboard, publish orders, start dashboard - they're still there!
**Why**: Reliability for critical events

### Flow Control
**Theory**: Backpressure prevents overload
**Practice**: See window size in AdminDashboard.java line 203
**Why**: Protects slow consumers

---

## 🎯 Your First Tasks

### Task 1: Modify Subscriptions (10 min)
In `AdminDashboard.java` line 145, change:
```java
Topic allOrders = JCSMPFactory.onlyInstance().createTopic("order/v1/>");
```
To:
```java
Topic urgentOnly = JCSMPFactory.onlyInstance().createTopic("order/v1/*/*/URGENT");
```
**Result**: You'll only see URGENT priority orders

### Task 2: Add Message Selector (15 min)
In `AdminDashboard.java` line 203, add:
```java
flowProps.setSelector("totalAmount > 1000");
```
**Result**: Only high-value orders appear

### Task 3: Monitor Queue Depth (10 min)
1. Start dashboard
2. Don't publish anything
3. Open Web UI → Queues → admin-dashboard-orders
4. Now publish orders
5. Watch queue depth go up and down
**Result**: You see guaranteed messaging in action

---

## 📖 Reference Guides

**Quick Code Snippets**: `SOLACE_PATTERNS.md`
- Need to publish? Check Publishing section
- Need to consume? Check Consuming section
- Need selectors? Check Filtering section

**Full Examples**: Your existing code
- Everything is already implemented
- Read the comments - they explain WHY not just WHAT

**Exercises**: `ADVANCED_SCENARIOS.md`
- Real-world patterns
- Step-by-step implementations
- Difficulty rated

**Troubleshooting**: `FAQ.md`
- Common errors
- Solutions
- Explanations

---

## 🚀 Next Steps

1. **Run the basic system** (now!)
2. **Read AdminDashboard.java** (30 min)
3. **Complete Task 1-3 above** (35 min)
4. **Start Scenario 1** (tomorrow)

---

## 💬 Questions?

- Check code comments - they're extensive
- Read FAQ.md - covers common issues
- Experiment - you can't break anything!
- Web UI helps visualize what's happening

**Most importantly**: Don't just read the code, RUN it. Change things. Break it. Fix it. That's how you learn!

---

## 🎓 Expected Timeline

- **Week 1**: Comfortable with basic pub/sub
- **Week 2**: Understanding queues and flows
- **Week 3**: Implementing request-reply
- **Week 4**: Handling errors and DLQs
- **Week 5**: Performance tuning

**You've got this!** 💪

The certification gave you the theory. This project gives you the experience. Combined, you'll be a confident Solace developer.

Happy coding! 🚀
