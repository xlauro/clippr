package com.laurosantos.clippr.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class EventBus {

    private static final Logger logger = LoggerFactory.getLogger(EventBus.class);
    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<? extends ClipprEvent>, List<Consumer<? extends ClipprEvent>>> listeners = new ConcurrentHashMap<>();
    private final ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private EventBus() {}

    public static EventBus getInstance() {
        return INSTANCE;
    }

    public <T extends ClipprEvent> void subscribe(Class<T> eventType, Consumer<T> listener) {
        listeners.computeIfAbsent(eventType, _ -> new CopyOnWriteArrayList<>())
                .add(listener);
    }

    public void publish(ClipprEvent event) {
        virtualExecutor.submit(() -> {
            for (Map.Entry<Class<? extends ClipprEvent>, List<Consumer<? extends ClipprEvent>>> entry : listeners.entrySet()) {
                if (entry.getKey().isInstance(event)) {
                    for (Consumer<? extends ClipprEvent> listener : entry.getValue()) {
                        try {
                            dispatch(listener, event);
                        } catch (Exception ex) {
                            logger.error("Erro ao despachar evento {}: {}", event.getClass().getSimpleName(), ex.getMessage(), ex);
                        }
                    }
                }
            }
        });
    }

    public void publishSync(ClipprEvent event) {
        for (Map.Entry<Class<? extends ClipprEvent>, List<Consumer<? extends ClipprEvent>>> entry : listeners.entrySet()) {
            if (entry.getKey().isInstance(event)) {
                for (Consumer<? extends ClipprEvent> listener : entry.getValue()) {
                    try {
                        dispatch(listener, event);
                    } catch (Exception ex) {
                        logger.error("Erro síncrono ao despachar evento {}: {}", event.getClass().getSimpleName(), ex.getMessage(), ex);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void dispatch(Consumer<? extends ClipprEvent> listener, ClipprEvent event) {
        ((Consumer<ClipprEvent>) listener).accept(event);
    }
}
