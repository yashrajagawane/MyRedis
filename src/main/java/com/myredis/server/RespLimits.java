package com.myredis.server;

import com.myredis.protocol.RespDecoder;

final class RespLimits {
    static final int DEFAULT_MAX_VALUE_BYTES = RespDecoder.DEFAULT_MAX_VALUE_BYTES;
    static final int DEFAULT_MAX_ARRAY_ELEMENTS = RespDecoder.DEFAULT_MAX_ARRAY_ELEMENTS;

    private RespLimits() {
    }
}
