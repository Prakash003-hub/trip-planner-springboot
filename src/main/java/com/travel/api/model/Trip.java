package com.travel.api.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Entity
@Table(name = "trips")
public class Trip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String tripName;
    private String groupId;
    private String startLocation;
    private Double startLatitude;
    private Double startLongitude;
    private String destination;
    private Double destinationLatitude;
    private Double destinationLongitude;
    private String budgetLevel;
    private Integer duration;
    private LocalDate startDate;
    private LocalTime startTime;
    private LocalDate endDate;
    private String companions;
    private Integer companionCount;
    private String tripType; // "destination_only" or "start_to_destination"
    private String category; // "natural", "historical", "entertainment", "food", "adventure", "spiritual"
    private boolean autoWeatherGuard; // Auto-apply weather fixes if true

    @Column(columnDefinition = "TEXT")
    private String aiGeneratedPlan;

    @ManyToMany
    @JoinTable(name = "trip_members", joinColumns = @JoinColumn(name = "trip_id"), inverseJoinColumns = @JoinColumn(name = "user_id"))
    private java.util.Set<User> members = new java.util.HashSet<>();

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    private List<Expense> expenses;

    public Trip() {
    }

    public String getTripName() {
        return tripName;
    }

    public void setTripName(String tripName) {
        this.tripName = tripName;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getStartLocation() {
        return startLocation;
    }

    public void setStartLocation(String startLocation) {
        this.startLocation = startLocation;
    }

    public Double getStartLatitude() {
        return startLatitude;
    }

    public void setStartLatitude(Double startLatitude) {
        this.startLatitude = startLatitude;
    }

    public Double getStartLongitude() {
        return startLongitude;
    }

    public void setStartLongitude(Double startLongitude) {
        this.startLongitude = startLongitude;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public Double getDestinationLatitude() {
        return destinationLatitude;
    }

    public void setDestinationLatitude(Double destinationLatitude) {
        this.destinationLatitude = destinationLatitude;
    }

    public Double getDestinationLongitude() {
        return destinationLongitude;
    }

    public void setDestinationLongitude(Double destinationLongitude) {
        this.destinationLongitude = destinationLongitude;
    }

    public String getBudgetLevel() {
        return budgetLevel;
    }

    public void setBudgetLevel(String budgetLevel) {
        this.budgetLevel = budgetLevel;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public String getCompanions() {
        return companions;
    }

    public void setCompanions(String companions) {
        this.companions = companions;
    }

    public Integer getCompanionCount() {
        return companionCount;
    }

    public void setCompanionCount(Integer companionCount) {
        this.companionCount = companionCount;
    }

    public String getTripType() {
        return tripType;
    }

    public void setTripType(String tripType) {
        this.tripType = tripType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getAiGeneratedPlan() {
        return aiGeneratedPlan;
    }

    public void setAiGeneratedPlan(String aiGeneratedPlan) {
        this.aiGeneratedPlan = aiGeneratedPlan;
    }

    public java.util.Set<User> getMembers() {
        return members;
    }

    public void setMembers(java.util.Set<User> members) {
        this.members = members;
    }

    public void addMember(User user) {
        this.members.add(user);
    }

    public void removeMember(User user) {
        this.members.remove(user);
    }

    public List<Expense> getExpenses() {
        return expenses;
    }

    public void setExpenses(List<Expense> expenses) {
        this.expenses = expenses;
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("spendingByMember")
    public java.util.Map<String, Double> getSpendingByMember() {
        if (expenses == null || expenses.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        java.util.Map<String, Double> breakdown = new java.util.HashMap<>();
        for (Expense e : expenses) {
            String member = e.getMemberName() != null ? e.getMemberName() : "Guest";
            breakdown.put(member, breakdown.getOrDefault(member, 0.0) + (e.getAmount() != null ? e.getAmount() : 0.0));
        }
        return breakdown;
    }

    @Transient
    @com.fasterxml.jackson.annotation.JsonProperty("totalSpent")
    public Double getTotalSpent() {
        if (expenses == null || expenses.isEmpty()) {
            return 0.0;
        }
        return expenses.stream()
                .mapToDouble(e -> e.getAmount() != null ? e.getAmount() : 0.0)
                .sum();
    }

    public boolean isAutoWeatherGuard() {
        return autoWeatherGuard;
    }

    public void setAutoWeatherGuard(boolean autoWeatherGuard) {
        this.autoWeatherGuard = autoWeatherGuard;
    }
}
