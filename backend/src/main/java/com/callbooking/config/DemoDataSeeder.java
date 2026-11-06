package com.callbooking.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.callbooking.api.model.CreateMeetingTypeRequest;
import com.callbooking.domain.MeetingTypeService;
import com.callbooking.persistence.MeetingTypeRepository;

/**
 * Optional demo content so a fresh checkout has something to show. It is only
 * added when the meeting type table is empty and can be disabled with
 * SEED_DEMO_DATA=false.
 */
@Component
@ConditionalOnProperty(prefix = "app", name = "seed-demo-data", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final MeetingTypeRepository repository;
    private final MeetingTypeService meetingTypeService;

    public DemoDataSeeder(MeetingTypeRepository repository, MeetingTypeService meetingTypeService) {
        this.repository = repository;
        this.meetingTypeService = meetingTypeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        meetingTypeService.create(new CreateMeetingTypeRequest()
                .name("Быстрый вопрос")
                .description("Пятнадцатиминутный звонок для короткого вопроса.")
                .durationMinutes(15));
        meetingTypeService.create(new CreateMeetingTypeRequest()
                .name("Знакомство")
                .description("Короткий звонок, чтобы познакомиться и обсудить задачу.")
                .durationMinutes(30));
        meetingTypeService.create(new CreateMeetingTypeRequest()
                .name("Консультация")
                .description("Подробный разбор вопроса и совместный план действий.")
                .durationMinutes(60));
    }
}
