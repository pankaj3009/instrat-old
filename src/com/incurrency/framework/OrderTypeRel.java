/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.ib.client.Contract;
import com.ib.client.Order;
import com.incurrency.RatesClient.Subscribe;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class OrderTypeRel implements Runnable,BidAskListener,OrderStatusListener {

    BeanConnection c;
    BeanSymbol s;
    Contract cont;
    Order o;
    double tickSize;
    boolean orderCompleted=false;
    final Object syncObject=new Object();
    private static final Logger logger = Logger.getLogger(OrderTypeRel.class.getName());
    
    public OrderTypeRel(BeanConnection c, BeanSymbol s,Contract cont, Order o,double tickSize){
        this.c=c;
        this.s=s;
        this.cont=cont;
        this.o=o;
        this.tickSize=tickSize;
    }
    
    @Override
    public void run() {
        Subscribe.tes.addBidAskListener(this);
        synchronized(syncObject){
            try {
                syncObject.wait();
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void bidaskChanged(BidAskEvent event) {
        double limitPrice=o.m_lmtPrice;
        switch(o.m_action){
            case "BUY":
                if(s.getBidPrice()>limitPrice){
                    o.m_lmtPrice=s.getBidPrice()+tickSize;
                    c.getWrapper().eClientSocket.placeOrder(o.m_orderId, cont, o);
                }
                break;
            case "SELL":
                if(s.getAskPrice()<limitPrice){
                    o.m_lmtPrice=s.getAskPrice()-tickSize;
                    c.getWrapper().eClientSocket.placeOrder(o.m_orderId, cont, o);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        if(event.getOrderID()==o.m_orderId){
            if(event.getRemaining()==0){
                synchronized(syncObject){
                syncObject.notify();                   
                }
            }
        }
    }
    
    
    
}
