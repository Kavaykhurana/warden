package edu.ashbrook.warden.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Everything the tools did during one chat turn. {@link ToolGuard} holds the lock while a tool runs, so
 * {@link #closeAndSnapshot()} waits for a running tool and no tool can start after the turn has been closed — an
 * action can never happen without being shown to the user.
 */
public final class ToolEventCollector {

    static final int MAX_CALLS_PER_TURN = 6;

    private final ReentrantLock lock = new ReentrantLock();
    private final List<ToolEvent> events = new ArrayList<>();
    private final Map<String, ToolOutcome> outcomes = new HashMap<>();
    private int calls;
    private boolean closed;
    private boolean capReported;

    void lock() {
        lock.lock();
    }

    void unlock() {
        lock.unlock();
    }

    boolean isClosed() {
        return closed;
    }

    ToolOutcome previous(String key) {
        return outcomes.get(key);
    }

    /** Counts a new call; false once the per-turn cap is exceeded. */
    boolean tryCount() {
        calls++;
        return calls <= MAX_CALLS_PER_TURN;
    }

    /** True only the first time the cap is hit, so a looping model produces one FAILED event, not many. */
    boolean firstOverCap() {
        boolean first = !capReported;
        capReported = true;
        return first;
    }

    void record(String key, ToolEvent event, ToolOutcome outcome) {
        events.add(event);
        outcomes.put(key, outcome);
    }

    public List<ToolEvent> closeAndSnapshot() {
        lock.lock();
        try {
            closed = true;
            return List.copyOf(events);
        } finally {
            lock.unlock();
        }
    }

    public List<ToolEvent> snapshot() {
        lock.lock();
        try {
            return List.copyOf(events);
        } finally {
            lock.unlock();
        }
    }
}
