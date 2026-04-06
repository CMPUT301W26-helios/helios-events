package com.example.helios.ui.nav;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.helios.model.Event;
import com.example.helios.model.WaitingListStatus;
import com.example.helios.ui.common.HeliosText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public final class EventBrowseFilter {
    public enum VisibilityFilter {
        ALL,
        PUBLIC_ONLY,
        PRIVATE_ONLY,
        JOINED_ONLY
    }

    public enum AvailabilityFilter {
        ALL,
        OPEN_NOW,
        UPCOMING
    }

    public enum CapacityFilter {
        ALL,
        LIMITED_CAPACITY,
        WAITLIST_LIMITED
    }

    private final List<String> selectedInterests = new ArrayList<>();
    @Nullable
    private Long startDateFilter;
    @Nullable
    private Long endDateFilter;
    @NonNull
    private VisibilityFilter visibilityFilter = VisibilityFilter.ALL;
    @NonNull
    private AvailabilityFilter availabilityFilter = AvailabilityFilter.ALL;
    @NonNull
    private CapacityFilter capacityFilter = CapacityFilter.ALL;

    @Nullable
    public Long getStartDateFilter() {
        return startDateFilter;
    }

    @Nullable
    public Long getEndDateFilter() {
        return endDateFilter;
    }

    public void setStartDateFilter(@Nullable Long startDateFilter) {
        this.startDateFilter = startDateFilter;
    }

    public void setEndDateFilter(@Nullable Long endDateFilter) {
        this.endDateFilter = endDateFilter;
    }

    public void clearDateRange() {
        startDateFilter = null;
        endDateFilter = null;
    }

    @NonNull
    public VisibilityFilter getVisibilityFilter() {
        return visibilityFilter;
    }

    public void setVisibilityFilter(@NonNull VisibilityFilter visibilityFilter) {
        this.visibilityFilter = visibilityFilter;
    }

    @NonNull
    public AvailabilityFilter getAvailabilityFilter() {
        return availabilityFilter;
    }

    public void setAvailabilityFilter(@NonNull AvailabilityFilter availabilityFilter) {
        this.availabilityFilter = availabilityFilter;
    }

    @NonNull
    public CapacityFilter getCapacityFilter() {
        return capacityFilter;
    }

    public void setCapacityFilter(@NonNull CapacityFilter capacityFilter) {
        this.capacityFilter = capacityFilter;
    }

    @NonNull
    public List<String> getSelectedInterests() {
        return selectedInterests;
    }

    public void replaceSelectedInterests(@NonNull List<String> interests) {
        selectedInterests.clear();
        selectedInterests.addAll(interests);
    }

    public boolean isInterestSelected(@NonNull String interest) {
        for (String selected : selectedInterests) {
            if (selected != null && selected.equalsIgnoreCase(interest)) {
                return true;
            }
        }
        return false;
    }

    public void toggleInterestSelection(@NonNull String interest, boolean shouldSelect) {
        if (shouldSelect) {
            if (!isInterestSelected(interest)) {
                selectedInterests.add(interest);
            }
            return;
        }

        for (int i = selectedInterests.size() - 1; i >= 0; i--) {
            String selected = selectedInterests.get(i);
            if (selected != null && selected.equalsIgnoreCase(interest)) {
                selectedInterests.remove(i);
            }
        }
    }

    @NonNull
    public List<String> collectAvailableInterests(
            @NonNull List<Event> events,
            @Nullable String currentUid,
            @NonNull Map<String, WaitingListStatus> currentUserEntryStatuses
    ) {
        Map<String, String> uniqueByNormalized = new TreeMap<>();
        for (Event event : collectDisplayableEvents(events, currentUid, currentUserEntryStatuses)) {
            List<String> eventInterests = event.getInterests();
            if (eventInterests == null) {
                continue;
            }
            for (String rawInterest : eventInterests) {
                String trimmed = HeliosText.trimToNull(rawInterest);
                if (trimmed == null) {
                    continue;
                }
                uniqueByNormalized.putIfAbsent(trimmed.toLowerCase(Locale.getDefault()), trimmed);
            }
        }
        return new ArrayList<>(uniqueByNormalized.values());
    }

    @NonNull
    public List<Event> collectDisplayableEvents(
            @NonNull List<Event> events,
            @Nullable String currentUid,
            @NonNull Map<String, WaitingListStatus> currentUserEntryStatuses
    ) {
        List<Event> displayableEvents = new ArrayList<>();
        for (Event event : events) {
            if (event != null && shouldDisplayEvent(event, currentUid, currentUserEntryStatuses)) {
                displayableEvents.add(event);
            }
        }
        return displayableEvents;
    }

    @NonNull
    public Result apply(
            @NonNull List<Event> allEvents,
            @Nullable String currentUid,
            @NonNull Map<String, WaitingListStatus> currentUserEntryStatuses,
            @NonNull String rawQuery
    ) {
        List<Event> filteredEvents = new ArrayList<>();
        String query = rawQuery.toLowerCase(Locale.getDefault()).trim();

        for (Event event : allEvents) {
            if (!shouldDisplayEvent(event, currentUid, currentUserEntryStatuses)
                    || !matchesVisibilityFilter(event)
                    || !matchesAvailability(event)
                    || !matchesCapacity(event)) {
                continue;
            }
            if (visibilityFilter == VisibilityFilter.JOINED_ONLY
                    && (event.getEventId() == null
                    || !currentUserEntryStatuses.containsKey(event.getEventId()))) {
                continue;
            }
            if (matchesSearch(event, query) && matchesDate(event) && matchesSelectedInterests(event)) {
                filteredEvents.add(event);
            }
        }

        sortFilteredEvents(filteredEvents, currentUserEntryStatuses);

        int privateCount = 0;
        int publicCount = 0;
        for (Event event : filteredEvents) {
            if (event.isPrivateEvent()) {
                privateCount++;
            } else {
                publicCount++;
            }
        }

        return new Result(filteredEvents, publicCount, privateCount);
    }

    private boolean matchesVisibilityFilter(@NonNull Event event) {
        if (visibilityFilter == VisibilityFilter.PUBLIC_ONLY) {
            return !event.isPrivateEvent();
        }
        if (visibilityFilter == VisibilityFilter.PRIVATE_ONLY) {
            return event.isPrivateEvent();
        }
        return true;
    }

    public boolean isJoinedFilter() {
        return visibilityFilter == VisibilityFilter.JOINED_ONLY;
    }

    private boolean matchesAvailability(@NonNull Event event) {
        if (availabilityFilter == AvailabilityFilter.ALL) {
            return true;
        }

        long now = System.currentTimeMillis();
        long opens = event.getRegistrationOpensMillis();
        long closes = event.getRegistrationClosesMillis();

        if (availabilityFilter == AvailabilityFilter.OPEN_NOW) {
            return opens > 0L && closes > 0L && opens <= now && closes >= now;
        }
        return opens > now;
    }

    private boolean matchesCapacity(@NonNull Event event) {
        if (capacityFilter == CapacityFilter.ALL) {
            return true;
        }
        if (capacityFilter == CapacityFilter.LIMITED_CAPACITY) {
            return event.getCapacity() > 0;
        }
        Integer waitlistLimit = event.getWaitlistLimit();
        return waitlistLimit != null && waitlistLimit > 0;
    }

    private boolean matchesSearch(@NonNull Event event, @NonNull String query) {
        if (query.isEmpty()) {
            return true;
        }
        if (HeliosText.containsIgnoreCase(event.getTitle(), query)
                || HeliosText.containsIgnoreCase(event.getDescription(), query)
                || HeliosText.containsIgnoreCase(event.getLocationName(), query)
                || HeliosText.containsIgnoreCase(event.getAddress(), query)) {
            return true;
        }

        List<String> interests = event.getInterests();
        if (interests == null) {
            return false;
        }
        for (String tag : interests) {
            if (HeliosText.safeLower(tag).contains(query)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDate(@NonNull Event event) {
        long startTimeMillis = event.getStartTimeMillis();
        if (startDateFilter != null && startTimeMillis < startDateFilter) {
            return false;
        }
        return endDateFilter == null || startTimeMillis <= endDateFilter;
    }

    private boolean matchesSelectedInterests(@NonNull Event event) {
        if (selectedInterests.isEmpty()) {
            return true;
        }

        List<String> eventInterests = event.getInterests();
        if (eventInterests == null || eventInterests.isEmpty()) {
            return false;
        }

        for (String selected : selectedInterests) {
            String selectedLower = selected.trim().toLowerCase(Locale.getDefault());
            for (String tag : eventInterests) {
                if (tag != null && tag.trim().toLowerCase(Locale.getDefault()).equals(selectedLower)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldDisplayEvent(
            @NonNull Event event,
            @Nullable String currentUid,
            @NonNull Map<String, WaitingListStatus> currentUserEntryStatuses
    ) {
        if (!event.isPrivateEvent()) {
            return true;
        }
        if (currentUid == null) {
            return false;
        }
        if (event.isPendingCoOrganizer(currentUid)) {
            return true;
        }
        WaitingListStatus status = currentUserEntryStatuses.get(event.getEventId());
        return status == WaitingListStatus.INVITED || status == WaitingListStatus.ACCEPTED;
    }

    private void sortFilteredEvents(
            @NonNull List<Event> filteredEvents,
            @NonNull Map<String, WaitingListStatus> currentUserEntryStatuses
    ) {
        Collections.sort(filteredEvents, new Comparator<Event>() {
            @Override
            public int compare(Event left, Event right) {
                int inviteComparison = Boolean.compare(
                        currentUserEntryStatuses.get(left.getEventId()) != WaitingListStatus.INVITED,
                        currentUserEntryStatuses.get(right.getEventId()) != WaitingListStatus.INVITED
                );
                if (inviteComparison != 0) {
                    return inviteComparison;
                }

                long leftStart = left.getStartTimeMillis();
                long rightStart = right.getStartTimeMillis();
                if (leftStart <= 0 && rightStart <= 0) {
                    return compareTitles(left, right);
                }
                if (leftStart <= 0) {
                    return 1;
                }
                if (rightStart <= 0) {
                    return -1;
                }

                int startComparison = Long.compare(rightStart, leftStart);
                if (startComparison != 0) {
                    return startComparison;
                }
                return compareTitles(left, right);
            }
        });
    }

    private int compareTitles(@NonNull Event left, @NonNull Event right) {
        String leftTitle = HeliosText.nonEmptyOr(left.getTitle(), "");
        String rightTitle = HeliosText.nonEmptyOr(right.getTitle(), "");
        return leftTitle.compareToIgnoreCase(rightTitle);
    }

    public static final class Result {
        private final List<Event> filteredEvents;
        private final int publicCount;
        private final int privateCount;

        private Result(
                @NonNull List<Event> filteredEvents,
                int publicCount,
                int privateCount
        ) {
            this.filteredEvents = filteredEvents;
            this.publicCount = publicCount;
            this.privateCount = privateCount;
        }

        @NonNull
        public List<Event> getFilteredEvents() {
            return filteredEvents;
        }

        public int getPublicCount() {
            return publicCount;
        }

        public int getPrivateCount() {
            return privateCount;
        }
    }
}
