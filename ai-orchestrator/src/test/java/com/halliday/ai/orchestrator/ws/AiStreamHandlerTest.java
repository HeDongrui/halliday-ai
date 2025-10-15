package com.halliday.ai.orchestrator.ws;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiStreamHandlerTest {

    @Test
    void finalizeInputAsyncClosesStreamBeforeRequestingFinalization() throws Exception {
        Class<?> contextClass = Class.forName("com.halliday.ai.orchestrator.ws.AiStreamHandler$StreamingContext");
        Constructor<?> constructor = contextClass.getDeclaredConstructor(int.class);
        constructor.setAccessible(true);
        Object context = constructor.newInstance(1024);

        Field finalizationRequestedField = contextClass.getDeclaredField("finalizationRequested");
        finalizationRequestedField.setAccessible(true);
        AtomicBoolean finalizationRequested = (AtomicBoolean) finalizationRequestedField.get(context);
        assertFalse(finalizationRequested.get(), "finalization flag should be unset initially");

        Field inputClosedField = contextClass.getDeclaredField("inputClosed");
        inputClosedField.setAccessible(true);
        AtomicBoolean inputClosed = (AtomicBoolean) inputClosedField.get(context);
        assertFalse(inputClosed.get(), "input should be open initially");

        Method finalizeInputAsync = contextClass.getDeclaredMethod("finalizeInputAsync", ExecutorService.class);
        finalizeInputAsync.setAccessible(true);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            finalizeInputAsync.invoke(context, executor);
        } finally {
            executor.shutdown();
        }
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "finalization task did not finish in time");

        assertTrue(inputClosed.get(), "input should be closed before finalization");
        assertTrue(finalizationRequested.get(), "finalization flag should be set after closing input");
    }
}
