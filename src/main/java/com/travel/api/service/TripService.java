package com.travel.api.service;

import com.travel.api.model.Trip;
import com.travel.api.model.User;
import com.travel.api.repository.TripRepository;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.LocalDate;
import java.util.List;

@Service
public class TripService {

    private final TripRepository tripRepository;
    private final GroqService groqService;
    private final WeatherService weatherService;
    private final EventService eventService;
    private final GeocodingService geocodingService;

    public TripService(TripRepository tripRepository, GroqService groqService,
            WeatherService weatherService, EventService eventService, GeocodingService geocodingService) {
        this.tripRepository = tripRepository;
        this.groqService = groqService;
        this.weatherService = weatherService;
        this.eventService = eventService;
        this.geocodingService = geocodingService;
    }

    public Trip createTrip(Trip trip) {
        if (trip == null) throw new IllegalArgumentException("Trip cannot be null");
        
        // Validation: Start date must be between today and 2 months from now
        LocalDate today = LocalDate.now();
        LocalDate maxDate = today.plusMonths(2);

        if (trip.getStartDate() == null || trip.getStartDate().isBefore(today)
                || trip.getStartDate().isAfter(maxDate)) {
            throw new IllegalArgumentException("Start date must be between today and 2 months from now.");
        }

        validateTripDateAvailability(trip, null);

        // Generate Group ID only for group trips (not Solo)

        // Generate Group ID only for group trips (not Solo)
        if (!trip.getCompanions().equalsIgnoreCase("single")) {
            trip.setGroupId(generateGroupId());
        } else {
            trip.setGroupId(null);
        }

        // Set endDate based on startDate and duration
        if (trip.getStartDate() != null && trip.getDuration() != null) {
            trip.setEndDate(trip.getStartDate().plusDays(trip.getDuration()));
        }

        // Handle Trip Type and Start Location
        if ("destination_only".equalsIgnoreCase(trip.getTripType())) {
            trip.setStartLocation(null);
            trip.setStartLatitude(null);
            trip.setStartLongitude(null);
        }

        populateMissingCoordinates(trip);

        // 3. Fetch Weather for initial optimization
        String weatherData = "Unknown weather context";
        try {
            weatherData = weatherService.getWeather(
                    trip.getDestination(),
                    trip.getDestinationLatitude(),
                    trip.getDestinationLongitude(),
                    trip.getStartDate(),
                    trip.getEndDate());
        } catch (Exception e) {
            System.err.println("Weather fetch failed during creation: " + e.getMessage());
        }

        // 4. Generate initial AI Plan (Weather-Aware)
        String aiPlan = groqService.generateItinerary(trip, weatherData);
        trip.setAiGeneratedPlan(aiPlan);

        // 4. Autonomous Weather Guard (Proactive Fix)
        if (trip.isAutoWeatherGuard()) {
            try {
                String analysisJson = getWeatherAnalysis(trip); // Overloaded or helper
                // Simple check for risks in the JSON string
                if (analysisJson.contains("\"risk_level\": \"High\"") || analysisJson.contains("\"risk_level\": \"Medium\"")) {
                    // For safety, let's just make a private helper or improve the method
                    String updatedPlan = groqService.applyWeatherAdjustments(trip, analysisJson, null);
                    trip.setAiGeneratedPlan(updatedPlan);
                }
            } catch (Exception e) {
                System.err.println("Autonomous Weather Guard failed: " + e.getMessage());
            }
        }

        return tripRepository.save(trip);
    }

