package com.gis.servelq.services;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A counter per socket topic, bumped on every push. The push carries it in a
 * "version" STOMP header and the 10s heartbeat lists the latest one for every
 * topic, so an app whose last push was lost sees it is behind and refetches.
 * Counters live in memory: after a restart every socket reconnects and the
 * apps reload anyway.
 */
@Service
public class SocketVersionService {

    private final Map<String, Long> versions = new ConcurrentHashMap<>();

    public long next(String destination) {
        return versions.merge(destination, 1L, Long::sum);
    }

    public Map<String, Long> snapshot() {
        return Map.copyOf(versions);
    }
}
