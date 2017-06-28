/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author Pankaj
 */
public class RedisConnect<K, V> implements Database<K, V> {

    private static final Logger logger = Logger.getLogger(RedisConnect.class.getName());

    private static final Object blpop_lock = new Object();
    private static final Object lpush_lock = new Object();
    public JedisPool pool;
    String uri;
    int port;

    public RedisConnect(String uri, int port, int database) {
        this.uri = uri;
        this.port = port;
        pool = new JedisPool(new JedisPoolConfig(), uri, port, 2000, null, database);
    }

    @Override
    public Long delKey(String storeName, String key) {
        try (Jedis jedis = pool.getResource()) {
            if (key.contains("_")) {
                return jedis.del(key.toString());
            } else {
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
    public List<String> blpop(String storeName, String key, int duration) {
        //synchronized(blpop_lock){
        try (Jedis jedis = pool.getResource()) {
            return jedis.blpop(duration, storeName + key);
        }
        //}
    }

    @Override
    public List<String> brpop(String storeName, String key, int duration) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.brpop(duration, storeName + key);
        }
    }

    @Override
    public List<String> lrange(String storeName, String key, int start, int end) {
        try (Jedis jedis = pool.getResource()) {
            return jedis.lrange(storeName + key, start, end);
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
        //synchronized (lpush_lock) {
        try (Jedis jedis = pool.getResource()) {
            jedis.lpush(key, value);
        }
    }
    //}

    @Override
    public Set<String> getMembers(String storeName, String searchString) {
        Set<String> shortlist = new HashSet<>();
        String cursor = "";
        while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = pool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains(searchString)) {
                        shortlist.addAll(jedis.smembers(key.toString()));
                    }
                }
            }
        }
        return shortlist;
    }

    @Override
    public OrderBean getLatestOrderBean(String key) {
        OrderBean ob = null;
        try (Jedis jedis = pool.getResource()) {
            Object o = jedis.lrange(key, -1, -1);
            try {
                Type type = new TypeToken<List<OrderBean>>() {
                }.getType();
                Gson gson = new GsonBuilder().create();
                ob = gson.fromJson((String) o, type);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "{0}_{1}", new Object[]{(String) o, key});
            }
        }
        return ob;
    }

    @Override
    public void insertOrder(String key, OrderBean ob) {
        ob.setUpdateTime();
        try (Jedis jedis = pool.getResource()) {
            Gson gson = new GsonBuilder().create();
            String string = gson.toJson(ob);
            jedis.lpush(key, string);
        }
    }

    @Override
    public OrderBean getTradeBean(String key) {
        OrderBean ob = null;
        try (Jedis jedis = pool.getResource()) {
            Object o = jedis.hgetAll(key);
            try {
                Type type = new TypeToken<OrderBean>() {
                }.getType();
                Gson gson = new GsonBuilder().create();
                ob = gson.fromJson((String) o, type);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "{0}_{1}", new Object[]{(String) o, key});
            }
        }
        return ob;
    }

    @Override
    public void updateOrderBean(String key, OrderBean ob) {
        try (Jedis jedis = pool.getResource()) {
            Gson gson = new GsonBuilder().create();
            String string = gson.toJson(ob);
            jedis.lpush(key, string);
        }

    }

    @Override
    public Set<String> getKeysOfList(String storeName, String searchString) {
        Set<String> shortlist = new HashSet<>();
        String cursor = "";
        while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = pool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getCursor();
                for (Object key : s.getResult()) {
                    if (Pattern.compile(searchString).matcher(key.toString()).find()) {
                        shortlist.add(key.toString());
                    }
                }
            }
        }
        return shortlist;
    }

}
