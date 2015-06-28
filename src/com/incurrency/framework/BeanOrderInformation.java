/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.Serializable;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class BeanOrderInformation implements Serializable {

    private int symbolid;
    private BeanConnection c;
    private int orderID;
    private long expireTime;
    private OrderEvent origEvent;
    private final static Logger logger = Logger.getLogger(BeanOrderInformation.class.getName());

    public BeanOrderInformation(int id, BeanConnection c, int orderid, long expiretime, OrderEvent event) {
        this.symbolid = id;
        this.c = c;
        this.orderID = orderid;
        this.expireTime = expiretime;
        this.origEvent = event;
    }

    /**
     * @return the symbolid
     */
    public int getSymbolid() {
        return symbolid;
    }

    /**
     * @param symbolid the symbolid to set
     */
    public void setSymbolid(int symbolid) {
        this.symbolid = symbolid;
    }

    /**
     * @return the c
     */
    public BeanConnection getC() {
        return c;
    }

    /**
     * @param c the c to set
     */
    public void setC(BeanConnection c) {
        this.c = c;
    }

    /**
     * @return the orderID
     */
    public int getOrderID() {
        return orderID;
    }

    /**
     * @param orderID the orderID to set
     */
    public void setOrderID(int orderID) {
        this.orderID = orderID;
    }

    /**
     * @return the expireTime
     */
    public long getExpireTime() {
        return expireTime;
    }

    /**
     * @param expireTime the expireTime to set
     */
    public void setExpireTime(long expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * @return the origEvent
     */
    public synchronized OrderEvent getOrigEvent() {
        return origEvent;
    }

    /**
     * @param origEvent the origEvent to set
     */
    public synchronized void setOrigEvent(OrderEvent origEvent) {
        this.origEvent = origEvent;
    }
}
