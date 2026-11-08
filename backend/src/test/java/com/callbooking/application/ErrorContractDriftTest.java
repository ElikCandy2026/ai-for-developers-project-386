package com.callbooking.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.callbooking.api.model.BadRequestCode;
import com.callbooking.api.model.ConflictCode;
import com.callbooking.api.model.InternalErrorCode;
import com.callbooking.api.model.NotFoundCode;

/**
 * Guards the closed error contract: the application enum must expose exactly the
 * codes generated from the TypeSpec enums, no more and no fewer.
 */
class ErrorContractDriftTest {

    @Test
    void applicationCodesMirrorContractEnums() {
        Set<String> contract = Stream.of(
                Arrays.stream(BadRequestCode.values()).map(BadRequestCode::getValue),
                Arrays.stream(NotFoundCode.values()).map(NotFoundCode::getValue),
                Arrays.stream(ConflictCode.values()).map(ConflictCode::getValue),
                Arrays.stream(InternalErrorCode.values()).map(InternalErrorCode::getValue))
                .flatMap(codes -> codes)
                .collect(Collectors.toSet());

        Set<String> application = Arrays.stream(ApplicationErrorCode.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(contract).hasSize(7);
        assertThat(application).containsExactlyInAnyOrderElementsOf(contract);
    }

    @Test
    void statusesMatchTheContractGroups() {
        assertThat(ApplicationErrorCode.VALIDATION_FAILED.status()).isEqualTo(400);
        assertThat(ApplicationErrorCode.MALFORMED_REQUEST.status()).isEqualTo(400);
        assertThat(ApplicationErrorCode.INVALID_DURATION.status()).isEqualTo(400);
        assertThat(ApplicationErrorCode.MEETING_TYPE_NOT_FOUND.status()).isEqualTo(404);
        assertThat(ApplicationErrorCode.SLOT_UNAVAILABLE.status()).isEqualTo(409);
        assertThat(ApplicationErrorCode.SLOT_TAKEN.status()).isEqualTo(409);
        assertThat(ApplicationErrorCode.INTERNAL_ERROR.status()).isEqualTo(500);
    }
}
