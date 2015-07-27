/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 *
 * @author Pankaj
 */
public class ExtendedHashMap<J, K, V> {

    public ConcurrentSkipListMap<J, ConcurrentHashMap<K, V>> store = new ConcurrentSkipListMap<>();
    private int currentSize;
    
    public void put(J key, ConcurrentHashMap<K,V> map) {
            store.put(key, map);     
            this.currentSize=store.size();
    }
    
    public void add(J key, K subkey, V value) {
        if (store.get(key) == null) {
            ConcurrentHashMap<K, V> temp = new ConcurrentHashMap<>();
            temp.put(subkey, value);
            store.put(key, temp);
        } else {
            store.get(key).put(subkey, value);
        }
        this.currentSize=store.size();

    }    
    
    
    public V get(J key, K subkey) {
        if (store.get(key) == null) {
            return null;
        } else {
            return store.get(key).get(subkey);
        }
    }
    
    public J getLastKey(){
        return store.lastKey();
    }

    }
/*
    @Override
    public Iterator iterator() {
        Iterator<ConcurrentHashMap> it = new Iterator<ConcurrentHashMap>() {
            private J currentIndex = store.firstKey();

            @Override
            public boolean hasNext() {
                return store.ceilingKey(currentIndex)!=null;
            }

            @Override
            public ConcurrentHashMap<K,V> next() {
                currentIndex=store.ceilingKey(currentIndex);
                return store.get(currentIndex);
            }

            @Override
            public void remove() {
                if(!hasNext()) throw new NoSuchElementException();
                store.remove(currentIndex);
                   
                }            
        };
        return it;
    }
    */
    
    

