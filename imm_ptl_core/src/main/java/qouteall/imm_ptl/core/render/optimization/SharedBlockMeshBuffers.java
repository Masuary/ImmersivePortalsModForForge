package qouteall.imm_ptl.core.render.optimization;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ChunkBufferBuilderPack;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.q_misc_util.Helper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * This optimization makes that different dimensions of ChunkRenderDispatcher
 *  use the same queue of ChunkBufferBuilderPack.
 * In vanilla, it will cause OutOfMemory exception then it will allocate fewer buffers.
 * Some dimension will have no buffer and the chunk cannot rebuild.
 */
public class SharedBlockMeshBuffers {
    public static final ThreadLocal<Object> bufferTemp =
        ThreadLocal.withInitial(() -> null);
    public static final ThreadLocal<Object> taskTemp =
        ThreadLocal.withInitial(() -> null);
    
    // a disposed dispatcher's last compiles still hold buffers until their uploads run, which nobody else triggers
    private static final long DISPOSED_DISPATCHER_DRAIN_NANOS = TimeUnit.SECONDS.toNanos(10);
    private static final Map<ChunkRenderDispatcher, Long> disposedDispatcherDrainDeadlines = new HashMap<>();
    
    public static void init() {
        IPGlobal.clientCleanupSignal.connect(SharedBlockMeshBuffers::cleanup);
        IPGlobal.preGameRenderSignal.connect(SharedBlockMeshBuffers::drainDisposedDispatchers);
    }
    
    public static void onDispatcherDisposed(ChunkRenderDispatcher dispatcher) {
        dispatcher.uploadAllPendingUploads();
        disposedDispatcherDrainDeadlines.put(dispatcher, System.nanoTime() + DISPOSED_DISPATCHER_DRAIN_NANOS);
    }
    
    private static void drainDisposedDispatchers() {
        if (disposedDispatcherDrainDeadlines.isEmpty()) {
            return;
        }
        long now = System.nanoTime();
        disposedDispatcherDrainDeadlines.entrySet().removeIf(entry -> {
            entry.getKey().uploadAllPendingUploads();
            return now - entry.getValue() > 0;
        });
    }
    
    /**
     * {@link net.minecraft.client.renderer.chunk.ChunkRenderDispatcher}
     */
    public static ConcurrentLinkedQueue<ChunkBufferBuilderPack> threadBuffers;
    
    public static ConcurrentLinkedQueue<ChunkBufferBuilderPack> acquireThreadBuffers() {
        if (threadBuffers == null) {
            createThreadBuffers();
        }
        return threadBuffers;
    }
    
    private static void createThreadBuffers() {
        Validate.isTrue(IPGlobal.enableSharedBlockMeshBuffers);
        
        int totalExpectedBufferSize =
            RenderType.chunkBufferLayers().stream().mapToInt(RenderType::bufferSize).sum();
        int bufferCountEstimation = Math.max(
            1,
            (int) ((double) Runtime.getRuntime().maxMemory() * 0.3D) / (totalExpectedBufferSize * 4) - 1
        );
        int availableProcessors = Runtime.getRuntime().availableProcessors();
        boolean is64Bits = Minecraft.getInstance().is64Bit();
        int effectiveProcessors = is64Bits ? availableProcessors : Math.min(availableProcessors, 4);
        int bufferCount = Math.max(1, Math.min(effectiveProcessors, bufferCountEstimation));
        
        ArrayList<ChunkBufferBuilderPack> list = new ArrayList<>();
        
        try {
            for (int m = 0; m < bufferCount; ++m) {
                list.add(new ChunkBufferBuilderPack());
            }
        }
        catch (OutOfMemoryError error) {
            error.printStackTrace();
            
            String errorMessage = String.format(
                "[Immersive Portals] Allocated only %s/%s block mesh buffers. Memory seems not enough.",
                list.size(), bufferCount
            );
            
            Helper.err(errorMessage);
            
            CHelper.printChat(errorMessage);
            
            int n = Math.min(list.size() * 2 / 3, list.size() - 1);
            
            for (int i = 0; i < n; ++i) {
                list.remove(list.size() - 1);
            }
            
            System.gc();
        }
        
        // a dispatcher's dispose() clears its buffer queue, which must not empty the pool of the other dimensions
        threadBuffers = new WaitableSharedQueue<>(list);
    }
    
    private static void cleanup() {
        // the buffers are not GL resources and can be collected by GC
        if (threadBuffers != null) {
            threadBuffers = null;
        }
        disposedDispatcherDrainDeadlines.clear();
    }
    
    public static String getDebugString() {
        if (IPGlobal.enableSharedBlockMeshBuffers && threadBuffers != null) {
            return "SharedBlockMeshBuffers " + Integer.toString(threadBuffers.size());
        }
        return "";
    }
}
