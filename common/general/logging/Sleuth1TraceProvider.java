package com.arun.general.logging;

import brave.Tracer;

/**
 * @author
 */
public class Sleuth1TraceProvider implements TraceIdProvider {

    private Tracer tracer;

    public Sleuth1TraceProvider (Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public String getTraceId() {
        if (this.tracer != null && this.tracer.currentSpan() != null) {
            return this.tracer.currentSpan().context().traceIdString();
        }
        return null;
    }
}
