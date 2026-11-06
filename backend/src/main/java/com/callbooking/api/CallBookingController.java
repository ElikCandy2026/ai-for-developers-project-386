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
import com.callbooking.domain.AvailabilityService;
import com.callbooking.domain.BookingService;
import com.callbooking.domain.MeetingTypeService;
import com.callbooking.domain.OwnerService;

/**
 * HTTP adapter. Routes, request/response models and their validation constraints
 * are generated from the TypeSpec contract into the *Api interfaces and model
 * classes implemented below.
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
        return ResponseEntity.ok(ownerService.getOwner());
    }

    @Override
    public ResponseEntity<List<MeetingType>> listMeetingTypes() {
        return ResponseEntity.ok(meetingTypeService.list());
    }

    @Override
    public ResponseEntity<MeetingType> createMeetingType(CreateMeetingTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(meetingTypeService.create(request));
    }

    @Override
    public ResponseEntity<MeetingType> getMeetingType(String meetingTypeId) {
        return ResponseEntity.ok(meetingTypeService.get(meetingTypeId));
    }

    @Override
    public ResponseEntity<Availability> getAvailability(String meetingTypeId) {
        return ResponseEntity.ok(availabilityService.getAvailability(meetingTypeId));
    }

    @Override
    public ResponseEntity<Booking> createBooking(CreateBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.create(request));
    }

    @Override
    public ResponseEntity<List<Booking>> listBookings(Boolean includePast) {
        return ResponseEntity.ok(bookingService.list(Boolean.TRUE.equals(includePast)));
    }
}
