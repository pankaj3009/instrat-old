/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.ArrayList;

/**
 *
 * @author pankaj
 */
public class LinkedAction {
    BeanConnection c;
    int orderID;
    OrderEvent e;
    EnumLinkedAction action=EnumLinkedAction.UNDEFINED;
    int delay=0;
    public LinkedAction(BeanConnection c, int orderID, OrderEvent e,EnumLinkedAction action,int delaySeconds) {
        this.c = c;
        this.orderID = orderID;
        this.e = e;
        this.action=action;
        this.delay=delaySeconds;        
    }     
}
