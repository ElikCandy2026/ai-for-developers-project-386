package com.callbooking.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.callbooking.api.ApiException;
import com.callbooking.api.ApiTime;
import com.callbooking.api.model.CreateMeetingTypeRequest;
import com.callbooking.api.model.MeetingType;
import com.callbooking.api.model.MeetingTypeSummary;
import com.callbooking.config.AppProperties;
import com.callbooking.persistence.MeetingTypeRepository;

@Service
public class MeetingTypeService {

    private static final int DURATION_ALIGNMENT_MINUTES = 15;

    private final MeetingTypeRepository repository;
    private final AppProperties properties;

    public MeetingTypeService(MeetingTypeRepository repository, AppProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    public List<MeetingType> list() {
        return repository.findAll();
    }

    public MeetingType get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> ApiException.notFound(
                        "MEETING_TYPE_NOT_FOUND",
                        "Тип встречи с идентификатором " + id + " не найден."));
    }

    public MeetingType create(CreateMeetingTypeRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("INVALID_NAME", "Название типа встречи не может быть пустым.");
        }
        if (name.length() > 80) {
            throw ApiException.badRequest("INVALID_NAME", "Название не может быть длиннее 80 символов.");
        }

        String description = request.getDescription() == null ? "" : request.getDescription().trim();
        if (description.length() > 1000) {
            throw ApiException.badRequest("INVALID_DESCRIPTION", "Описание не может быть длиннее 1000 символов.");
        }

        int duration = request.getDurationMinutes() == null ? 0 : request.getDurationMinutes();
        AppProperties.Owner owner = properties.owner();
        if (duration < owner.minimumDurationMinutes() || duration > owner.maximumDurationMinutes()) {
            throw ApiException.badRequest(
                    "INVALID_DURATION",
                    "Длительность должна быть от " + owner.minimumDurationMinutes()
                            + " до " + owner.maximumDurationMinutes() + " минут.");
        }
        if (duration % DURATION_ALIGNMENT_MINUTES != 0) {
            throw ApiException.badRequest(
                    "INVALID_DURATION",
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
}
