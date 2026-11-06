package qouteall.imm_ptl.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LevelThreadsTest {
    @Test
    void theServerThreadOwnsLevelsThatTickOnIt() {
        assertTrue(LevelThreads.isOwner(true, false, true));
    }

    @Test
    void aPoolThreadDoesNotOwnLevelsThatTickOnTheServerThread() {
        assertFalse(LevelThreads.isOwner(false, false, false));
    }

    @Test
    void aPoolThreadOwnsTheLevelItTicks() {
        assertTrue(LevelThreads.isOwner(true, true, false));
    }

    @Test
    void aPoolThreadDoesNotOwnAnotherPoolThreadsLevel() {
        assertFalse(LevelThreads.isOwner(false, true, false));
    }

    // the server thread can still read its own thread in a level the pool already ticks
    @Test
    void theServerThreadDoesNotOwnALevelTickingOnThePoolEvenIfItReadsAStaleThread() {
        assertFalse(LevelThreads.isOwner(true, true, true));
        assertFalse(LevelThreads.isOwner(false, true, true));
    }
}
