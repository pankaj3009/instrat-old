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
    int id;
    OrderEvent e;
    double ticksize=0.05;
    EnumOrderSide side;
    double limitprice;
    ExecutionManager oms;
    boolean orderCompleted=false;
    final Object syncObject=new Object();
    private static final Logger logger = Logger.getLogger(OrderTypeRel.class.getName());
    
    public OrderTypeRel(int id, BeanConnection c,OrderEvent event,double ticksize,ExecutionManager oms){
        this.c=c;
        this.id=id;
        this.e=event;
        side=event.getSide();
        limitprice=event.getLimitPrice();
        this.ticksize=ticksize;
        this.oms=oms;
        
    }
    
    @Override
    public void run() {
        Subscribe.tes.addBidAskListener(this);
        synchronized(syncObject){
            try {
                syncObject.wait();
                logger.log(Level.INFO,"OrderTypeRel: Notified execution completion");
                Subscribe.tes.removeBidAskListener(this);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void bidaskChanged(BidAskEvent event) {

        switch(side){
            case BUY:
            case COVER:
                double bidprice=Parameters.symbol.get(id).getBidPrice();
                if(bidprice>limitprice){
                    limitprice=bidprice+ticksize;
                    e.setLimitPrice(limitprice);
                    e.setOrderStage(EnumOrderStage.AMEND);
                    e.setAccount(c.getAccountName());
                    e.setTag("BIDASKCHANGED");
                    oms.orderReceived(e);
                }
                break;
            case SHORT:
            case SELL:
                double askprice=Parameters.symbol.get(id).getAskPrice();
                if(askprice>0 && askprice<limitprice){
                    limitprice=askprice-ticksize;
                    e.setLimitPrice(limitprice);
                    e.setOrderStage(EnumOrderStage.AMEND);
                    e.setAccount(c.getAccountName());
                    e.setTag("BIDASKCHANGED");
                    oms.orderReceived(e);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        OrderBean ob=c.getOrders().get(event.getOrderID());
        logger.log(Level.INFO,"OrderTypeRel : OrderID:{0},OrderID from ob:{1}, Remaining{2}",new Object[]{event.getOrderID(),ob.getOrderID()});
            if(event.getOrderID()==ob.getOrderID()){
            logger.log(Level.INFO,"Match OrderTypeRel : InternalOrderID:{0},Remaining{1}",new Object[]{event.getOrderID(),event.getRemaining()});
            if(event.getRemaining()==0){
                logger.log(Level.INFO,"OrderTypeRel: Waiting for lock");
                synchronized(syncObject){
                logger.log(Level.INFO,"OrderTypeRel: Lock obtained");
                syncObject.notify();                   
                }
            }
        }
    }
    
    
    
}
