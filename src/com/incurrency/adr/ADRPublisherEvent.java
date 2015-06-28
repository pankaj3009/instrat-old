/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.incurrency.adr;

import java.util.logging.Logger;

/**
 *
 * @author Admin
 */
public class ADRPublisherEvent {
    private int field = 0;
    private double price = 0.0;
    private static final Logger logger = Logger.getLogger(ADRPublisherEvent.class.getName());

    /*
     * Constructor
     */
    public ADRPublisherEvent(int type, double price)
    {
        field = type;
        this.price = price;
    }
    
   
    public double getPrice() {return price;};

    public int getField() {return field;}
}
