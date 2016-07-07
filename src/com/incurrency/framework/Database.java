/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author admin
 */
public interface Database<K,V> {
    
    public Long delKey(String storeName,String key);
    public void rename(String oldStoreName,String newStoreName,String oldKeyName,String newKeyName);
    public Set<String> getKeys(String storeName);
    public Object IncrementKey(String StoreName,String key,K field,int incr);
    //public Long setList(J key, Object value);
    //public Long setSet(String key, String value);
    public Long setHash (String StoreName,String key, K field, V value);
    //public void removeValue(String StoreName,String key, K field);
    //public String setMMap(String key,String[]field,Object[]value);
    //public void removeMValue(String key, String[]field);
    //public String getKey(String key);
    //public Long getLength(String key);
    //public List<String> getList(String key);
    //public String getFirstListValue(String key);
    //public String getLastListValue(String key);
    //public Long addListElementAtEnd(String key, Object value);
    //public Long removeElementFromList(String key,Object value);
    //public Long getListLength(String key);
    //public Boolean isSetMember(String key,String value);
    public V getValue(String storeName,String key, K field);
    public ConcurrentHashMap<K,V> getValues(String storeName,String Key);
    public List<String> blpop(String storeName,String key, int duration);
    public List<String> brpop(String storeName,String key,int duration);
    public List<String> lrange(String storeName,String key,int start, int end);
    //public int loadVariables();
    //public int saveVariables();
    //public Set<String> getKeys(String pattern);
    //public Boolean clearKeys(Set<String> keys);
    //public void copy(String sourceKey,String targetKey,String[] exclusions);
    
}
