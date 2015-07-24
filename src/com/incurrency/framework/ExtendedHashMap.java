/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.HashMap;

/**
 *
 * @author Pankaj
 */
public class ExtendedHashMap<J, K, V> {

    public HashMap<J, HashMap<K, V>> store = new HashMap<>();

    public void put(J key, HashMap<K,V> map) {
            store.put(key, map);            
    }
    
    public void add(J key, K subkey, V value) {
        if (store.get(key) == null) {
            HashMap<K, V> temp = new HashMap<>();
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
    
    public HashMap <K,V> get(J key) {
        if (store.get(key) == null) {
            return null;
        } else {
            return store.get(key);
        }
    }
    
}
