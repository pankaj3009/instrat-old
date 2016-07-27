/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 *
 * @author Pankaj
 */
public class DataStore<K,V> implements Database<K,V> {
    private ConcurrentHashMap<String,ConcurrentSkipListMap<String, ConcurrentHashMap<K, V>>> store=new ConcurrentHashMap<>();
    
    @Override
    public Long delKey(String storeName,String key) {
        store.get(storeName).remove(key);
        return 1L;
    }

    @Override
    public Object IncrementKey(String storeName,String key, K field, int incr) {
        String value=getValue(storeName,key,field).toString();
        if(Utilities.isDouble(value)){
            return Utilities.getDouble(value, 0);
        }else if(Utilities.isInteger(value)){
            return Utilities.getInt(value, 0);
        }else return 0;
        
    }
/*
    @Override
    public void removeValue(String storeName,String key, K field) {        
        ConcurrentHashMap<K, V> temp=store.get(storeName).get(key);
        temp.remove(field);
    }
*/
    @Override
    public V getValue(String storeName,String key, K field) {
          if (store.get(storeName)==null||store.get(storeName).get(key) == null) {
            return null;
        } else {
            return store.get(storeName).get(key).get(field);
        }
    }

    @Override
    public Long setHash(String storeName,String key, K field, V value) {
        if(store.get(storeName).containsKey(key)){
            store.get(storeName).get(key).put(field, value);
            return 1L;
        }else{
            ConcurrentHashMap<K, V> temp=new ConcurrentHashMap<>();
            temp.put(field, value);
            store.get(storeName).put(key, temp);
            return 1L;
        }        
    }

    @Override
    public ConcurrentHashMap<K, V> getValues(String storeName,String Key) {
        return store.get(storeName).get(Key);
    }    

    @Override
    public Set<String> getKeys(String storeName) {
        Set<String>out=new HashSet<>();
        if(store.get(storeName)!=null){
            out= store.get(storeName).keySet();
        }
        return out;
    }

    @Override
    public void rename(String oldStoreName,String newStoreName, String oldKeyName, String newKeyName) {
        ConcurrentHashMap<K,V> old= store.get(oldStoreName).get(oldKeyName);
        store.get(oldStoreName).remove(oldKeyName);
        store.get(newStoreName).put(newKeyName, old);
    }

    @Override
    public List<String> blpop(String storeName, String key, int duration) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<String> brpop(String storeName, String key, int duration) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<String> lrange(String storeName, String key, int start, int end) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void rename(String storeName, String newStoreName) {
       throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
