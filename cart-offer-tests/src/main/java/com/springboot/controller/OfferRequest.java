package com.springboot.controller;

public class OfferRequest {
    private int restaurantId;
    private String offerType;
    private int discount;
    private java.util.List<String> segments;

    public OfferRequest() {}

    public OfferRequest(int restaurantId, String offerType, int discount, java.util.List<String> segments) {
        this.restaurantId = restaurantId;
        this.offerType = offerType;
        this.discount = discount;
        this.segments = segments;
    }

    // Getters and setters

    public int getRestaurantId() {
        return restaurantId;
    }

    public void setRestaurantId(int restaurantId) {
        this.restaurantId = restaurantId;
    }

    public String getOfferType() {
        return offerType;
    }

    public void setOfferType(String offerType) {
        this.offerType = offerType;
    }

    public int getDiscount() {
        return discount;
    }

    public void setDiscount(int discount) {
        this.discount = discount;
    }

    public java.util.List<String> getSegments() {
        return segments;
    }

    public void setSegments(java.util.List<String> segments) {
        this.segments = segments;
    }
}
