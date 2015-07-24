/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author Pankaj
 */
public class ExtendedHashMap<J, K, V> {

    public ConcurrentHashMap<J, ConcurrentHashMap<K, V>> store = new ConcurrentHashMap<>();

    public void put(J key, ConcurrentHashMap<K,V> map) {
            store.put(key, map);            
    }
    
    public void add(J key, K subkey, V value) {
        if (store.get(key) == null) {
            ConcurrentHashMap<K, V> temp = new ConcurrentHashMap<>();
            temp.put(subkey, value);
            store.put(key, temp);
        } else {
            store.get(key).put(subkey, value);
        }
    }    
    
    
    public V get(J key, K subkey) {
        if (store.get(key) == null) {
            return null;
        } else {
            return store.get(key).get(subkey);
        }
    }
    
    public ConcurrentHashMap <K,V> get(J key) {
        if (store.get(key) == null) {
            return null;
        } else {
            return store.get(key);
        }
    }
    
}
