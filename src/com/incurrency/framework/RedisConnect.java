/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 *
 * @author Pankaj
 */
public class RedisConnect<K, V> implements Database<K, V> {

    public JedisPool pool;
    String uri;
    int port;

    public RedisConnect(String uri, int port, int database) {
        this.uri = uri;
        this.port = port;
        pool = new JedisPool(new JedisPoolConfig(),uri, port,2000,null,database);
    }

    @Override
    public Long delKey(String storeName, String key) {
        try (Jedis jedis = pool.getResource()) {
           if( key.contains("_")){
            return jedis.del(key.toString());
        }else{
               return jedis.del(storeName + "_" + key);
           }
        }
    }

    @Override
    public Set<String> getKeys(String storeName) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.keys(storeName + "*");
        }
    }

    @Override
    public Object IncrementKey(String StoreName, String key, K field, int incr) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Long setHash(String StoreName, String key, K field, V value) {
        try (Jedis jedis = pool.getResource()) {
            if (key.contains("_")) {
                return jedis.hset(key, field.toString(), value.toString());
            } else {
                return jedis.hset(StoreName + "_" + key, field.toString(), value.toString());

            }
        }
    }
    
      public Long setHash(String key, K field, V value) {
        try (Jedis jedis = pool.getResource()) {
                return jedis.hset(key, field.toString(), value.toString());
            }
    }
    /*
    /*
     @Override
     public void removeValue(String StoreName, String key, K field) {
     try (Jedis jedis = pool.getResource()) {
     jedis.hset(StoreName+"_"+key, field.toString(), value.toString());
     }}
     }
     */

    @Override
    public V getValue(String storeName, String key, K field) {
        try (Jedis jedis = pool.getResource()) {
            if (key.contains("_")) {
                Object out = jedis.hget(key, field.toString());
                if (out != null) {
                    return (V) jedis.hget(key, field.toString());
                } else {
                    return null;
                }
            } else {
                Object out = jedis.hget(storeName + "_" + key, field.toString());
                if (out != null) {
                    return (V) jedis.hget(storeName + "_" + key, field.toString());
                } else {
                    return null;
                }
            }
        }
    }

    @Override
    public ConcurrentHashMap<K, V> getValues(String storeName, String Key) {
        try (Jedis jedis = pool.getResource()) {
            Map<String, String> in = jedis.hgetAll(Key);
            return new <K, V>ConcurrentHashMap(in);

        }
    }

    @Override
    public void rename(String oldStoreName, String newStoreName, String oldKeyName, String newKeyName) {
        try (Jedis jedis = pool.getResource()) {
            jedis.rename(oldKeyName, newKeyName);
        }
    }

    @Override
    public synchronized List<String> blpop(String storeName,String key,int duration) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.blpop(duration, storeName+key);
        }
    }

    @Override
    public List<String> brpop(String storeName, String key, int duration) {
                try (Jedis jedis = pool.getResource()) {
            return jedis.brpop(duration, storeName+key);
        }
    }

    @Override
    public List<String> lrange(String storeName, String key, int start, int end) {
                try (Jedis jedis = pool.getResource()) {
            return jedis.lrange(storeName+key,start,end);  
                }
    }

    @Override
    public void rename(String storeName, String newStoreName) {
         try (Jedis jedis = pool.getResource()) {
             jedis.rename(storeName, newStoreName);
         }
    }

    @Override
    public void lpush(String key, String value) {
        try (Jedis jedis = pool.getResource()) {
             jedis.lpush(key, value);
         }
    }
}
