package com.hsf302.trainoffice.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeatBerthLevelTest {

    @Test
    void fourBerthCoachSeatsTierLevelsAndLabels() {
        Coach coach = Coach.builder().sleeperCoach(true).berthsPerCompartment(4).build();

        Seat s1A = Seat.builder().coach(coach).seatNumber("K01-L1A").berthLevel("LOWER").build();
        Seat s1B = Seat.builder().coach(coach).seatNumber("K01-L1B").berthLevel("LOWER").build();
        Seat s2A = Seat.builder().coach(coach).seatNumber("K01-L2A").berthLevel("UPPER").build();
        Seat s2B = Seat.builder().coach(coach).seatNumber("K01-L2B").berthLevel("UPPER").build();

        // 1A & 1B -> Tầng 1
        assertEquals(1, s1A.getTierLevel());
        assertEquals("booking.level1", s1A.getBerthLevelKey());
        assertEquals("booking.level1LowerBerth", s1A.getBerthFullLabelKey());

        assertEquals(1, s1B.getTierLevel());
        assertEquals("booking.level1", s1B.getBerthLevelKey());
        assertEquals("booking.level1LowerBerth", s1B.getBerthFullLabelKey());

        // 2A & 2B -> Tầng 2
        assertEquals(2, s2A.getTierLevel());
        assertEquals("booking.level2", s2A.getBerthLevelKey());
        assertEquals("booking.level2UpperBerth", s2A.getBerthFullLabelKey());

        assertEquals(2, s2B.getTierLevel());
        assertEquals("booking.level2", s2B.getBerthLevelKey());
        assertEquals("booking.level2UpperBerth", s2B.getBerthFullLabelKey());
    }

    @Test
    void sixBerthCoachSeatsTierLevelsAndLabels() {
        Coach coach = Coach.builder().sleeperCoach(true).berthsPerCompartment(6).build();

        Seat s1A = Seat.builder().coach(coach).seatNumber("K01-L1A").berthLevel("LOWER").build();
        Seat s2A = Seat.builder().coach(coach).seatNumber("K01-L2A").berthLevel("MIDDLE").build();
        Seat s3A = Seat.builder().coach(coach).seatNumber("K01-L3A").berthLevel("UPPER").build();

        assertEquals(1, s1A.getTierLevel());
        assertEquals("booking.level1", s1A.getBerthLevelKey());
        assertEquals("booking.level1LowerBerth", s1A.getBerthFullLabelKey());

        assertEquals(2, s2A.getTierLevel());
        assertEquals("booking.level2", s2A.getBerthLevelKey());
        assertEquals("booking.level2MiddleBerth", s2A.getBerthFullLabelKey());

        assertEquals(3, s3A.getTierLevel());
        assertEquals("booking.level3", s3A.getBerthLevelKey());
        assertEquals("booking.level3UpperBerth", s3A.getBerthFullLabelKey());
    }
}
