package com.travel.api.service;

import com.travel.api.model.Trip;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GroqService {

    private final RestTemplate restTemplate;

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    @Value("${groq.fallback.model}")
    private String fallbackModel;

    public GroqService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public String modifyItinerary(Trip trip, List<Integer> selectedDays) {
        String prompt = String.format(
                "Existing itinerary JSON:\n%s\n\n" +
                        "Trip context:\n" +
                        "- Start location: %s\n" +
                        "- Destination: %s\n" +
                        "- Duration: %s days\n" +
                        "- Budget level: %s\n" +
                        "- Companions: %s\n" +
                        "- Selected days to regenerate: %s\n\n" +
                        "STRICT TASK:\n" +
                        "1. Read the existing JSON and return the FULL updated JSON object in the exact same schema.\n" +
                        "2. Regenerate ONLY the itinerary entries whose 'day' value is in %s.\n" +
                        "3. For every selected day, make the plan MATERIALLY DIFFERENT from the old version.\n" +
                        "4. A material difference means changing at least two of these fields for each selected day: location, hotel, activities, food, transportation_options, transport.\n" +
                        "5. Keep every unselected day as close as possible to the original content.\n" +
                        "6. Keep the same total number of days, preserve day numbers, and keep valid route continuity.\n" +
                        "7. Do NOT remove the selected days. Replace their content with new options that still fit the trip route, category, and budget.\n" +
                        "8. If the current itinerary is a route trip, keep the route flowing forward instead of jumping backward.\n" +
                        "9. Return ONLY one valid JSON object. No markdown. No explanations.\n\n" +
                        "IMPORTANT:\n" +
                        "- Unselected days should remain nearly unchanged.\n" +
                        "- Selected days must be visibly different so the user can make a decision day by day.\n" +
                        "- Preserve top-level fields like route, distance_optimization, budget_split, route_plan, notes, and trip_type unless small adjustments are required for the changed days.",
                trip.getAiGeneratedPlan(),
                trip.getStartLocation(),
                trip.getDestination(),
                trip.getDuration(),
                trip.getBudgetLevel(),
                trip.getCompanions(),
                selectedDays.toString(),
                selectedDays.toString());

        System.out.println("DEBUG: Modifying itinerary for selected days: " + selectedDays);
        return callGroqApi(prompt);
    }

    public String analyzeWeatherRisks(String existingPlan, String weatherData) {
        String prompt = String.format(
                "You are a smart travel assistant.\n\n" +
                "Current Trip Plan:\n" +
                "%s\n\n" +
                "Weather Data:\n" +
                "%s\n\n" +
                "TASK:\n" +
                "1. Analyze if the weather affects the trip.\n" +
                "2. If yes: Suggest an alternative place or activity.\n" +
                "Wait for user confirmation (ask_user field).\n\n" +
                "Response format:\n" +
                "{\n" +
                "  \"issue\": \"weather not suitable\",\n" +
                "  \"suggestion\": \"visit indoor museum instead\",\n" +
                "  \"ask_user\": \"Day {day_number} has {weather_condition}. So change the Day {day_number} plan?\",\n" +
                "  \"risky_days\": [{\"day\": number, \"weather_condition\": \"string\", \"risk_level\": \"High|Medium\", \"indoor_alternative\": \"string\"}]\n" +
                "}",
                existingPlan, weatherData);

        System.out.println("DEBUG: Analyzing weather risks...");
        return callGroqApi(prompt);
    }

    public String applyWeatherAdjustments(Trip trip, String weatherRisksJson, List<Integer> daysToSwitch) {
        String prompt = String.format(
                "Existing Trip Plan: %s\n" +
                "Weather Risks & Alternatives: %s\n" +
                "Days to Switch to Indoor: %s\n\n" +
                "Task: Regenerate the trip plan. For the specified days to switch, replace the current plan with the AI-suggested indoor alternative.\n" +
                "Keep all other days and the overall route consistent. Ensure the JSON structure remains identical to the original.\n" +
                "Return ONLY the updated valid JSON object. Do not include any conversational text.",
                trip.getAiGeneratedPlan(),
                weatherRisksJson,
                daysToSwitch.toString());

        System.out.println("DEBUG: Applying weather adjustments for days: " + daysToSwitch);
        return callGroqApi(prompt);
    }

    public String generateItinerary(Trip trip, String weatherSummary) {
        String prompt = createPrompt(trip, weatherSummary);
        System.out.println("DEBUG: Generating initial weather-aware itinerary for: " + trip.getDestination());
        return callGroqApi(prompt);
    }

    public String calculateSettlement(String contributionsJson) {
        String prompt = String.format(
                "You are a financial assistant for group travel.\n\n" +
                "Group Members Contribution:\n" +
                "%s\n\n" +
                "Tasks:\n" +
                "1. Calculate total amount spent.\n" +
                "2. Calculate average per person.\n" +
                "3. Identify who paid extra and who paid less.\n" +
                "4. Split the balance amount.\n" +
                "5. Show who should pay whom.\n\n" +
                "Rules:\n" +
                "- Use the exact contribution values given in the input.\n" +
                "- 'paid extra' means the member paid above average.\n" +
                "- 'needs to pay' means the member paid below average.\n" +
                "- The settlement must clearly show the minimal set of payments needed to balance the group.\n" +
                "- Amounts should be numeric values, not currency symbols.\n\n" +
                "Output JSON format:\n" +
                "{\n" +
                "  \"total\": number,\n" +
                "  \"average\": number,\n" +
                "  \"balances\": [{\"person\": \"string\", \"status\": \"paid extra / needs to pay\", \"amount\": number}],\n" +
                "  \"settlement\": [\"P4 pays P2 56\", \"P4 pays P3 26\", \"P4 pays P1 6\", \"P4 pays P5 6\"]\n" +
                "}", contributionsJson);
        return callGroqApi(prompt);
    }

    public String checkGpsStatus(String currentCoords, String plannedRoute) {
        String prompt = String.format(
                "You are a travel tracking assistant.\n\n" +
                "Inputs:\n" +
                "- Current user location: %s\n" +
                "Planned route: %s\n\n" +
                "Tasks:\n" +
                "1. Track user progress along route.\n" +
                "2. Detect deviation from planned route.\n" +
                "3. Suggest correction or alternate route.\n\n" +
                "Rules:\n" +
                "- If the user is following the expected route progression, return status as 'on track'.\n" +
                "- If the user is significantly away from the planned route or moving in the wrong direction, return status as 'deviated'.\n" +
                "- The suggestion should be short, practical, and route-aware.\n\n" +
                "Output JSON:\n" +
                "{\n" +
                "  \"status\": \"on track / deviated\",\n" +
                "  \"suggestion\": \"string\"\n" +
                "}", currentCoords, plannedRoute);
        return callGroqApi(prompt);
    }

    private String callGroqApi(String prompt) {
        return callGroqApi(prompt, model, true);
    }

    private String callGroqApi(String prompt, String currentModel, boolean allowFallback) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", currentModel);

        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content",
                "You are an intelligent AI trip planning agent. Return ONLY valid JSON representing a detailed travel plan.\n\n" +
                        "### STRICT GEOGRAPHIC CONSTRAINTS:\n" +
                        "1. If 'trip_type' is 'destination_only':\n" +
                        "   - You MUST plan activities ONLY within the metropolitan boundary of the destination city.\n" +
                        "   - Do NOT suggest day trips to neighboring cities or places that require more than 30 mins travel from the city center.\n" +
                        "   - Example: If destination is 'Chennai', do NOT suggest 'Pondicherry'.\n" +
                        "2. If 'trip_type' is 'start_to_destination':\n" +
                        "   - You MUST plan a sequential journey from 'start_location' to 'destination'.\n" +
                        "   - Each day should represent a progression along the route, including major stopovers, transit times, and highway-side attractions.\n\n" +
                        "### DATA REQUIREMENTS:\n" +
                        "- The JSON MUST have the following structure:\n" +
                        "{\n" +
                        "  \"trip_type\": \"destination_only | start_to_destination\",\n" +
                        "  \"route\": [\"ordered list of major stops/transit points\"],\n" +
                        "  \"itinerary\": [\n" +
                        "    {\n" +
                        "      \"day\": number,\n" +
                        "      \"location\": \"string\",\n" +
                        "      \"hotel\": \"string\",\n" +
                        "      \"activities\": [\"specific, time-boxed local activities\"],\n" +
                        "      \"estimatedCost\": \"string\",\n" +
                        "      \"food\": [\"local restaurant/dish recommendations\"],\n" +
                        "      \"transportation_options\": [\"local transit suggestions\"],\n" +
                        "      \"transport\": \"primary mode of travel for the day\",\n" +
                        "      \"crowd_level\": \"Low | Medium | High\",\n" +
                        "      \"safetyTips\": \"string\"\n" +
                        "    }\n" +
                        "  ],\n" +
                        "  \"distance_optimization\": \"detailed explanation of why this route/sequence is chosen\",\n" +
                        "  \"budget_split\": {\"travel\": number, \"stay\": number, \"food\": number, \"activities\": number, \"emergency\": number},\n" +
                        "  \"route_plan\": [{\"from\": \"place A\", \"to\": \"place B\", \"gps_flow\": \"instruction\"}],\n" +
                        "  \"notes\": \"AI insight based on Weather, Climate, and Crowd level\"\n" +
                        "}\n" +
                        "### FIELD DEFINITIONS:\n" +
                        "- 'gps_flow': High-level place-to-place movement instructions.\n" +
                        "- 'budget_split': Percentages that MUST sum to exactly 100.\n\n" +
                        "Do not include any conversational text, only the JSON object.");
        messages.add(systemMessage);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.3);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(apiUrl, HttpMethod.POST, request,
                    new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("choices")) {
                List<?> choices = (List<?>) body.get("choices");
                if (choices != null && !choices.isEmpty()) {
                    Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
                    Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
                    String content = (String) message.get("content");

                    if (content != null && content.contains("```json")) {
                        content = content.substring(content.indexOf("```json") + 7);
                        if (content.contains("```")) {
                            content = content.substring(0, content.lastIndexOf("```"));
                        }
                    }
                    return content != null ? content.trim() : "No itinerary generated.";
                }
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            if (e.getRawStatusCode() == 429 && allowFallback && fallbackModel != null) {
                System.err.println("DEBUG: Primary model rate limited (429). Switching to fallback model: " + fallbackModel);
                return callGroqApi(prompt, fallbackModel, false); // Retry once with fallback
            }
            System.err.println("DEBUG: Groq API HTTP Error: " + e.getRawStatusCode() + " - " + e.getResponseBodyAsString());
            return "Failed to generate itinerary. API Error: " + e.getRawStatusCode();
        } catch (Throwable e) {
            System.err.println("DEBUG: Groq API Critical Error: " + e.getMessage());
            return "Failed to generate itinerary. Unexpected Error: " + e.getMessage();
        }
        return "No itinerary generated.";
    }

    private String createPrompt(Trip trip, String weatherSummary) {
        String tripType = trip.getTripType() != null ? trip.getTripType() : "destination_only";
        String category = trip.getCategory() != null ? trip.getCategory() : "General";
        String context;
        String constraints;

        if (tripType.equals("destination_only")) {
            context = String.format("A local %s-themed %d-day itinerary for %s. Forecast: %s.", category, trip.getDuration(), trip.getDestination(), weatherSummary);
            constraints = String.format(
                    "- ROLE: Local Expert. STRICTLY plan within the city limits of %s.\n" +
                    "- WEATHER ADAPTATION: If the forecast shows rain/storm, prioritize indoor places (Clubs, Malls, Galleries).\n" +
                    "- CATEGORY: Strictly focus on '%s' attractions and vibe.\n" +
                    "- NO EXIT: Do NOT suggest any places outside %s.", 
                    trip.getDestination(), category, trip.getDestination());
        } else {
            context = String.format("A %s-themed %d-day road trip from %s to %s. Route Forecast: %s.", category, trip.getDuration(), trip.getStartLocation(), trip.getDestination(), weatherSummary);
            constraints = String.format(
                    "- ROLE: Route Strategist. Sequence logically from %s to %s.\n" +
                    "- WEATHER ADAPTATION: If heavy rain is detected on the route, suggest safe roadside breaks or indoor alternatives.\n" +
                    "- CATEGORY: Focus on '%s' across the journey.\n" +
                    "- TACTICAL: Provide precise highways and point-to-point flow.",
                    trip.getStartLocation(), trip.getDestination(), category);
        }

        return String.format(
                "TASK: %s\n\n" +
                "CONSTRAINTS:\n%s\n\n" +
                "USER PREFERENCES:\n" +
                "- Budget: %s\n" +
                "- Group Size: %d heads\n" +
                "- Style: %s travel\n" +
                "- TRAVEL TYPE: %s\n" +
                "- Factor in seasonal weather and climate insights.\n" +
                "- Monitor crowd levels and suggest peak-time workarounds.\n" +
                "- Analyze distance and optimize for time efficiency using map-based logic.\n" +
                "- Provide a meticulously detailed day-by-day JSON itinerary with GPS-based travel flow.\n" +
                "- ALLOCATE BUDGET: Provide a percentage-based 'budget_split' for Travel, Stay, Food, Activities, and Emergency matching the %s budget precisely.\n" +
                "- Start Date: %s\n" +
                "- Start Time: %s\n" +
                "- IMPORTANT TIME LOGIC: Only apply late-arrival logic when a start time is provided. If the trip starts late in the day (for example after 6:00 PM, especially around 10:00 PM), Day 1 must be a light arrival plan only such as check-in, dinner, rest, or a short nearby activity. Do not create a full-day sightseeing schedule for late-night starts.\n" +
                "- **Category Preference: %s** (Focus on attractions of this type)\n\n" +
                "INSTRUCTIONS:\n" +
                "- Provide a meticulously detailed day-by-day JSON itinerary. Suggest hotels and food that match the %s budget precisely.\n" +
                "- Since the category is '%s', ensure that at least 70%% of the suggested activities are related to this category.",
                context,
                constraints,
                trip.getBudgetLevel(),
                trip.getCompanionCount() != null ? trip.getCompanionCount() + 1 : 1,
                trip.getCompanions(),
                trip.getCompanions().equalsIgnoreCase("Single") ? "Solo" : "Group",
                trip.getBudgetLevel(),
                trip.getStartDate(),
                trip.getStartTime() != null ? trip.getStartTime() : "Not specified",
                trip.getCategory() != null ? trip.getCategory() : "General",
                trip.getBudgetLevel(),
                trip.getCategory() != null ? trip.getCategory() : "General");
    }
}
