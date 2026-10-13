package com.example.exam.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;

/** CommandLineRunner with explicit ordering between startup seed tasks. */
final class OrderedCommandLineRunner implements CommandLineRunner, Ordered {
    private final int order;
    private final CommandLineRunner delegate;

    OrderedCommandLineRunner(int order, CommandLineRunner delegate) {
        this.order = order;
        this.delegate = delegate;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public void run(String... args) throws Exception {
        delegate.run(args);
    }
}
