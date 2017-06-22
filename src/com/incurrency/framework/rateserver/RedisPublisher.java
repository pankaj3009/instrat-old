/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import com.incurrency.framework.Algorithm;
import redis.clients.jedis.Jedis;

/**
 *
 * @author Pankaj
 */
public class RedisPublisher {

    Jedis jedis = Algorithm.marketdatapool.getResource();

    public RedisPublisher(int port) {

    }

    public synchronized void send(String topic, String message) {
        //try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
        jedis.publish(topic, message);
        //}
    }

    public synchronized void close() {

    }
}
