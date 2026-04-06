package com.example.helios.ui.event;

import androidx.annotation.NonNull;

import com.example.helios.model.Event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EventDiscoveryInsights {
    private static final int DEFAULT_TAG_LIMIT = 8;

    private EventDiscoveryInsights() {}

    @NonNull
    public static Result fromEvents(@NonNull List<Event> events, long nowMillis) {
        Map<String, TagAggregate> tagsByKey = new HashMap<>();
        int activePublicCount = 0;
        int activePrivateCount = 0;
        int activeEventCount = 0;

        for (Event event : events) {
            if (event == null || !isActiveEvent(event, nowMillis)) {
                continue;
            }

            activeEventCount++;
            if (event.isPrivateEvent()) {
                activePrivateCount++;
            } else {
                activePublicCount++;
            }

            long freshnessScore = getFreshnessScore(event);
            Map<String, String> uniqueTagsForEvent = new LinkedHashMap<>();
            for (String tag : EventUiFormatter.getDisplayTags(event)) {
                uniqueTagsForEvent.putIfAbsent(tag.toLowerCase(Locale.getDefault()), tag);
            }

            for (Map.Entry<String, String> entry : uniqueTagsForEvent.entrySet()) {
                TagAggregate aggregate = tagsByKey.get(entry.getKey());
                if (aggregate == null) {
                    aggregate = new TagAggregate(entry.getValue());
                    tagsByKey.put(entry.getKey(), aggregate);
                }
                aggregate.count++;
                aggregate.latestActivityMillis = Math.max(aggregate.latestActivityMillis, freshnessScore);
            }
        }

        List<TagAggregate> sortedHotTags = new ArrayList<>(tagsByKey.values());
        Collections.sort(sortedHotTags, HOT_TAG_COMPARATOR);

        List<TagAggregate> sortedNewTags = new ArrayList<>(tagsByKey.values());
        Collections.sort(sortedNewTags, NEW_TAG_COMPARATOR);

        return new Result(
                toDisplayList(sortedHotTags, DEFAULT_TAG_LIMIT),
                toDisplayList(sortedNewTags, DEFAULT_TAG_LIMIT),
                activeEventCount,
                activePublicCount,
                activePrivateCount
        );
    }

    private static boolean isActiveEvent(@NonNull Event event, long nowMillis) {
        long registrationClosesMillis = event.getRegistrationClosesMillis();
        if (registrationClosesMillis > 0) {
            return registrationClosesMillis >= nowMillis;
        }
        long endTimeMillis = event.getEndTimeMillis();
        if (endTimeMillis > 0) {
            return endTimeMillis >= nowMillis;
        }
        long startTimeMillis = event.getStartTimeMillis();
        return startTimeMillis <= 0 || startTimeMillis >= nowMillis;
    }

    private static long getFreshnessScore(@NonNull Event event) {
        long freshnessScore = Math.max(event.getRegistrationOpensMillis(), event.getStartTimeMillis());
        freshnessScore = Math.max(freshnessScore, event.getEndTimeMillis());
        if (freshnessScore <= 0L) {
            return Long.MIN_VALUE;
        }
        return freshnessScore;
    }

    @NonNull
    private static List<String> toDisplayList(@NonNull List<TagAggregate> tags, int maxItems) {
        List<String> values = new ArrayList<>();
        int limit = Math.min(maxItems, tags.size());
        for (int i = 0; i < limit; i++) {
            values.add(tags.get(i).displayName);
        }
        return values;
    }

    private static final Comparator<TagAggregate> HOT_TAG_COMPARATOR = (left, right) -> {
        int countComparison = Integer.compare(right.count, left.count);
        if (countComparison != 0) {
            return countComparison;
        }
        int freshnessComparison = Long.compare(right.latestActivityMillis, left.latestActivityMillis);
        if (freshnessComparison != 0) {
            return freshnessComparison;
        }
        return left.displayName.compareToIgnoreCase(right.displayName);
    };

    private static final Comparator<TagAggregate> NEW_TAG_COMPARATOR = (left, right) -> {
        int freshnessComparison = Long.compare(right.latestActivityMillis, left.latestActivityMillis);
        if (freshnessComparison != 0) {
            return freshnessComparison;
        }
        int countComparison = Integer.compare(right.count, left.count);
        if (countComparison != 0) {
            return countComparison;
        }
        return left.displayName.compareToIgnoreCase(right.displayName);
    };

    private static final class TagAggregate {
        @NonNull
        private final String displayName;
        private int count = 0;
        private long latestActivityMillis = Long.MIN_VALUE;

        private TagAggregate(@NonNull String displayName) {
            this.displayName = displayName;
        }
    }

    public static final class Result {
        @NonNull
        private final List<String> hotTags;
        @NonNull
        private final List<String> newTags;
        private final int activeEventCount;
        private final int activePublicCount;
        private final int activePrivateCount;

        private Result(
                @NonNull List<String> hotTags,
                @NonNull List<String> newTags,
                int activeEventCount,
                int activePublicCount,
                int activePrivateCount
        ) {
            this.hotTags = hotTags;
            this.newTags = newTags;
            this.activeEventCount = activeEventCount;
            this.activePublicCount = activePublicCount;
            this.activePrivateCount = activePrivateCount;
        }

        @NonNull
        public List<String> getHotTags() {
            return hotTags;
        }

        @NonNull
        public List<String> getNewTags() {
            return newTags;
        }

        public int getActiveEventCount() {
            return activeEventCount;
        }

        public int getActivePublicCount() {
            return activePublicCount;
        }

        public int getActivePrivateCount() {
            return activePrivateCount;
        }
    }
}
