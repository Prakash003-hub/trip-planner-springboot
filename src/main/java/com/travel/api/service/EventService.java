package com.travel.api.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    // A simple mock database of major Indian festivals for demonstration.
    // In a real app, this would come from an external API or a database.
    private static final Map<String, List<Festival>> FESTIVALS = Map.of(
            "india", List.of(
                    new Festival("Diwali", LocalDate.of(2026, 11, 8), LocalDate.of(2026, 11, 12),
                            "High crowding and heavy traffic expected during Diwali."),
                    new Festival("Holi", LocalDate.of(2026, 3, 3), LocalDate.of(2026, 3, 4),
                            "Expect crowds and color celebrations in public areas."),
                    new Festival("Pongal", LocalDate.of(2026, 1, 14), LocalDate.of(2026, 1, 17),
                            "Major harvest festival; expect crowded transport and markets."),
                    new Festival("Independence Day", LocalDate.of(2026, 8, 15), LocalDate.of(2026, 8, 15),
                            "Public holiday; some areas might have restricted access or parades."),
                    new Festival("Christmas", LocalDate.of(2026, 12, 25), LocalDate.of(2026, 12, 25),
                            "Public holiday; popular tourist spots may be crowded.")));

    public List<String> getEventAlerts(String destination, LocalDate startDate, LocalDate endDate) {
        List<String> alerts = new ArrayList<>();

        // For now, we use a general "india" key if the destination (or part of it) is
        // in India.
        // This is a simplification.
        String context = "india";

        List<Festival> relevantFestivals = FESTIVALS.getOrDefault(context, new ArrayList<>());

        for (Festival festival : relevantFestivals) {
            if (isOverlapping(startDate, endDate, festival.start, festival.end)) {
                alerts.add("Festival Alert: " + festival.name + ". " + festival.description);
            }
        }

        return alerts;
    }

    private boolean isOverlapping(LocalDate start1, LocalDate end1, LocalDate start2, LocalDate end2) {
        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    private static class Festival {
        String name;
        LocalDate start;
        LocalDate end;
        String description;

        Festival(String name, LocalDate start, LocalDate end, String description) {
            this.name = name;
            this.start = start;
            this.end = end;
            this.description = description;
        }
    }
}