    // Helper for internal use to avoid ID check if not saved yet
    private String getWeatherAnalysis(Trip trip) {
        String weatherData = weatherService.getWeather(
                trip.getDestination(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                trip.getStartDate(),
                trip.getEndDate());
        return groqService.analyzeWeatherRisks(trip.getAiGeneratedPlan(), weatherData);
    }

    private String generateGroupId() {
        String digits = "0123456789";
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        java.util.Random random = new java.util.Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(digits.charAt(random.nextInt(digits.length())));
        }
        for (int i = 0; i < 4; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    public Trip updateTrip(@org.springframework.lang.NonNull Long id, Trip updatedData) {
        Trip existing = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        boolean needsReplan = !existing.getDestination().equalsIgnoreCase(updatedData.getDestination())
                || !java.util.Objects.equals(existing.getDuration(), updatedData.getDuration())
                || !existing.getBudgetLevel().equalsIgnoreCase(updatedData.getBudgetLevel())
                || !existing.getCompanions().equalsIgnoreCase(updatedData.getCompanions())
                || !java.util.Objects.equals(existing.getCategory(), updatedData.getCategory())
                || !java.util.Objects.equals(existing.getTripType(), updatedData.getTripType());

        existing.setTripName(updatedData.getTripName());
        existing.setCategory(updatedData.getCategory());
        existing.setTripType(updatedData.getTripType());
        existing.setStartLocation(updatedData.getStartLocation());
        existing.setStartLatitude(updatedData.getStartLatitude());
        existing.setStartLongitude(updatedData.getStartLongitude());
        existing.setDestination(updatedData.getDestination());
        existing.setDestinationLatitude(updatedData.getDestinationLatitude());
        existing.setDestinationLongitude(updatedData.getDestinationLongitude());
        existing.setBudgetLevel(updatedData.getBudgetLevel());
        existing.setDuration(updatedData.getDuration());
        existing.setStartDate(updatedData.getStartDate());
        existing.setStartTime(updatedData.getStartTime());
        existing.setCompanions(updatedData.getCompanions());
        existing.setCompanionCount(updatedData.getCompanionCount());

        validateTripDateAvailability(existing, existing.getId());

        // Update endDate
        if (existing.getStartDate() != null && existing.getDuration() != null) {
            existing.setEndDate(existing.getStartDate().plusDays(existing.getDuration()));
        }

        // Handle Group ID
        if (existing.getCompanions().equalsIgnoreCase("single")) {
            existing.setGroupId(null);
        } else if (existing.getGroupId() == null) {
            existing.setGroupId(generateGroupId());
        }

        if ("destination_only".equalsIgnoreCase(existing.getTripType())) {
            existing.setStartLocation(null);
            existing.setStartLatitude(null);
            existing.setStartLongitude(null);
        }

        populateMissingCoordinates(existing);

        // Regenerate AI Plan if destination/duration/level changed
        if (needsReplan) {
            String weatherData = "Normal weather context";
            try {
                weatherData = weatherService.getWeather(
                        existing.getDestination(),
                        existing.getDestinationLatitude(),
                        existing.getDestinationLongitude(),
                        existing.getStartDate(),
                        existing.getEndDate());
            } catch (Exception e) { /* ignore */ }
            String newPlan = groqService.generateItinerary(existing, weatherData);
            existing.setAiGeneratedPlan(newPlan);
        }

        return tripRepository.save(existing);
    }

    private Trip getTripForLeaderAction(Long tripId, User user) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        if (user == null || trip.getUser() == null || !trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Only the trip leader can change this trip.");
        }

        return trip;
    }

    private void validateTripDateAvailability(Trip trip, Long ignoreTripId) {
        if (trip.getUser() == null || trip.getStartDate() == null || trip.getDuration() == null || trip.getDuration() <= 0) {
            return;
        }

        LocalDate requestedStart = trip.getStartDate();
        LocalDate requestedEnd = requestedStart.plusDays(trip.getDuration() - 1L);

        List<Trip> existingTrips = tripRepository.findAllByUserOrMember(trip.getUser());
        for (Trip existingTrip : existingTrips) {
            if (ignoreTripId != null && ignoreTripId.equals(existingTrip.getId())) {
                continue;
            }
            if (existingTrip.getStartDate() == null || existingTrip.getDuration() == null || existingTrip.getDuration() <= 0) {
                continue;
            }

            LocalDate existingStart = existingTrip.getStartDate();
            LocalDate existingEnd = existingStart.plusDays(existingTrip.getDuration() - 1L);
            boolean overlaps = !requestedEnd.isBefore(existingStart) && !requestedStart.isAfter(existingEnd);

            if (overlaps) {
                throw new IllegalArgumentException(
                        String.format(
                                "Trip dates overlap with \"%s\" (%s to %s). Please choose a date after that trip ends.",
                                existingTrip.getTripName() != null && !existingTrip.getTripName().isBlank()
                                        ? existingTrip.getTripName()
                                        : existingTrip.getDestination(),
                                existingStart,
                                existingEnd));
            }
        }
    }

    public Trip joinTrip(String groupId, User user) {
        Trip trip = tripRepository.findByGroupId(groupId)
                .orElseThrow(() -> new RuntimeException("Trip not found with Group ID: " + groupId));

        trip.addMember(user);
        return tripRepository.save(trip);
    }

    public List<Trip> getTripsByUser(User user) {
        List<Trip> trips = tripRepository.findAllByUserOrMember(user);
        boolean changed = false;

        for (Trip trip : trips) {
            changed = populateMissingCoordinates(trip) || changed;
        }

        if (changed) {
            tripRepository.saveAll(trips);
        }

        return trips;
    }

    public Trip leaveTrip(Long id, User user) {
        if (id == null) {
            throw new IllegalArgumentException("ID cannot be null");
        }

        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        if (user == null) {
            throw new RuntimeException("User not found");
        }

        if (trip.getUser() != null && trip.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Trip leader cannot leave the trip. Delete the trip instead.");
        }

        boolean wasMember = trip.getMembers().stream()
                .anyMatch(member -> member != null && member.getId() != null && member.getId().equals(user.getId()));

        if (!wasMember) {
            throw new RuntimeException("You are not a member of this trip.");
        }

        trip.removeMember(user);
        return tripRepository.save(trip);
    }

    public Trip getTripById(Long id) {
        if (id == null)
            return null;
        Trip trip = tripRepository.findById(id).orElse(null);
        if (trip == null) {
            return null;
        }

        if (populateMissingCoordinates(trip)) {
            trip = tripRepository.save(trip);
        }

        return trip;
    }

    public Trip modifyItinerary(Long id, List<Integer> selectedDays, User user) {
        if (id == null) throw new IllegalArgumentException("ID cannot be null");
        if (selectedDays == null || selectedDays.isEmpty()) {
            throw new IllegalArgumentException("Please select at least one day to change.");
        }

        Trip trip = getTripForLeaderAction(id, user);

        String updatedPlan = groqService.modifyItinerary(trip, selectedDays);
        if (updatedPlan == null
                || updatedPlan.isBlank()
                || updatedPlan.startsWith("Failed to generate itinerary")
                || updatedPlan.equals("No itinerary generated.")) {
            throw new RuntimeException("AI could not regenerate the selected days right now. Please try again.");
        }
        trip.setAiGeneratedPlan(updatedPlan);

        return tripRepository.save(trip);
    }

    public String getWeatherAnalysis(Long id) {
        if (id == null) throw new IllegalArgumentException("ID cannot be null");
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
        
        String weatherData = weatherService.getWeather(
                trip.getDestination(),
                trip.getDestinationLatitude(),
                trip.getDestinationLongitude(),
                trip.getStartDate(),
                trip.getEndDate());
        return groqService.analyzeWeatherRisks(trip.getAiGeneratedPlan(), weatherData);
    }

    public Trip applyWeatherFix(Long id, String weatherRisksJson, List<Integer> daysToSwitch, User user) {
        Trip trip = getTripForLeaderAction(id, user);
        
        String updatedPlan = groqService.applyWeatherAdjustments(trip, weatherRisksJson, daysToSwitch);
        trip.setAiGeneratedPlan(updatedPlan);
        return tripRepository.save(trip);
    }

    public List<String> getTripAlerts(Long id) {
        Trip trip = tripRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        List<String> alerts = new ArrayList<>();

        // 1. Weather Alerts
        try {
            String weatherJson = weatherService.getWeather(
                    trip.getDestination(),
                    trip.getDestinationLatitude(),
                    trip.getDestinationLongitude(),
                    trip.getStartDate(),
                    trip.getEndDate());
            // Basic check for adverse weather in the JSON response (simplified)
            if (weatherJson.contains("\"precipitation_probability_max\":[") && weatherJson.contains("80")) {
                alerts.add("Weather Alert: High probability of heavy rain during your trip.");
            }
            if (weatherJson.contains("\"temperature_2m_max\":[") && weatherJson.contains("40")) {
                alerts.add("Weather Alert: Extreme temperatures (above 40°C) expected.");
            }
        } catch (Exception e) {
            System.err.println("Error fetching weather for alerts: " + e.getMessage());
        }

        // 2. Festival/Event Alerts
        alerts.addAll(eventService.getEventAlerts(trip.getDestination(), trip.getStartDate(), trip.getEndDate()));

        return alerts;
    }

    public void deleteTrip(Long id, User user) {
        if (id == null) throw new IllegalArgumentException("ID cannot be null");
        Trip trip = getTripForLeaderAction(id, user);
        tripRepository.deleteById(trip.getId());
    }

    public String calculateAiSettlement(Long tripId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));

