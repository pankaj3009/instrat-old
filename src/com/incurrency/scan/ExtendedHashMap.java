/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scan;

import java.util.HashMap;

/**
 *
 * @author Pankaj
 */
public class ExtendedHashMap<J, K, V> {

    public HashMap<J, HashMap<K, V>> store = new HashMap<>();

    public void add(J key, K subkey, V value) {
        if (store.get(key) == null) {
            HashMap<K, V> temp = new HashMap<>();
            temp.put(subkey, value);
            store.put(key, temp);
        } else {
            store.get(key).put(subkey, value);
        }
    }
}
