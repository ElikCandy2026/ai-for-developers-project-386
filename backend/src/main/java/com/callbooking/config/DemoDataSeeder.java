package com.callbooking.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.callbooking.application.MeetingTypeService;
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
        meetingTypeService.create(
                "Быстрый вопрос",
                "Пятнадцатиминутный звонок для короткого вопроса.",
                15);
        meetingTypeService.create(
                "Знакомство",
                "Короткий звонок, чтобы познакомиться и обсудить задачу.",
                30);
        meetingTypeService.create(
                "Консультация",
                "Подробный разбор вопроса и совместный план действий.",
                60);
    }
}
