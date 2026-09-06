package com.myredis.benchmark;

import org.openjdk.jmh.Main;

public final class RunBenchmarks {
    private RunBenchmarks() {
    }

    public static void main(String[] args) throws Exception {
        Main.main(args.length == 0 ? new String[]{"StorageBenchmark"} : args);
    }
}
