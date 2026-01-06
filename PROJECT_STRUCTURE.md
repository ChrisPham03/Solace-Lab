# 📁 Project Structure Overview

Complete directory structure and file organization for the Solace Admin Dashboard project.

```
solace-admin-dashboard/
│
├── 📄 README.md                          # Main documentation & getting started guide
├── 📄 SOLACE_PATTERNS.md                 # Quick reference for Solace patterns
├── 📄 FAQ.md                             # Troubleshooting & frequently asked questions
├── 📄 pom.xml                            # Parent Maven POM (multi-module)
├── 🐳 docker-compose.yml                 # Solace broker setup
├── 🔒 .gitignore                         # Git ignore rules
│
├── 🚀 run-dashboard.sh                   # Script to run admin dashboard
├── 🚀 run-publisher.sh                   # Script to run order publisher
│
├── 📦 order-publisher/                   # Module 1: Test Data Generator
│   ├── 📄 pom.xml                        # Module POM
│   └── src/main/java/com/solace/practice/
│       ├── model/
│       │   ├── Order.java                # Order domain model
│       │   └── OrderStatus.java          # Order lifecycle states enum
│       └── publisher/
│           └── OrderPublisher.java       # ⭐ Main publisher class
│                                         #    • Publishes test orders
│                                         #    • Demonstrates topic hierarchy
│                                         #    • Shows guaranteed messaging
│                                         #    • Publisher confirmations
│
└── 📦 admin-dashboard/                   # Module 2: Real-Time Monitor (Main Learning)
    ├── 📄 pom.xml                        # Module POM
    └── src/main/java/com/solace/practice/
        ├── model/
        │   ├── Order.java                # Order domain model (copy)
        │   └── OrderStatus.java          # Order status enum (copy)
        └── dashboard/
            ├── AdminDashboard.java       # ⭐ Main dashboard class
            │                             #    • Queue provisioning
            │                             #    • Topic subscriptions
            │                             #    • Flow receiver setup
            │                             #    • Message consumption
            │                             #    • Acknowledgment patterns
            │                             #    • Error handling
            │
            └── MetricsTracker.java       # Real-time metrics collection
                                          #    • Orders per second
                                          #    • Revenue tracking
                                          #    • Regional distribution
                                          #    • Status breakdown
```

---

## 📚 Key Learning Files

### 🎯 Primary Learning Sources

1. **AdminDashboard.java** (⭐⭐⭐ MOST IMPORTANT)
   - 500+ lines of heavily commented code
   - Covers ALL consumer patterns
   - Queue provisioning, topic subscriptions, flow control
   - Message acknowledgment, error handling
   - THIS IS WHERE YOU LEARN THE MOST

2. **OrderPublisher.java** (⭐⭐)
   - Publisher patterns and best practices
   - Topic hierarchy design
   - Guaranteed messaging
   - Publisher confirmations
   - User properties

3. **SOLACE_PATTERNS.md** (⭐⭐⭐ ESSENTIAL REFERENCE)
   - Quick reference guide
   - Code snippets for every pattern
   - Copy-paste ready examples
   - Study this alongside the code

---

## 🎓 How to Use This Project

### Step 1: Read the Documentation
1. Start with **README.md** - Overview and setup
2. Skim **SOLACE_PATTERNS.md** - Know what patterns exist
3. Keep **FAQ.md** open - For troubleshooting

### Step 2: Run the Code
1. Start Solace: `docker-compose up -d`
2. Build: `mvn clean install`
3. Run dashboard: `./run-dashboard.sh` (or manual command)
4. Run publisher: `./run-publisher.sh`

### Step 3: Study the Code
**Read in this order:**

1. **OrderPublisher.java**
   - Lines 1-100: Connection setup
   - Lines 100-200: Publishing logic
   - Lines 200-300: Topic hierarchy design
   - Lines 300-end: Publisher event handling

2. **AdminDashboard.java**
   - Lines 1-100: Session setup
   - Lines 100-200: Queue provisioning
   - Lines 200-300: Topic subscriptions
   - Lines 300-400: Flow receiver creation
   - Lines 400-500: Message listener implementation
   - Lines 500-end: Error handling

3. **MetricsTracker.java**
   - Real-time metrics patterns
   - Thread-safe data structures

### Step 4: Experiment
Try the exercises in README.md:
- Modify topic subscriptions
- Add message selectors
- Implement new features

---

## 🔧 Configuration Files

