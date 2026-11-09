package com.callbooking.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import com.callbooking.api.model.Availability;
import com.callbooking.api.model.Booking;
import com.callbooking.api.model.CreateBookingRequest;
import com.callbooking.api.model.CreateMeetingTypeRequest;
import com.callbooking.api.model.MeetingType;
import com.callbooking.api.model.Owner;
import com.callbooking.application.AvailabilityService;
import com.callbooking.application.BookingService;
import com.callbooking.application.MeetingTypeService;
import com.callbooking.application.OwnerService;

/**
 * HTTP adapter. Routes, request/response models and their validation constraints
 * are generated from the TypeSpec contract into the *Api interfaces and model
 * classes implemented below. This layer translates between the generated
 * contract and the application use cases.
 */
@RestController
public class CallBookingController
        implements OwnerApi, MeetingTypesApi, AvailabilityApi, BookingsApi {

    private final OwnerService ownerService;
    private final MeetingTypeService meetingTypeService;
    private final AvailabilityService availabilityService;
    private final BookingService bookingService;

    public CallBookingController(
            OwnerService ownerService,
            MeetingTypeService meetingTypeService,
            AvailabilityService availabilityService,
            BookingService bookingService) {
        this.ownerService = ownerService;
        this.meetingTypeService = meetingTypeService;
        this.availabilityService = availabilityService;
        this.bookingService = bookingService;
    }

    @Override
    public ResponseEntity<Owner> getOwner() {
        return ResponseEntity.ok(DomainApiMapper.toApi(ownerService.getOwner()));
    }

    @Override
    public ResponseEntity<List<MeetingType>> listMeetingTypes() {
        return ResponseEntity.ok(DomainApiMapper.toApi(meetingTypeService.list()));
    }

    @Override
    public ResponseEntity<MeetingType> createMeetingType(CreateMeetingTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DomainApiMapper.toApi(meetingTypeService.create(
                        request.getName(),
                        request.getDescription(),
                        request.getDurationMinutes())));
    }

    @Override
    public ResponseEntity<MeetingType> getMeetingType(String meetingTypeId) {
        return ResponseEntity.ok(DomainApiMapper.toApi(meetingTypeService.get(meetingTypeId)));
    }

    @Override
    public ResponseEntity<Availability> getAvailability(String meetingTypeId) {
        return ResponseEntity.ok(DomainApiMapper.toApi(availabilityService.getAvailability(meetingTypeId)));
    }

    @Override
    public ResponseEntity<Booking> createBooking(CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DomainApiMapper.toApi(bookingService.create(
                        request.getMeetingTypeId(),
                        request.getStart().toInstant(),
                        request.getGuestName(),
                        request.getGuestEmail(),
                        request.getNotes())));
    }

    @Override
    public ResponseEntity<List<Booking>> listBookings(Boolean includePast) {
        return ResponseEntity.ok(DomainApiMapper.bookingsToApi(
                bookingService.list(Boolean.TRUE.equals(includePast))));
    }
}
