package com.halliday.ai.common.stt;

import java.util.Objects;

public final class SttResult {

    private final String text;
    private final boolean finished;
    private final int idx;

    private SttResult(Builder builder) {
        this.text = builder.text;
        this.finished = builder.finished;
        this.idx = builder.idx;
    }

    public String getText() {
        return text;
    }

    public boolean isFinished() {
        return finished;
    }

    public int getIdx() {
        return idx;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String text = "";
        private boolean finished;
        private int idx;

        public Builder text(String text) {
            this.text = Objects.requireNonNullElse(text, "");
            return this;
        }

        public Builder finished(boolean finished) {
            this.finished = finished;
            return this;
        }

        public Builder idx(int idx) {
            this.idx = idx;
            return this;
        }

        public SttResult build() {
            return new SttResult(this);
        }
    }
}
