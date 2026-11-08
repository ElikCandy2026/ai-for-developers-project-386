package com.callbooking.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.callbooking.api.ApiTime;
import com.callbooking.api.model.CreateMeetingTypeRequest;
import com.callbooking.api.model.MeetingType;
import com.callbooking.api.model.MeetingTypeSummary;
import com.callbooking.application.ApplicationErrorCode;
import com.callbooking.application.ApplicationException;
import com.callbooking.persistence.MeetingTypeRepository;

@Service
public class MeetingTypeService {

    private static final int DURATION_ALIGNMENT_MINUTES = 15;

    private final MeetingTypeRepository repository;

    public MeetingTypeService(MeetingTypeRepository repository) {
        this.repository = repository;
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

    public MeetingType create(CreateMeetingTypeRequest request) {
        // Presence, lengths and the 15–240 range are enforced by Bean Validation
        // from the contract. The service only normalises and owns the semantic
        // rule: the duration must be a multiple of the slot alignment.
        String name = trim(request.getName());
        String description = trim(request.getDescription());

        int duration = request.getDurationMinutes() == null ? 0 : request.getDurationMinutes();
        if (duration % DURATION_ALIGNMENT_MINUTES != 0) {
            throw ApplicationException.of(
                    ApplicationErrorCode.INVALID_DURATION,
                    "Длительность должна быть кратна " + DURATION_ALIGNMENT_MINUTES + " минутам.");
        }

        Instant createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        MeetingType meetingType = new MeetingType()
                .id(UUID.randomUUID().toString())
                .name(name)
                .description(description)
                .durationMinutes(duration)
                .createdAt(ApiTime.utc(createdAt));

        repository.insert(meetingType);
        return meetingType;
    }

    public MeetingTypeSummary summary(MeetingType meetingType) {
        return new MeetingTypeSummary()
                .id(meetingType.getId())
                .name(meetingType.getName())
                .description(meetingType.getDescription())
                .durationMinutes(meetingType.getDurationMinutes());
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
