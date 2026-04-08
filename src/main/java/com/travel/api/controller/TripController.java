package com.travel.api.controller;

import com.travel.api.model.Trip;
import com.travel.api.model.User;
import com.travel.api.repository.UserRepository;
import com.travel.api.service.TripService;
import com.travel.api.service.WeatherService;
import com.travel.api.service.GeocodingService;
import com.travel.api.service.LiveLocationService;
import com.travel.api.service.OpenTripMapService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService tripService;
    private final UserRepository userRepository;
    private final WeatherService weatherService;
    private final GeocodingService geocodingService;
    private final LiveLocationService liveLocationService;
    private final OpenTripMapService openTripMapService;

    public TripController(TripService tripService, UserRepository userRepository, WeatherService weatherService,
            GeocodingService geocodingService, LiveLocationService liveLocationService,
            OpenTripMapService openTripMapService) {
        this.tripService = tripService;
        this.userRepository = userRepository;
        this.weatherService = weatherService;
        this.geocodingService = geocodingService;
        this.liveLocationService = liveLocationService;
        this.openTripMapService = openTripMapService;
    }

    @PostMapping
    public ResponseEntity<?> createTrip(
            @RequestBody Trip trip,
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        trip.setUser(user);
        try {
            return ResponseEntity.ok(tripService.createTrip(trip));
        } catch (IllegalArgumentException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping
    public ResponseEntity<List<Trip>> getUserTrips(
            @AuthenticationPrincipal UserDetails userDetails) {

        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));

        return ResponseEntity.ok(tripService.getTripsByUser(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Trip> getTrip(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getTripById(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTrip(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        System.out.println("Attempting to delete trip with ID: " + id);
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            tripService.deleteTrip(id, user);
            System.out.println("Successfully deleted trip with ID: " + id);
            return ResponseEntity.ok("Trip deleted successfully");
        } catch (Exception e) {
            System.err.println("Error deleting trip: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTrip(
            @PathVariable Long id,
            @RequestBody Trip updatedTrip,
            @AuthenticationPrincipal UserDetails userDetails) {
        if (id == null)
            return ResponseEntity.badRequest().body("Trip ID is required");
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Trip currentTrip = tripService.getTripById(id);
            if (currentTrip == null || currentTrip.getUser() == null || !currentTrip.getUser().getId().equals(user.getId())) {
                throw new RuntimeException("Only the trip leader can change this trip.");
            }
            Trip saved = tripService.updateTrip(id, updatedTrip);
            return ResponseEntity.ok(saved);
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/join/{groupId}")
    public ResponseEntity<?> joinTrip(
            @PathVariable String groupId,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            return ResponseEntity.ok(tripService.joinTrip(groupId, user));
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/leave")
    public ResponseEntity<?> leaveTrip(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        try {
            return ResponseEntity.ok(tripService.leaveTrip(id, user));
        } catch (RuntimeException e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/modify")
    public ResponseEntity<?> modifyTrip(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Object rawSelectedDays = request.get("selectedDays");
            List<Integer> selectedDays = new java.util.ArrayList<>();

            if (rawSelectedDays instanceof List<?> rawList) {
                for (Object item : rawList) {
                    if (item instanceof Number number) {
                        selectedDays.add(number.intValue());
                    } else if (item instanceof String text) {
                        try {
                            selectedDays.add(Integer.parseInt(text.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                }
            }

            return ResponseEntity.ok(tripService.modifyItinerary(id, selectedDays, user));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @GetMapping("/{id}/alerts")
    public ResponseEntity<List<String>> getTripAlerts(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.getTripAlerts(id));
    }

    @GetMapping("/{id}/weather")
    public ResponseEntity<?> getTripWeather(@PathVariable Long id) {
        Trip trip = tripService.getTripById(id);
        if (trip == null)
            return ResponseEntity.notFound().build();

        return ResponseEntity
                .ok(weatherService.getWeatherData(
                        trip.getDestination(),
                        trip.getDestinationLatitude(),
                        trip.getDestinationLongitude(),
                        trip.getStartDate(),
                        trip.getEndDate()));
    }

    @GetMapping("/{id}/coordinates")
    public ResponseEntity<Map<String, Map<String, Double>>> getTripCoordinates(
            @PathVariable Long id) {
        Trip trip = tripService.getTripById(id);
        if (trip == null)
            return ResponseEntity.notFound().build();

        Map<String, Double> startCoords = trip.getStartLatitude() != null && trip.getStartLongitude() != null
                ? Map.of("lat", trip.getStartLatitude(), "lon", trip.getStartLongitude())
                : geocodingService.getCoordinates(trip.getStartLocation());
        Map<String, Double> destCoords = trip.getDestinationLatitude() != null && trip.getDestinationLongitude() != null
                ? Map.of("lat", trip.getDestinationLatitude(), "lon", trip.getDestinationLongitude())
                : geocodingService.getCoordinates(trip.getDestination());

        Map<String, Map<String, Double>> result = new HashMap<>();
        result.put("start", startCoords);
        result.put("destination", destCoords);

        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/stops-coordinates")
    public ResponseEntity<Map<String, Map<String, Double>>> getStopsCoordinates(
            @PathVariable Long id,
            @RequestBody List<String> locations) {
        return ResponseEntity.ok(geocodingService.getCoordinatesForList(locations));
    }

    @PostMapping("/{id}/nearby-places")
    public ResponseEntity<?> getNearbyPlaces(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Trip trip = tripService.getTripById(id);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isTripParticipant(trip, user)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have access to this trip."));
        }

        Object rawLocations = request.get("locations");
        int radiusMeters = request.get("radiusMeters") instanceof Number number ? number.intValue() : 3000;
        int limit = request.get("limit") instanceof Number number ? number.intValue() : 6;

        if (!(rawLocations instanceof List<?> locations)) {
            return ResponseEntity.badRequest().body(Map.of("message", "locations array is required."));
        }

        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Object item : locations) {
            if (!(item instanceof Map<?, ?> location)) {
                continue;
            }

            String name = location.get("name") instanceof String value ? value : "Unknown";
            Double lat = location.get("lat") instanceof Number value ? value.doubleValue() : null;
            Double lon = location.get("lon") instanceof Number value ? value.doubleValue() : null;

            if (lat == null || lon == null) {
                continue;
            }

            result.put(name, openTripMapService.getNearbyPlaces(lat, lon, radiusMeters, limit));
        }

        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/weather-analysis")
    public ResponseEntity<String> getTripWeatherAnalysis(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(tripService.getWeatherAnalysis(id));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    @PostMapping("/{id}/apply-weather-fix")
    public ResponseEntity<?> applyWeatherFix(
            @PathVariable Long id,
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByEmail(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            String weatherRisksJson = (String) request.get("weatherRisksJson");
            @SuppressWarnings("unchecked")
            List<Integer> daysToSwitch = (List<Integer>) request.get("daysToSwitch");
            return ResponseEntity.ok(tripService.applyWeatherFix(id, weatherRisksJson, daysToSwitch, user));
        } catch (Exception e) {
            Map<String, String> error = new HashMap<>();
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping("/{id}/settle-ai")
    public ResponseEntity<String> calculateAiSettlement(@PathVariable Long id) {
        return ResponseEntity.ok(tripService.calculateAiSettlement(id));
    }

    @PostMapping("/{id}/gps-status")
    public ResponseEntity<String> checkGpsStatus(@PathVariable Long id, @RequestBody Map<String, Double> coords) {
        return ResponseEntity.ok(tripService.checkGpsStatus(id, coords.get("lat"), coords.get("lon")));
    }

    @GetMapping("/{id}/member-locations")
    public ResponseEntity<?> getMemberLocations(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Trip trip = tripService.getTripById(id);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isTripParticipant(trip, user)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have access to this trip."));
        }

        return ResponseEntity.ok(liveLocationService.getLocations(id));
    }

    @PostMapping("/{id}/member-locations")
    public ResponseEntity<?> updateMemberLocation(
            @PathVariable Long id,
            @RequestBody Map<String, Double> coords,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Trip trip = tripService.getTripById(id);
        if (trip == null) {
            return ResponseEntity.notFound().build();
        }
        if (!isTripParticipant(trip, user)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "You do not have access to this trip."));
        }

        Double lat = coords.get("lat");
        Double lon = coords.get("lon");
        if (lat == null || lon == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Latitude and longitude are required."));
        }

        String memberKey = user.getUserId() != null && !user.getUserId().isBlank() ? user.getUserId() : user.getEmail();
        liveLocationService.updateLocation(id, memberKey, user.getName(), user.getUserId(), lat, lon);
        return ResponseEntity.ok(Map.of("message", "Location updated."));
    }

    @DeleteMapping("/{id}/member-locations")
    public ResponseEntity<?> clearMemberLocation(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmail(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        String memberKey = user.getUserId() != null && !user.getUserId().isBlank() ? user.getUserId() : user.getEmail();
        liveLocationService.removeLocation(id, memberKey);
        return ResponseEntity.ok(Map.of("message", "Location removed."));
    }

    private boolean isTripParticipant(Trip trip, User user) {
        if (trip == null || user == null) {
            return false;
        }

        if (trip.getUser() != null && user.getId() != null && user.getId().equals(trip.getUser().getId())) {
            return true;
        }

        return trip.getMembers() != null && trip.getMembers().stream()
                .anyMatch(member -> member != null && member.getId() != null && member.getId().equals(user.getId()));
    }
}
