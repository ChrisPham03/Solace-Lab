package com.solace.practice.model;

public enum OrderStatus {
    CREATED,
    VALIDATED,
    PAID,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
```

---

## 💡 Why an Enum?

An order goes through a **lifecycle**:
```
CREATED → VALIDATED → PAID → SHIPPED → DELIVERED
                ↓
            CANCELLED
```

**Why this matters for Solace:**

Our topic structure will be:
```
order/v1/{region}/{status}/{priority}
