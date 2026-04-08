package com.travel.api.service;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiveLocationService {

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, Map<String, Object>>> tripLocations = new ConcurrentHashMap<>();

    public void updateLocation(Long tripId, String memberKey, String memberName, String userId, Double lat, Double lon) {
        ConcurrentHashMap<String, Map<String, Object>> tripMap =
                tripLocations.computeIfAbsent(tripId, ignored -> new ConcurrentHashMap<>());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberKey", memberKey);
        payload.put("name", memberName);
        payload.put("userId", userId);
        payload.put("lat", lat);
        payload.put("lon", lon);
        payload.put("updatedAt", OffsetDateTime.now().toString());

        tripMap.put(memberKey, payload);
    }

    public List<Map<String, Object>> getLocations(Long tripId) {
        ConcurrentHashMap<String, Map<String, Object>> tripMap = tripLocations.get(tripId);
        if (tripMap == null) {
            return List.of();
        }

        List<Map<String, Object>> locations = new ArrayList<>(tripMap.values());
        locations.sort(Comparator.comparing(location -> String.valueOf(location.getOrDefault("name", ""))));
        return locations;
    }

    public void removeLocation(Long tripId, String memberKey) {
        ConcurrentHashMap<String, Map<String, Object>> tripMap = tripLocations.get(tripId);
        if (tripMap == null) {
            return;
        }

        tripMap.remove(memberKey);
        if (tripMap.isEmpty()) {
            tripLocations.remove(tripId);
        }
    }
}
