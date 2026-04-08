package com.travel.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WeatherService {

    @Value("${openmeteo.url}")
    private String weatherUrl;

    private final RestTemplate restTemplate;
    private final GeocodingService geocodingService;
    private final ObjectMapper objectMapper;

    public WeatherService(RestTemplate restTemplate, GeocodingService geocodingService, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.geocodingService = geocodingService;
        this.objectMapper = objectMapper;
    }

    public String getWeather(String location, LocalDate startDate, LocalDate endDate) {
        return getWeather(location, null, null, startDate, endDate);
    }

    public String getWeather(String location, Double lat, Double lon, LocalDate startDate, LocalDate endDate) {
        try {
            return objectMapper.writeValueAsString(getWeatherData(location, lat, lon, startDate, endDate));
        } catch (Exception e) {
            e.printStackTrace();
            return "Weather info unavailable for " + location + ". Error: " + e.getMessage();
        }
    }

    public Map<String, Object> getWeatherData(String location, LocalDate startDate, LocalDate endDate) {
        return getWeatherData(location, null, null, startDate, endDate);
    }

    public Map<String, Object> getWeatherData(String location, Double lat, Double lon, LocalDate startDate, LocalDate endDate) {
        try {
            Map<String, Double> coords = (lat != null && lon != null)
                    ? Map.of("lat", lat, "lon", lon)
                    : geocodingService.getCoordinates(location);
            if (coords == null) {
                return Map.of("message", "Could not find coordinates for " + location);
            }

            LocalDate tripStart = startDate;
            LocalDate tripEnd = endDate != null ? endDate : startDate;
            LocalDate today = LocalDate.now();
            LocalDate maxForecastDate = today.plusDays(15);
            LocalDate effectiveForecastStart = tripStart.isBefore(today) ? today : tripStart;
            LocalDate effectiveForecastEnd = tripEnd.isAfter(maxForecastDate) ? maxForecastDate : tripEnd;

            Map<String, Map<String, Object>> forecastByDate = new HashMap<>();
            String timezone = "auto";

            if (effectiveForecastStart != null
                    && effectiveForecastEnd != null
                    && !effectiveForecastStart.isAfter(effectiveForecastEnd)) {
                String url = UriComponentsBuilder.fromHttpUrl("https://api.open-meteo.com/v1/forecast")
                        .queryParam("latitude", coords.get("lat"))
                        .queryParam("longitude", coords.get("lon"))
                        .queryParam("daily",
                                "temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max")
                        .queryParam("timezone", "auto")
                        .queryParam("start_date", effectiveForecastStart.toString())
                        .queryParam("end_date", effectiveForecastEnd.toString())
                        .toUriString();

                String rawResponse = restTemplate.getForObject(url, String.class);
                Map<String, Object> apiResponse = objectMapper.readValue(rawResponse, new TypeReference<>() {});
                Object rawTimezone = apiResponse.get("timezone");
                if (rawTimezone != null) {
                    timezone = String.valueOf(rawTimezone);
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> daily = (Map<String, Object>) apiResponse.get("daily");
                if (daily != null) {
                    List<String> times = toStringList(daily.get("time"));
                    List<Object> maxTemps = toObjectList(daily.get("temperature_2m_max"));
                    List<Object> minTemps = toObjectList(daily.get("temperature_2m_min"));
                    List<Object> rainProbabilities = toObjectList(daily.get("precipitation_probability_max"));
                    List<Object> maxWinds = toObjectList(daily.get("wind_speed_10m_max"));

                    for (int i = 0; i < times.size(); i++) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("temperature_2m_max", getListValue(maxTemps, i));
                        entry.put("temperature_2m_min", getListValue(minTemps, i));
                        entry.put("precipitation_probability_max", getListValue(rainProbabilities, i));
                        entry.put("wind_speed_10m_max", getListValue(maxWinds, i));
                        forecastByDate.put(times.get(i), entry);
                    }
                }
            }

            List<String> time = new ArrayList<>();
            List<Object> maxTemps = new ArrayList<>();
            List<Object> minTemps = new ArrayList<>();
            List<Object> rainProbabilities = new ArrayList<>();
            List<Object> maxWinds = new ArrayList<>();
            List<String> status = new ArrayList<>();
            List<String> statusMessages = new ArrayList<>();

            int forecastedDays = 0;
            int pendingDays = 0;
            int elapsedDays = 0;

            for (LocalDate cursor = tripStart; !cursor.isAfter(tripEnd); cursor = cursor.plusDays(1)) {
                String iso = cursor.toString();
                Map<String, Object> forecast = forecastByDate.get(iso);

                time.add(iso);
                if (forecast != null) {
                    maxTemps.add(forecast.get("temperature_2m_max"));
                    minTemps.add(forecast.get("temperature_2m_min"));
                    rainProbabilities.add(forecast.get("precipitation_probability_max"));
                    maxWinds.add(forecast.get("wind_speed_10m_max"));
                    status.add("forecast");
                    statusMessages.add("Realtime forecast available for this day.");
                    forecastedDays++;
                } else if (cursor.isBefore(today)) {
                    maxTemps.add(null);
                    minTemps.add(null);
                    rainProbabilities.add(null);
                    maxWinds.add(null);
                    status.add("elapsed");
                    statusMessages.add("This trip day has already passed. Realtime forecast is no longer available.");
                    elapsedDays++;
                } else {
                    maxTemps.add(null);
                    minTemps.add(null);
                    rainProbabilities.add(null);
                    maxWinds.add(null);
                    status.add("pending");
                    statusMessages.add("Forecast will appear automatically when this day enters the provider's realtime forecast window.");
                    pendingDays++;
                }
            }

            Map<String, Object> daily = new LinkedHashMap<>();
            daily.put("time", time);
            daily.put("temperature_2m_max", maxTemps);
            daily.put("temperature_2m_min", minTemps);
            daily.put("precipitation_probability_max", rainProbabilities);
            daily.put("wind_speed_10m_max", maxWinds);
            daily.put("status", status);
            daily.put("status_message", statusMessages);

            Map<String, Object> coverage = new LinkedHashMap<>();
            coverage.put("forecasted_days", forecastedDays);
            coverage.put("pending_days", pendingDays);
            coverage.put("elapsed_days", elapsedDays);
            coverage.put("provider_limit_days", 15);
            coverage.put("max_forecast_date", maxForecastDate.toString());
            coverage.put("live_updates", true);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("location", location);
            response.put("timezone", timezone);
            response.put("trip_start_date", tripStart != null ? tripStart.toString() : null);
            response.put("trip_end_date", tripEnd != null ? tripEnd.toString() : null);
            response.put("generated_at", java.time.OffsetDateTime.now().toString());
            response.put("message", pendingDays > 0
                    ? "Day-by-day weather is shown for the full trip. Available days use realtime forecast data, and later days will auto-update as they become forecastable."
                    : "Day-by-day realtime forecast data is available for the full trip.");
            response.put("coverage", coverage);
            response.put("daily", daily);
            return response;
        } catch (Exception e) {
            e.printStackTrace();
            return Map.of("message", "Weather info unavailable for " + location + ". Error: " + e.getMessage());
        }
    }

    // Better implementation with coordinates if we had them.
    public String getWeather(double lat, double lon) {
        String url = UriComponentsBuilder.fromHttpUrl(weatherUrl)
                .queryParam("latitude", lat)
                .queryParam("longitude", lon)
                .queryParam("daily", "temperature_2m_max,temperature_2m_min,precipitation_sum")
                .toUriString();
        return restTemplate.getForObject(url, String.class);
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private List<Object> toObjectList(Object value) {
        if (value instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        return List.of();
    }

    private Object getListValue(List<Object> list, int index) {
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }
}
