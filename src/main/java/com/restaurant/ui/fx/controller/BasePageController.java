package com.restaurant.ui.fx.controller;

import com.restaurant.ui.TableOrderStage;

/**
 * BasePageController — Phase 15.
 *
 * <p>Abstract base class cho tất cả page controllers trong TableOrderStage.
 * Mỗi controller nhận reference đến Stage (coordinator) thông qua
 * {@link #setStage(TableOrderStage)} và override {@link #onNavigatedTo()}
 * để thực hiện logic khi page được hiển thị.
 */
public abstract class BasePageController {

    /** Reference đến Stage cha — set ngay sau FXMLLoader.load(). */
    protected TableOrderStage stage;

    /**
     * Gọi bởi {@link TableOrderStage} ngay sau FXMLLoader.load().
     * Controllers dùng giá trị này để gọi shared state / navigate.
     */
    public void setStage(TableOrderStage stage) {
        this.stage = stage;
    }

    /**
     * Gọi mỗi khi Stage điều hướng đến page này.
     * Override để refresh data, bắt đầu animation, sync total, v.v.
     */
    public abstract void onNavigatedTo();

    // ── Convenience helpers ────────────────────────────────────────────────────

    /** Format giá tiền (delegate sang Stage). */
    protected static String fmt(double v) {
        return TableOrderStage.formatPrice(v);
    }
}
