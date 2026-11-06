package qouteall.imm_ptl.core;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.q_misc_util.my_util.MyTaskList;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Some mods tick server levels on their own threads while the server thread ticks the other levels (Vault Hunters
 * ticks every vault level on its pool). Off its owning thread a level must not be read through anything that waits
 * for the owner ({@code ServerChunkCache.getChunk} joins the owner's mailbox, which deadlocks when the owner is
 * waiting for this thread), and no thread may change a level it does not own.
 * <p>
 * During such a tick a level's {@code Level.thread} is swapped to the ticking thread, but the server thread can still
 * read the old value, so the levels ticking off the server thread are tracked here as well.
 */
public final class LevelThreads {
    private static final Set<Level> levelsTickingOffServerThread = ConcurrentHashMap.newKeySet();

    private LevelThreads() {
    }
    
    public static void init() {
        IPGlobal.serverCleanupSignal.connect(LevelThreads::clearTickingOffServerThread);
    }

    /**
     * Called on the server thread before the level's tick is handed to another thread.
     */
    public static void markTickingOffServerThread(Level level) {
        levelsTickingOffServerThread.add(level);
    }

    /**
     * Called on the server thread once every level handed to another thread has finished its tick.
     */
    public static void clearTickingOffServerThread() {
        levelsTickingOffServerThread.clear();
    }

    public static boolean isOwnedByCurrentThread(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return true;
        }
        return isOwner(
            ((IEWorld) level).portal_getThread() == Thread.currentThread(),
            levelsTickingOffServerThread.contains(level),
            serverLevel.getServer().isSameThread()
        );
    }
    
    /**
     * @param isRecordedOwnerThread the level's {@code Level.thread} is the current thread
     * @param isTickingOffServerThread the level is being ticked by another mod's thread right now
     * @param isServerThread the current thread is the server thread, which can still read the level's old thread
     */
    public static boolean isOwner(boolean isRecordedOwnerThread, boolean isTickingOffServerThread, boolean isServerThread) {
        if (isTickingOffServerThread) {
            return isRecordedOwnerThread && !isServerThread;
        }
        return isRecordedOwnerThread;
    }

    /**
     * Runs the task now if the current thread owns every given level, otherwise on the server thread at the end of
     * this tick, when no level ticks elsewhere.
     */
    public static void runOwning(Runnable task, Level... levels) {
        for (Level level : levels) {
            if (!isOwnedByCurrentThread(level)) {
                IPGlobal.serverTaskList.addTask(MyTaskList.oneShotTask(task));
                return;
            }
        }
        task.run();
    }
}
