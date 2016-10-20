/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

import com.incurrency.framework.Algorithm;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 *
 * @author Pankaj
 */
public class RedisPublisher {


    public RedisPublisher(int port) {
        
    }

    public synchronized void send(String topic, String message) {
        try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
            jedis.publish(topic, message);
        }
    }

    public synchronized void close() {
       
    }
}