        try {
            java.util.LinkedHashMap<String, Integer> memberSpending = new java.util.LinkedHashMap<>();

            if (trip.getUser() != null && trip.getUser().getName() != null) {
                memberSpending.put(trip.getUser().getName(), 0);
            }
            if (trip.getMembers() != null) {
                trip.getMembers().forEach(member -> {
                    if (member != null && member.getName() != null) {
                        memberSpending.putIfAbsent(member.getName(), 0);
                    }
                });
            }

            trip.getExpenses().forEach(expense -> {
                String person = expense.getMemberName() != null && !expense.getMemberName().isBlank()
                        ? expense.getMemberName()
                        : "Unknown";
                int amount = (int) Math.round(expense.getAmount() != null ? expense.getAmount() : 0.0);
                memberSpending.put(person, memberSpending.getOrDefault(person, 0) + amount);
            });

            if (memberSpending.isEmpty()) {
                throw new RuntimeException("No group members found for settlement.");
            }

            int total = memberSpending.values().stream().mapToInt(Integer::intValue).sum();
            int average = Math.round((float) total / memberSpending.size());

            java.util.List<java.util.Map<String, Object>> balances = new java.util.ArrayList<>();
            java.util.List<java.util.Map<String, Object>> creditors = new java.util.ArrayList<>();
            java.util.List<java.util.Map<String, Object>> debtors = new java.util.ArrayList<>();

            memberSpending.forEach((person, paid) -> {
                int balance = paid - average;
                java.util.Map<String, Object> balanceEntry = new java.util.LinkedHashMap<>();
                balanceEntry.put("person", person);
                balanceEntry.put("status", balance >= 0 ? "paid extra" : "needs to pay");
                balanceEntry.put("amount", Math.abs(balance));
                balances.add(balanceEntry);

                if (balance > 0) {
                    java.util.Map<String, Object> creditor = new java.util.LinkedHashMap<>();
                    creditor.put("person", person);
                    creditor.put("amount", balance);
                    creditors.add(creditor);
                } else if (balance < 0) {
                    java.util.Map<String, Object> debtor = new java.util.LinkedHashMap<>();
                    debtor.put("person", person);
                    debtor.put("amount", Math.abs(balance));
                    debtors.add(debtor);
                }
            });

            creditors.sort((a, b) -> Integer.compare((int) b.get("amount"), (int) a.get("amount")));
            debtors.sort((a, b) -> Integer.compare((int) b.get("amount"), (int) a.get("amount")));

            java.util.List<String> settlement = new java.util.ArrayList<>();
            int creditorIndex = 0;
            int debtorIndex = 0;

            while (creditorIndex < creditors.size() && debtorIndex < debtors.size()) {
                java.util.Map<String, Object> creditor = creditors.get(creditorIndex);
                java.util.Map<String, Object> debtor = debtors.get(debtorIndex);

                int creditorAmount = (int) creditor.get("amount");
                int debtorAmount = (int) debtor.get("amount");
                int transfer = Math.min(creditorAmount, debtorAmount);

                settlement.add(String.format("%s pays %s %d", debtor.get("person"), creditor.get("person"), transfer));

                creditor.put("amount", creditorAmount - transfer);
                debtor.put("amount", debtorAmount - transfer);

                if ((int) creditor.get("amount") == 0) creditorIndex++;
                if ((int) debtor.get("amount") == 0) debtorIndex++;
            }

            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("total", total);
            result.put("average", average);
            result.put("balances", balances);
            result.put("settlement", settlement);

            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result);
        } catch (Exception e) {
            return "{\"error\": \"Settlement analysis failed: " + e.getMessage() + "\"}";
        }
    }

    public String checkGpsStatus(Long tripId, Double lat, Double lon) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new RuntimeException("Trip not found"));
        
        String currentCoords = String.format("lat: %f, lon: %f", lat, lon);
        return groqService.checkGpsStatus(currentCoords, trip.getAiGeneratedPlan());
    }

    private boolean populateMissingCoordinates(Trip trip) {
        if (trip == null) {
            return false;
        }

        boolean changed = false;

        if (trip.getDestination() != null && (trip.getDestinationLatitude() == null || trip.getDestinationLongitude() == null)) {
            java.util.Map<String, Double> coords = geocodingService.getCoordinates(trip.getDestination());
            if (coords != null) {
                trip.setDestinationLatitude(coords.get("lat"));
                trip.setDestinationLongitude(coords.get("lon"));
                changed = true;
            }
        }

        if (trip.getStartLocation() != null && (trip.getStartLatitude() == null || trip.getStartLongitude() == null)) {
            java.util.Map<String, Double> coords = geocodingService.getCoordinates(trip.getStartLocation());
            if (coords != null) {
                trip.setStartLatitude(coords.get("lat"));
                trip.setStartLongitude(coords.get("lon"));
                changed = true;
            }
        }

        return changed;
    }
}
