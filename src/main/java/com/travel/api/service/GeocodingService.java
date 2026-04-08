package com.travel.api.service;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.List;

@Service
public class GeocodingService {

    private final RestTemplate restTemplate;

    public GeocodingService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Map<String, Double> getCoordinates(String location) {
        for (String candidate : buildCandidates(location)) {
            Map<String, Double> coords = requestCoordinates(candidate);
            if (coords != null) {
                return coords;
            }
        }
        return null;
    }

    private List<String> buildCandidates(String location) {
        if (location == null || location.trim().isEmpty()) {
            return List.of();
        }

        String trimmed = location.trim();
        String shortName = trimmed.contains(",") ? trimmed.substring(0, trimmed.indexOf(',')).trim() : trimmed;
        return shortName.equalsIgnoreCase(trimmed) ? List.of(trimmed) : List.of(trimmed, shortName);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> requestCoordinates(String location) {
        try {
            Map<String, Double> openMeteoCoords = requestOpenMeteoCoordinates(location);
            if (openMeteoCoords != null) {
                return openMeteoCoords;
            }

            Map<String, Double> nominatimCoords = requestNominatimCoordinates(location);
            if (nominatimCoords != null) {
                return nominatimCoords;
            }
        } catch (Exception e) {
            System.err.println("Error during geocoding for '" + location + "': " + e.getMessage());
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> requestOpenMeteoCoordinates(String location) {
        String url = "https://geocoding-api.open-meteo.com/v1/search?name={name}&count=1&language=en&format=json";
        Map<String, Object> response = restTemplate.getForObject(url, Map.class, location);
        if (response != null && response.containsKey("results")) {
            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("results");
            if (results != null && !results.isEmpty()) {
                Map<String, Object> firstResult = results.get(0);
                return Map.of(
                        "lat", ((Number) firstResult.get("latitude")).doubleValue(),
                        "lon", ((Number) firstResult.get("longitude")).doubleValue());
            }
        }
        return null;
    }

    private Map<String, Double> requestNominatimCoordinates(String location) {
        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://nominatim.openstreetmap.org/search")
                    .queryParam("q", location)
                    .queryParam("format", "jsonv2")
                    .queryParam("limit", 1)
                    .build(true)
                    .toUri();

            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.ACCEPT, "application/json");
            headers.set(HttpHeaders.USER_AGENT, "SmartTripPlanner/1.0");

            ResponseEntity<List<Map<String, Object>>> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});

            List<Map<String, Object>> results = response.getBody();
            if (results != null && !results.isEmpty()) {
                Map<String, Object> firstResult = results.get(0);
                Object lat = firstResult.get("lat");
                Object lon = firstResult.get("lon");
                if (lat != null && lon != null) {
                    return Map.of(
                            "lat", Double.parseDouble(String.valueOf(lat)),
                            "lon", Double.parseDouble(String.valueOf(lon)));
                }
            }
        } catch (Exception e) {
            System.err.println("Nominatim geocoding failed for '" + location + "': " + e.getMessage());
        }
        return null;
    }

    public Map<String, Map<String, Double>> getCoordinatesForList(List<String> locations) {
        Map<String, Map<String, Double>> results = new java.util.HashMap<>();
        for (String loc : locations) {
            if (loc != null && !loc.trim().isEmpty()) {
                Map<String, Double> coords = getCoordinates(loc);
                if (coords != null) {
                    results.put(loc, coords);
                }
            }
        }
        return results;
    }
}
