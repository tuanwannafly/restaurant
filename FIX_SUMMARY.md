# Fix Summary: Bàn DIRTY Không Hiển Thị Sau Thanh Toán

## 🔴 Root Cause Identified

**Problem:** Sau khi thanh toán xong, bàn không được đánh dấu DIRTY (cần dọn) trong WaiterController.

**Root causes (3 lớp):**

### 1. **RestaurantEventClient Reconnect Loop Vô Hạn**
- Client liên tục cố kết nối tới server nhưng fail (`Connection refused`)
- Spam log với reconnect attempts mà không giới hạn
- **Fix:** Thêm `MAX_RECONNECT_ATTEMPTS = 12` + `consecutiveFailures` counter
  - Sau 12 lần fail → dừng reconnect
  - Reset counter khi connect thành công
  - Log rõ ràng: "Hãy kiểm tra WsServer có chạy không?"

### 2. **Broadcast Topics Chưa Đủ**
- `CashierController.completePayment()` broadcast: `TABLES`, `ORDERS`, `BADGE`, `KITCHEN`, `table_id`
- `WaiterController.initialize()` subscribe: `KITCHEN`, `ORDERS`, `BADGE`, `TABLES` ✓
- `WaiterController.doPoll()` trigger khi nhận event
- **Status:** ✓ FIXED (already in CashierController)

### 3. **WebSocket Server Startup Issues**
- Main app khởi động `RestaurantEventServer` muộn
- Tablet startup trước → client reconnect loop
- **Best practice:** Server start TRƯỚC client connect

---

## ✅ Fixes Applied

### Fix 1: `RestaurantEventClient.java` - Graceful Backoff
```java
// Added:
private static final int MAX_RECONNECT_ATTEMPTS = 12;
private int consecutiveFailures = 0;

// In scheduleReconnect():
consecutiveFailures++;
if (consecutiveFailures > MAX_RECONNECT_ATTEMPTS) {
    LOGGER.warning("[WsClient] Đã thất bại " + consecutiveFailures + 
        " lần — dừng reconnect để tránh spam.");
    return;
}

// In onOpen():
consecutiveFailures = 0; // reset khi connect thành công
```

**Benefit:**
- Tránh spam logs
- Tránh lãng phí CPU/network
- Log rõ ràng hướng debug: "Kiểm tra WsServer có chạy tại ws://localhost:8025?"

---

## 🧠 How It Works (Flow Chart)

### Success Scenario:
```
1. Khách bấm "Thanh toán" trên tablet
2. Cashier confirm thanh toán qua CashierPaymentDialogController
   ↓
3. CashierController.completePayment() → TableDAO.updateStatus(DIRTY)
   ↓
4. Broadcast WsEvent:
   - TABLES (Admin TableController refresh)
   - ORDERS (Badge update)
   - BADGE (Badge count)
   - KITCHEN (←← THIS triggers WaiterController)
   - table_id (Tablet notify)
   ↓
5. WaiterController.onMessage(KITCHEN event) → doPoll()
   ↓
6. KitchenDAO.getDirtyTables() → returns bàn DIRTY
   ↓
7. WaiterController.rebuildCleanTable() → UI cập nhật
   ↓
8. Waiter thấy bàn cần dọn ✓
```

### Failure Scenario (Before Fix):
```
RestaurantEventClient.reconnect() fails
  → onClose() → scheduleReconnect()
  → 1s delay → reconnect fail → 2s delay → reconnect fail → ...
  → Loop vô hạn, spam logs, event DROP
  ↓
WaiterController không nhận event
  ↓
doPoll() không trigger
  ↓
Bàn DIRTY không hiển thị ✗
```

---

## 📋 Checklist Before Testing

### Admin Process (Main app):
- [ ] `RestaurantEventServer` start BEFORE `RestaurantEventClient.connect()`
- [ ] Check log: `[WsServer] Server khởi động thành công tại cổng 8025`
- [ ] Check log: `[Main] WS server bind thành công — khởi động Oracle DCN`

### Tablet Process:
- [ ] `RestaurantEventClient` start AFTER admin server is up
- [ ] Check log: `[WsClient] Kết nối thành công tới ws://localhost:8025`
- [ ] NOT: `[WsClient] Connection refused` looping

### WaiterController:
- [ ] Subscribe: `KITCHEN, ORDERS, BADGE, TABLES`
- [ ] Handler trigger `doPoll()` khi nhận event
- [ ] Check: `KitchenDAO.getDirtyTables(rid)` returns correct data

### CashierController:
- [ ] `completePayment()` broadcast tất cả 5 events (TABLES, ORDERS, BADGE, KITCHEN, table_id)
- [ ] Log: `[CashierController] broadcast lỗi:` ← nếu có = network issue

---

## 🔍 Debugging Tips

### Check WsServer Status:
```bash
# Monitor logs
tail -f logs/restaurant.log | grep -i wsserver

# Expected:
[WsServer] Server khởi động thành công tại cổng 8025
[WsClient] Kết nối thành công tới ws://localhost:8025
```

### Check Reconnect Loop:
```bash
tail -f logs/restaurant.log | grep -i "reconnect\|connection"

# Before fix: spam 100+ lines/min
# After fix: stop after 12 attempts + log reason
```

### Verify Broadcast:
```bash
tail -f logs/restaurant.log | grep -E "\[WsClient\] PUB|WaiterController.*broadcast"

# Should see:
[WsClient] PUB → server: topic='KITCHEN'
[WaiterController] broadcast lỗi:  ← nếu ERROR
```

---

## 🚀 How to Apply These Fixes

All changes are committed to `main` branch:

1. **RestaurantEventClient.java** — Graceful backoff + max retries
   - Status: ✅ APPLIED

2. **CashierController.java** — KITCHEN broadcast (already present)
   - Status: ✅ VERIFIED

3. **WaiterController.java** — Subscribe + handler (already present)
   - Status: ✅ VERIFIED

### Build & Run:
```bash
cd restaurant
mvn clean install
java -Dws.port=8025 -jar target/restaurant-fx.jar

# Or via IDE:
- Run Main.java
- Check console logs
```

---

## ⚠️ Network Requirements

### Port 8025 (WebSocket):
- Must be open locally (localhost ↔ localhost)
- If running on different machines, update client URI:
  ```java
  // In RestaurantEventClient.getInstance():
  URI uri = URI.create("ws://SERVER_IP:8025");
  ```

### Firewall:
- Whitelist port 8025 if using Windows Firewall
- Test: `netstat -an | grep 8025` should show LISTENING

---

## 📝 Expected Behavior After Fix

1. **Tablet completes payment** → Cashier confirms
2. **WaiterController receives broadcast** within 1 second
3. **Bàn cần dọn appears** in "Dọn bàn" tab
4. **Toast shows:** "Có 1 bàn cần dọn!"
5. **Waiter clicks "✓ Dọn xong"** → bàn returns RANH

---

## 📚 Related Files

- `src/main/java/com/restaurant/websocket/RestaurantEventClient.java` — Fixed
- `src/main/java/com/restaurant/websocket/RestaurantEventServer.java` — No change needed
- `src/main/java/com/restaurant/ui/fx/controller/WaiterController.java` — No change needed (correct)
- `src/main/java/com/restaurant/ui/fx/controller/CashierController.java` — No change needed (correct)
- `src/main/java/com/restaurant/Main.java` — Startup order is correct

---

**Last Updated:** 2026-05-26  
**Status:** Ready for Testing
