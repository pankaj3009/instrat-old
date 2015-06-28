/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.adr;

import com.incurrency.framework.Parameters;
import com.incurrency.framework.TradeEvent;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class ADRPublisherTradeReceived implements Runnable {

    ADRPublisher s;
    TradeEvent event;
    private static final Logger logger = Logger.getLogger(ADRPublisherTradeReceived.class.getName());
    
    public ADRPublisherTradeReceived(ADRPublisher s, TradeEvent event){
        this.s=s;
        this.event=event;
    }
    
    @Override
    public void run() {
        s.processTradeReceived(event);
    }
    
}
