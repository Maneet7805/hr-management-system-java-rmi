package com.bhel.hrm.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bhel.hrm.common.SerializationSupport;
import org.junit.jupiter.api.Test;

class ExceptionContractTest {

    @Test
    void invalidCredentialsSurvivesSerializationWithItsMessage() {
        HrmException copy = SerializationSupport.roundTrip(new InvalidCredentialsException("Wrong ID or password"));

        assertInstanceOf(InvalidCredentialsException.class, copy);
        assertEquals("Wrong ID or password", copy.getMessage());
    }

    @Test
    void permissionDeniedSurvivesSerialization() {
        assertInstanceOf(PermissionDeniedException.class,
                SerializationSupport.roundTrip(new PermissionDeniedException("HR only")));
    }

    @Test
    void validationSurvivesSerialization() {
        assertInstanceOf(ValidationException.class,
                SerializationSupport.roundTrip(new ValidationException("Bad date")));
    }

    @Test
    void notFoundSurvivesSerialization() {
        assertInstanceOf(NotFoundException.class,
                SerializationSupport.roundTrip(new NotFoundException("No such employee")));
    }

    @Test
    void serviceUnavailableSurvivesSerialization() {
        assertInstanceOf(ServiceUnavailableException.class,
                SerializationSupport.roundTrip(new ServiceUnavailableException("Try again later")));
    }

    @Test
    void exceptionsNeverCarryACauseSoNoSqlDetailsLeakToTheClient() {
        assertNull(new ServiceUnavailableException("Try again later").getCause());
    }
}
