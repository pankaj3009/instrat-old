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
public class ADRPublisherTickPriceEvent {
    private int tickerID = 0;
    private int field = 0;
    private double price = 0.0;
    private static final Logger logger = Logger.getLogger(ADRPublisherTickPriceEvent.class.getName());

    /*
     * Constructor
     */
    public ADRPublisherTickPriceEvent(int id, int type, double price)
    {
        tickerID = id;
        field = type;
        this.price = price;
    }
    
    public int getTickerID() {return tickerID;};
    
    public double getPrice() {return price;};

    public int getField() {return field;}
}
