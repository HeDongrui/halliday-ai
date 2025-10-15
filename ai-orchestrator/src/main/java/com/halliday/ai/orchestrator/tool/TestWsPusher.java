package com.halliday.ai.orchestrator.tool;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 本地测试工具，将 WAV 音频切分成 20ms 帧推送到 orchestrator WebSocket，并收集返回音频。
 */
public final class TestWsPusher {

    private TestWsPusher() {
    }

    /**
     * 命令行入口：java -cp target/... TestWsPusher <wavPath> [wsUrl]
     *
     * @param args 命令行参数
     * @throws Exception IO 或网络异常
     */
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("用法: java TestWsPusher <wav路径> [wsUrl]");
            return;
        }
        Path wavPath = Path.of(args[0]);
        String wsUrl = args.length > 1 ? args[1] : "ws://127.0.0.1:9099/ai/stream";
        byte[] wavBytes = Files.readAllBytes(wavPath);
        byte[] pcmData = stripWavHeaderIfPresent(wavBytes);
        List<byte[]> frames = splitFrames(pcmData, 640);
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        try (BufferedOutputStream bos = new BufferedOutputStream(Files.newOutputStream(Path.of("out.pcm")))) {
            java.net.http.WebSocket webSocket = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build()
                    .newWebSocketBuilder()
                    .buildAsync(java.net.URI.create(wsUrl), new Listener(bos))
                    .join();
            sendFrames(webSocket, frames, scheduler);
            long totalDurationMs = frames.size() * 20L + 200;
            scheduler.schedule(() -> webSocket.sendText("{\"type\":\"eos\"}", true), totalDurationMs, TimeUnit.MILLISECONDS);
            scheduler.schedule(() -> webSocket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "done"), totalDurationMs + 200, TimeUnit.MILLISECONDS);
            scheduler.shutdown();
            scheduler.awaitTermination(totalDurationMs + 1_000, TimeUnit.MILLISECONDS);
        }
        System.out.println("推流完成，结果已写入 out.pcm，可使用 ffplay 播放。");
    }

    private static void sendFrames(java.net.http.WebSocket webSocket, List<byte[]> frames, ScheduledExecutorService scheduler) {
        for (int i = 0; i < frames.size(); i++) {
            byte[] frame = frames.get(i);
            scheduler.schedule(() -> webSocket.sendBinary(ByteBuffer.wrap(frame), true), i * 20L, TimeUnit.MILLISECONDS);
        }
    }

    private static byte[] stripWavHeaderIfPresent(byte[] wavBytes) {
        if (wavBytes.length > 44 && wavBytes[0] == 'R' && wavBytes[1] == 'I') {
            byte[] pcm = new byte[wavBytes.length - 44];
            System.arraycopy(wavBytes, 44, pcm, 0, pcm.length);
            return pcm;
        }
        return wavBytes;
    }

    private static List<byte[]> splitFrames(byte[] data, int frameSize) {
        List<byte[]> frames = new ArrayList<>();
        for (int offset = 0; offset < data.length; offset += frameSize) {
            int len = Math.min(frameSize, data.length - offset);
            byte[] frame = new byte[len];
            System.arraycopy(data, offset, frame, 0, len);
            frames.add(frame);
        }
        return frames;
    }

    private static class Listener implements java.net.http.WebSocket.Listener {

        private final BufferedOutputStream output;

        Listener(BufferedOutputStream output) {
            this.output = output;
        }

        @Override
        public void onOpen(java.net.http.WebSocket webSocket) {
            System.out.println("WebSocket 已连接");
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
            System.out.println("收到文本: " + data);
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onBinary(java.net.http.WebSocket webSocket, ByteBuffer data, boolean last) {
            try {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                output.write(bytes);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(java.net.http.WebSocket webSocket, Throwable error) {
            error.printStackTrace();
        }
    }
}
