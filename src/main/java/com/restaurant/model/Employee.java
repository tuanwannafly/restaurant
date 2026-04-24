package com.restaurant.model;

public class Employee {
    public enum Role { PHUC_VU, DAU_BEP, THU_NGAN, QUAN_LY }

    private String id;
    private String name;
    private String cccd;
    private String phone;
    private String address;
    private String startDate;
    private Role role;
    /** true nếu nhân viên đã được liên kết với một tài khoản đăng nhập. */
    private boolean hasAccount;

    public Employee(String id, String name, String cccd, String phone, String address, String startDate, Role role) {
        this.id = id;
        this.name = name;
        this.cccd = cccd;
        this.phone = phone;
        this.address = address;
        this.startDate = startDate;
        this.role = role;
        this.hasAccount = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCccd() { return cccd; }
    public void setCccd(String cccd) { this.cccd = cccd; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public boolean isHasAccount() { return hasAccount; }
    public void setHasAccount(boolean hasAccount) { this.hasAccount = hasAccount; }

    public String getRoleDisplay() {
        switch (role) {
            case PHUC_VU: return "Phục vụ";
            case DAU_BEP: return "Đầu bếp";
            case THU_NGAN: return "Thu ngân";
            case QUAN_LY: return "Quản lý";
            default: return "";
        }
    }
}