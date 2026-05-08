package com.restaurant.websocket;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * DTO đại diện cho một sự kiện WebSocket trong hệ thống SmartRestaurant.
 *
 * <h3>Cấu trúc JSON wire-format</h3>
 * <pre>{@code
 * {
 *   "topic":        "orders",
 *   "restaurantId": 1,
 *   "payload":      null          // optional
 * }
 * }</pre>
 *
 * <h3>Sử dụng</h3>
 * <pre>{@code
 * // Tạo event (factory method)
 * WsEvent evt = WsEvent.of(WsTopic.ORDERS, session.getRestaurantId());
 *
 * // Serialise → JSON string
 * String json = evt.toJson();
 *
 * // Deserialise ← JSON string
 * WsEvent evt2 = WsEvent.fromJson(json);
 * }</pre>
 *
 * <p>Lớp này bất biến (immutable); mọi trường đều là {@code final}.
 *
 * @param topic        topic WebSocket (xem {@link WsTopic})
 * @param restaurantId ID nhà hàng phát sinh sự kiện
 * @param payload      dữ liệu tùy chọn đính kèm (có thể null)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class WsEvent {

    // ── Shared ObjectMapper — thread-safe sau khi cấu hình ───────────────────
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String topic;
    private final long   restaurantId;
    private final String payload;   // nullable

    // ── Constructor (Jackson deserialisation via @JsonCreator) ────────────────

    @JsonCreator
    public WsEvent(
            @JsonProperty("topic")        String topic,
            @JsonProperty("restaurantId") long   restaurantId,
            @JsonProperty("payload")      String payload) {
        this.topic        = topic;
        this.restaurantId = restaurantId;
        this.payload      = payload;
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * Tạo WsEvent không kèm payload.
     *
     * @param topic        topic WebSocket (dùng hằng số trong {@link WsTopic})
     * @param restaurantId ID nhà hàng phát sinh sự kiện
     * @return WsEvent mới, {@code payload == null}
     */
    public static WsEvent of(String topic, long restaurantId) {
        return new WsEvent(topic, restaurantId, null);
    }

    /**
     * Tạo WsEvent kèm payload tuỳ chọn.
     *
     * @param topic        topic WebSocket
     * @param restaurantId ID nhà hàng
     * @param payload      dữ liệu đính kèm (JSON string hoặc plain text)
     * @return WsEvent mới
     */
    public static WsEvent of(String topic, long restaurantId, String payload) {
        return new WsEvent(topic, restaurantId, payload);
    }

    // ── Serialisation helpers ─────────────────────────────────────────────────

    /**
     * Serialise WsEvent sang JSON string.
     *
     * @return chuỗi JSON, ví dụ {@code {"topic":"orders","restaurantId":1}}
     * @throws RuntimeException nếu Jackson gặp lỗi (không xảy ra với kiểu đơn giản này)
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("[WsEvent] Không thể serialise event: " + this, e);
        }
    }

    /**
     * Deserialise WsEvent từ JSON string.
     *
     * @param json chuỗi JSON cần parse
     * @return WsEvent được tái tạo
     * @throws RuntimeException nếu JSON không hợp lệ hoặc thiếu trường bắt buộc
     */
    public static WsEvent fromJson(String json) {
        try {
            return MAPPER.readValue(json, WsEvent.class);
        } catch (Exception e) {
            throw new RuntimeException("[WsEvent] Không thể deserialise JSON: " + json, e);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    @JsonProperty("topic")
    public String getTopic()        { return topic; }

    @JsonProperty("restaurantId")
    public long   getRestaurantId() { return restaurantId; }

    @JsonProperty("payload")
    public String getPayload()      { return payload; }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "WsEvent{topic='" + topic + "', restaurantId=" + restaurantId
                + (payload != null ? ", payload='" + payload + "'" : "") + '}';
    }
}