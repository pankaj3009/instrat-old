/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.Subscribe;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class OrderTypeRel implements Runnable, BidAskListener, OrderStatusListener {

    BeanConnection c;
    int id;
    int underlyingid = -1;
    OrderEvent e;
    double ticksize = 0.05;
    EnumOrderSide side;
    private double limitPrice;
    ExecutionManager oms;
    boolean orderCompleted = false;
    final Object syncObject = new Object();
    private static final Logger logger = Logger.getLogger(OrderTypeRel.class.getName());

    public OrderTypeRel(int id, BeanConnection c, OrderEvent event, double ticksize, ExecutionManager oms) {
        try{
        this.c = c;
        this.id = id;
        //We need underlyingid, if we are doing options.
        //As there are only two possibilities for underlying(as of now), we test for both.
        if (Parameters.symbol.get(id).getType().equals("OPT")) {
            String expiry = Parameters.symbol.get(id).getExpiry();
            int symbolid = Utilities.getReferenceID(Parameters.symbol, id, "IND");
            if (symbolid == -1) {
                symbolid = Utilities.getReferenceID(Parameters.symbol, id, "STK");
            }
            underlyingid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, symbolid, expiry);
        }
        this.e = event;
        side = event.getSide();
        limitPrice = event.getLimitPrice();
        this.ticksize = ticksize;
        this.oms = oms;
        }catch (Exception evt){
            logger.log(Level.SEVERE,null,evt);
        }
    }

    @Override
    public void run() {
        try{
        Subscribe.tes.addBidAskListener(this);
        Subscribe.tes.addOrderStatusListener(this);
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addOrderStatusListener(this);
            c.getWrapper().addBidAskListener(this);
        }
        MainAlgorithm.tes.addBidAskListener(this);
        synchronized (syncObject) {
            try {
                syncObject.wait();
                logger.log(Level.INFO, "OrderTypeRel: Closing Manager");
                Subscribe.tes.removeBidAskListener(this);
                Subscribe.tes.removeOrderStatusListener(this);
                for (BeanConnection c : Parameters.connection) {
                    c.getWrapper().removeOrderStatusListener(this);
                    c.getWrapper().removeBidAskListener(this);
                }
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
        }catch (Exception e){
            logger.log(Level.SEVERE,null,e);
        }
    }

    @Override
    public void bidaskChanged(BidAskEvent event) {
        try{
            boolean fatfinger=false;
        if (event.getSymbolID() == id||event.getSymbolID()==underlyingid) {
            double tmpLimitPrice = limitPrice;
            switch (side) {
                case BUY:
                case COVER:
                    double bidPrice = Parameters.symbol.get(id).getBidPrice();
                    switch (Parameters.symbol.get(id).getType()) {
                        case "OPT":
                            Parameters.symbol.get(id).getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                            if (Parameters.symbol.get(id).getOptionProcess() == null) {
                                String strike = Parameters.symbol.get(id).getOption();
                                String right = Parameters.symbol.get(id).getRight();
                                String expiry = Parameters.symbol.get(id).getExpiry();
                                Parameters.symbol.get(id).SetOptionProcess(expiry, right, strike);
                            }
                            double calculatedPrice = Parameters.symbol.get(id).getOptionProcess().NPV();
                            calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);

                            /*
                             * BP - LP - CP : Do Nothing. We are the best bid
                             * LP - BP - CP : Change to Best Bid
                             * BP - CP - LP : Change to Best Bid
                             * CP - BP - LP : Second Best Bid
                             * LP - CP - BP : Second Best Bid
                             * CP - LP - BP : Second Best Bid
                             * Fat Finger protection
                             * BP-CP / CP > +5% stay at CP.
                             */

                            if (bidPrice == 0 || (bidPrice - calculatedPrice) / (calculatedPrice) > 0.05) {
                                fatfinger = true;
                                if (Math.abs(limitPrice - (calculatedPrice - 10 * ticksize)) < 10 * ticksize) {
                                    //To prevent frequent orders when we are not near market, we limit updates only if
                                    //new limitprice is off by 10 ticksize.
                                    tmpLimitPrice = calculatedPrice - 10 * ticksize;

                                }
                            }
                            if (!fatfinger) {
                                if ((limitPrice <= bidPrice && bidPrice >= calculatedPrice)
                                        || (bidPrice <= calculatedPrice && calculatedPrice <= limitPrice)) {
                                    //Change to Best Bid
                                    tmpLimitPrice = bidPrice + ticksize;
                                } else if ((calculatedPrice <= bidPrice && bidPrice <= limitPrice)
                                        || (limitPrice <= calculatedPrice && calculatedPrice <= bidPrice)
                                        || (calculatedPrice <= limitPrice && limitPrice <= bidPrice)) {
                                    //Change to second best ask
                                    tmpLimitPrice = bidPrice - ticksize;
                                }
                            }
                            if (tmpLimitPrice != limitPrice) {
                                e.setLimitPrice(tmpLimitPrice);
                                e.setOrderStage(EnumOrderStage.AMEND);
                                e.setAccount(c.getAccountName());
                                e.setTag("BIDASKCHANGED");
                                logger.log(Level.INFO, "OrderTypeRel, Symbol:{0}, Side:{1}, CalculatedOptionPrice:{2}, CurrentLimitPriceWithBroker:{3}, BidPrice:{4}, NewLimitPrice:{5}",
                                        new Object[]{Parameters.symbol.get(id).getDisplayname(), side, calculatedPrice, limitPrice, bidPrice, tmpLimitPrice});
                                oms.orderReceived(e);
                            }
                            break;
                        default:
                            if (bidPrice > 0 && bidPrice < limitPrice) {
                                tmpLimitPrice = bidPrice + ticksize;
                                e.setLimitPrice(tmpLimitPrice);
                                e.setOrderStage(EnumOrderStage.AMEND);
                                e.setAccount(c.getAccountName());
                                e.setTag("BIDASKCHANGED");
                                oms.orderReceived(e);
                            }
                            break;
                    }
                    break;
                case SHORT:
                case SELL:
                    double askPrice = Parameters.symbol.get(id).getAskPrice();
                    switch (Parameters.symbol.get(id).getType()) {
                        case "OPT":
                            Parameters.symbol.get(id).getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                            if (Parameters.symbol.get(id).getOptionProcess() == null) {
                                String strike = Parameters.symbol.get(id).getOption();
                                String right = Parameters.symbol.get(id).getRight();
                                String expiry = Parameters.symbol.get(id).getExpiry();
                                Parameters.symbol.get(id).SetOptionProcess(expiry, right, strike);
                            }
                            double calculatedPrice = Parameters.symbol.get(id).getOptionProcess().NPV();
                            calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);

                            /*
                             * CP - LP - AP : Do Nothing. We are the best ask
                             * CP - AP - LP : Change to Best Ask
                             * LP - CP - AP : Change to Best Ask
                             * LP - AP - CP : Second Best Ask
                             * AP - CP - LP : Second Best Ask
                             * AP - LP - CP : Second Best Ask
                             * Fat Finger protection
                             * CP - AP / CP > +5% stay at CP.
                             */
                            
                             if (askPrice == 0 || (calculatedPrice - askPrice) / (calculatedPrice) > 0.05) {
                                fatfinger = true;
                                if (Math.abs(limitPrice - (calculatedPrice - 10 * ticksize)) < 10 * ticksize) {
                                    //To prevent frequent orders when we are not near market, we limit updates only if
                                    //new limitprice is off by 10 ticksize.
                                    tmpLimitPrice = calculatedPrice + 10 * ticksize;

                                }
                            }
                            if (!fatfinger) {
                                if ((calculatedPrice <= askPrice && askPrice <= limitPrice)
                                        || (limitPrice <= calculatedPrice && calculatedPrice <= askPrice)) {
                                    //Change to Best Ask
                                    tmpLimitPrice = askPrice - ticksize;
                                } else if ((limitPrice <= askPrice && askPrice <= calculatedPrice)
                                        || (askPrice <= calculatedPrice && calculatedPrice <= limitPrice)
                                        || (askPrice <= limitPrice && limitPrice <= calculatedPrice)) {
                                    //Change to second best ask
                                    tmpLimitPrice = askPrice + ticksize;
                                }
                            }
                            if (tmpLimitPrice != limitPrice) {
                                e.setLimitPrice(tmpLimitPrice);
                                e.setOrderStage(EnumOrderStage.AMEND);
                                e.setAccount(c.getAccountName());
                                e.setTag("BIDASKCHANGED");
                                logger.log(Level.INFO, "OrderTypeRel, Symbol:{0}, Side:{1}, CalculatedOptionPrice:{2}, CurrentLimitPriceWithBroker:{3}, AskPrice:{4}, NewLimitPrice:{5}",
                                        new Object[]{Parameters.symbol.get(id).getDisplayname(), side, calculatedPrice, limitPrice, askPrice,tmpLimitPrice});
                                oms.orderReceived(e);
                            }
                            break;
                        default:
                            if (askPrice > 0 && askPrice < limitPrice) {
                                tmpLimitPrice = askPrice - ticksize;
                                e.setLimitPrice(tmpLimitPrice);
                                e.setOrderStage(EnumOrderStage.AMEND);
                                e.setAccount(c.getAccountName());
                                e.setTag("BIDASKCHANGED");
                                oms.orderReceived(e);
                            }
                            break;
                    }
                    break;
                default:
                    break;
            }
        }
        }catch(Exception e){
            logger.log(Level.SEVERE,null,e);
        }
    }

    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        OrderBean ob = c.getOrders().get(event.getOrderID());
         if (this.c.equals(event.getC()) && event.getOrderID() == ob.getOrderID()) {
            this.limitPrice = ob.getParentLimitPrice();
             if (event.getRemaining() == 0) {
                synchronized (syncObject) {
                        syncObject.notify();
                }
            }
        }
    }
}
