package com.bhel.hrm.server.dao.jdbc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.bhel.hrm.common.model.LeaveType;
import com.bhel.hrm.server.db.DatabaseTestBase;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LeaveTypeDaoTest extends DatabaseTestBase {

    @Test
    void findAllReturnsTheFourSeededTypesInIdOrder() {
        List<LeaveType> types = database.read(da -> da.leaveTypes().findAll());

        assertEquals(List.of("Annual", "Medical", "Emergency", "Unpaid"), types.stream().map(LeaveType::name).toList());
    }

    @Test
    void theSeededAllowancesMatchTheDecisionLog() {
        List<LeaveType> types = database.read(da -> da.leaveTypes().findAll());

        assertEquals(14, types.get(0).annualAllowance());
        assertEquals(14, types.get(1).annualAllowance());
        assertEquals(3, types.get(2).annualAllowance());
        assertNull(types.get(3).annualAllowance());
    }

    @Test
    void findByIdReturnsTheMatchingType() {
        assertEquals(Optional.of(new LeaveType(1L, "Annual", 14)), database.read(da -> da.leaveTypes().findById(1L)));
    }

    @Test
    void findByIdForAnUnknownTypeIsEmpty() {
        assertEquals(Optional.empty(), database.read(da -> da.leaveTypes().findById(999L)));
    }
}