### docker-compose.yml
```yaml
Purpose: Local Solace broker setup
Ports:
  - 8080: Web UI (http://localhost:8080)
  - 55555: SMF (messaging port)
  - 9000: REST API
Credentials: admin/admin
```

### pom.xml (Parent)
```xml
Purpose: Maven multi-module project
Modules:
  - order-publisher
  - admin-dashboard
Dependencies:
  - sol-jcsmp (Solace Java API)
  - jackson (JSON)
  - slf4j (Logging)
```

---

## 📊 Component Interaction

```
┌─────────────────────────────────────────────────────────────┐
│                      SOLACE BROKER                          │
│  ┌──────────────┐         ┌──────────────┐                 │
│  │   Topics     │         │   Queues     │                 │
│  │              │         │              │                 │
│  │ order/v1/    │────────>│ admin-       │                 │
│  │   US-EAST/   │  routed │ dashboard-   │                 │
│  │     CREATED/ │   to    │ orders       │                 │
│  │       NORMAL │         │              │                 │
│  └──────────────┘         └──────────────┘                 │
│        ▲                         │                          │
└────────┼─────────────────────────┼──────────────────────────┘
         │                         │
         │ publish                 │ consume
         │                         ▼
    ┌────────────┐         ┌──────────────┐
    │  Order     │         │    Admin     │
    │  Publisher │         │  Dashboard   │
    └────────────┘         └──────────────┘
```

---

## 🎯 What Each File Teaches

| File | Concepts Covered |
|------|------------------|
| **OrderPublisher.java** | Publishing, Topic design, Guaranteed messaging, Confirmations |
| **AdminDashboard.java** | Queues, Subscriptions, Flows, Acknowledgment, Error handling |
| **MetricsTracker.java** | Thread safety, Real-time processing, Data aggregation |
| **SOLACE_PATTERNS.md** | ALL patterns in quick reference format |
| **README.md** | Architecture, Exercises, Best practices |
| **FAQ.md** | Troubleshooting, Common issues, Solutions |

---

## 💾 Data Flow

1. **Publisher** creates Order object
2. **Publisher** serializes to JSON
3. **Publisher** publishes to topic with hierarchy
4. **Broker** receives message on topic
5. **Broker** routes to queue (topic-to-queue mapping)
6. **Broker** stores persistently
7. **Dashboard** flow receiver gets message
8. **Dashboard** deserializes JSON
9. **Dashboard** processes and updates metrics
10. **Dashboard** acknowledges message
11. **Broker** removes from queue

---

## 🧪 Testing Scenarios

The project is designed to let you test:

✅ **Happy Path**
- Messages published and received
- Metrics updated correctly
- Dashboard displays real-time data

✅ **Error Scenarios**
- Publisher disconnects (auto-reconnect)
- Consumer crashes (messages redelivered)
- Invalid JSON (error handling)
- Queue full (quota management)

✅ **Performance**
- Burst of messages (flow control)
- Multiple consumers (load balancing)
- High throughput (metrics tracking)

---

## 📖 Learning Path

### Week 1-2: Foundation
- ✅ Run the project successfully
- ✅ Understand topic hierarchy
- ✅ Study OrderPublisher.java
- ✅ Modify topic subscriptions

### Week 3-4: Advanced
- ✅ Study AdminDashboard.java thoroughly
- ✅ Implement message selectors
- ✅ Add new metrics
- ✅ Try different queue configurations

### Week 5-6: Mastery
- ✅ Build request-reply pattern
- ✅ Implement DLQ handling
- ✅ Add new services (inventory, payment)
- ✅ Performance testing

---

## 🎓 Certification Mapping

This project covers topics from Solace Developer Practitioner exam:

| Exam Topic | Covered In |
|------------|------------|
| Pub/Sub basics | OrderPublisher.java |
| Topic structure | OrderPublisher.java, README.md |
| Guaranteed messaging | Both modules |
| Queues | AdminDashboard.java |
| Message acknowledgment | AdminDashboard.java |
| Flow control | AdminDashboard.java |
| Error handling | Both modules |
| Best practices | All documentation |

---

## 🚀 Quick Commands Reference

```bash
# Start everything
docker-compose up -d
mvn clean install
./run-dashboard.sh    # Terminal 1
./run-publisher.sh    # Terminal 2

# Stop
Ctrl+C in both terminals
docker-compose down

# Clean rebuild
mvn clean install

# View broker logs
docker logs -f solace-broker

# Access Web UI
open http://localhost:8080
```

---

**This project structure is designed for maximum learning efficiency!**
Every file has a purpose, every comment teaches a concept. 🎓
