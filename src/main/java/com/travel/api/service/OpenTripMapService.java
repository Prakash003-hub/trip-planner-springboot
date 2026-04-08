package com.travel.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenTripMapService {

    private final RestTemplate restTemplate;

    @Value("${opentripmap.api.key:}")
    private String apiKey;

    public OpenTripMapService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder
                .setConnectTimeout(Duration.ofSeconds(10))
                .setReadTimeout(Duration.ofSeconds(15))
                .build();
    }

    public List<Map<String, Object>> getNearbyPlaces(double lat, double lon, int radiusMeters, int limit) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }

        try {
            URI uri = UriComponentsBuilder
                    .fromHttpUrl("https://api.opentripmap.com/0.1/en/places/radius")
                    .queryParam("radius", Math.max(radiusMeters, 1000))
                    .queryParam("lon", lon)
                    .queryParam("lat", lat)
                    .queryParam("limit", Math.min(Math.max(limit, 1), 20))
                    .queryParam("rate", 2)
                    .queryParam("format", "json")
                    .queryParam("apikey", apiKey)
                    .build(true)
                    .toUri();

            RequestEntity<Void> request = new RequestEntity<>(buildHeaders(), HttpMethod.GET, uri);
            ResponseEntity<List> response = restTemplate.exchange(request, List.class);

            if (response.getBody() == null) {
                return List.of();
            }

            List<Map<String, Object>> normalized = new ArrayList<>();
            for (Object item : response.getBody()) {
                if (!(item instanceof Map<?, ?> rawPlace)) {
                    continue;
                }

                Object rawName = rawPlace.get("name");
                Object rawPoint = rawPlace.get("point");

                if (!(rawName instanceof String name) || name.isBlank() || !(rawPoint instanceof Map<?, ?> point)) {
                    continue;
                }

                Number pointLat = point.get("lat") instanceof Number number ? number : null;
                Number pointLon = point.get("lon") instanceof Number number ? number : null;
                if (pointLat == null || pointLon == null) {
                    continue;
                }

                Map<String, Object> place = new LinkedHashMap<>();
                place.put("name", name);
                place.put("lat", pointLat.doubleValue());
                place.put("lon", pointLon.doubleValue());
                place.put("kinds", rawPlace.containsKey("kinds") ? rawPlace.get("kinds") : "");
                place.put("dist", rawPlace.get("dist"));
                place.put("xid", rawPlace.containsKey("xid") ? rawPlace.get("xid") : "");
                place.put("wikidata", rawPlace.containsKey("wikidata") ? rawPlace.get("wikidata") : "");
                normalized.add(place);
            }

            return normalized;
        } catch (Exception error) {
            System.err.println("Error fetching OpenTripMap places: " + error.getMessage());
            return List.of();
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.ACCEPT, "application/json");
        headers.set(HttpHeaders.USER_AGENT, "SmartTripPlanner/1.0");
        return headers;
    }
}
