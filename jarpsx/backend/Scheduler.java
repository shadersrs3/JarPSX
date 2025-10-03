package jarpsx.backend;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import jarpsx.backend.Emulator;

public class Scheduler {
    public class Userdata {
        private String message;
        Userdata(String message) {
            this.message = message;
        }
        
        public String getMessage() {
            return message;
        }
    }

    public interface EventCallback {
        public void execute(Userdata userdata);
    }

    private class Event {
        private int eventId;
        private long cycles;
        private Userdata userdata;
    }

    public static final int EVENT_BREAK_DISPATCH = 0;

    private Emulator emulator;
    private long cyclesElapsed;
    private long earlyEventCycles;
    private int earlyEventIndex;
    private List<Event> scheduledEvents;
    private EventCallback[] registeredEventCallbacks;
    private static final int MAX_EVENT_CALLBACKS = 16;

    public void registerEventCallback(int eventId, EventCallback callback) {
        registeredEventCallbacks[eventId] = callback;
    }

    public Scheduler(Emulator emulator) {
        this.emulator = emulator;
        scheduledEvents = new ArrayList<Event>();
        earlyEventCycles = cyclesElapsed = 0L;
        earlyEventIndex = -1;
        registeredEventCallbacks = new EventCallback[MAX_EVENT_CALLBACKS];
    }

    public void addCycles(long cycles) {
        cyclesElapsed += cycles;
    }

    public long getEarlyEventCycles() {
        return earlyEventCycles;
    }

    public long getCyclesElapsed() {
        return cyclesElapsed;
    }

    private void sortEarlyEventCycles() {
        if (scheduledEvents.size() != 0) {
            Collections.sort(scheduledEvents, (a, b) -> { return Long.compare(a.cycles, b.cycles); });
            earlyEventCycles = scheduledEvents.get(0).cycles;
        }
    }

    public void runEvents() {
        Iterator<Event> it = scheduledEvents.iterator();
        while (it.hasNext()) {
            Event e = it.next();
            if (cyclesElapsed >= e.cycles) {
                if (registeredEventCallbacks[e.eventId] != null)
                    registeredEventCallbacks[e.eventId].execute(e.userdata);
                it.remove();
            }
        }

        sortEarlyEventCycles();
    }

    public void schedule(long absoluteCycles, int eventId, Userdata userdata) {
        Event e = new Event();
        e.eventId = eventId;
        e.cycles = absoluteCycles;
        e.userdata = userdata;
        scheduledEvents.add(e);
        sortEarlyEventCycles();
    }
}