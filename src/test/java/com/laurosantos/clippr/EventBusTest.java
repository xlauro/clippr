package com.laurosantos.clippr;

import com.laurosantos.clippr.events.ClipprEvent;
import com.laurosantos.clippr.events.EventBus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    @Test
    @DisplayName("Deve publicar e receber evento assíncrono via EventBus em Virtual Threads")
    void shouldPublishAndReceiveEventAsynchronously() throws InterruptedException {
        EventBus bus = EventBus.getInstance();
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> receivedMessage = new AtomicReference<>();
        AtomicReference<Boolean> isVirtualThread = new AtomicReference<>(false);

        bus.subscribe(ClipprEvent.LogMessage.class, event -> {
            isVirtualThread.set(Thread.currentThread().isVirtual());
            receivedMessage.set(event.message());
            latch.countDown();
        });

        bus.publish(new ClipprEvent.LogMessage("Teste de Mensagem Assíncrona", false));

        boolean completed = latch.await(3, TimeUnit.SECONDS);
        assertTrue(completed, "O listener deve ser invocado dentro do tempo limite");
        assertEquals("Teste de Mensagem Assíncrona", receivedMessage.get());
        assertTrue(isVirtualThread.get(), "O evento deve rodar em uma Virtual Thread do Java 25");
    }
}
