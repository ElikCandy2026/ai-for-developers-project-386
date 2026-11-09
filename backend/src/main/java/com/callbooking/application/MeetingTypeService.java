package com.callbooking.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.callbooking.domain.MeetingType;
import com.callbooking.domain.MeetingTypeSummary;
import com.callbooking.domain.SchedulingPolicy;
import com.callbooking.persistence.MeetingTypeRepository;

@Service
public class MeetingTypeService {

    private final MeetingTypeRepository repository;
    private final SchedulingPolicy policy;

    public MeetingTypeService(MeetingTypeRepository repository, SchedulingPolicy policy) {
        this.repository = repository;
        this.policy = policy;
    }

    public List<MeetingType> list() {
        return repository.findAll();
    }

    public MeetingType get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ApplicationException.of(
                        ApplicationErrorCode.MEETING_TYPE_NOT_FOUND,
                        "Тип встречи с идентификатором " + id + " не найден."));
    }

    public MeetingType create(String name, String description, Integer durationMinutes) {
        // Presence, lengths and the 15–240 range are enforced by Bean Validation
        // from the contract. The service only normalises and owns the semantic
        // rule: the duration must be a multiple of the slot alignment.
        String normalisedName = trim(name);
        String normalisedDescription = trim(description);

        int duration = durationMinutes == null ? 0 : durationMinutes;
        if (policy.durationAlignment(duration) != SchedulingPolicy.DurationAlignment.OK) {
            throw ApplicationException.of(
                    ApplicationErrorCode.INVALID_DURATION,
                    "Длительность должна быть кратна "
                            + SchedulingPolicy.DURATION_ALIGNMENT_MINUTES + " минутам.");
        }

        Instant createdAt = policy.now().truncatedTo(ChronoUnit.MILLIS);
        MeetingType meetingType = new MeetingType(
                UUID.randomUUID().toString(),
                normalisedName,
                normalisedDescription,
                duration,
                createdAt);

        repository.insert(meetingType);
        return meetingType;
    }

    public MeetingTypeSummary summary(MeetingType meetingType) {
        return new MeetingTypeSummary(
                meetingType.id(),
                meetingType.name(),
                meetingType.description(),
                meetingType.durationMinutes());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
