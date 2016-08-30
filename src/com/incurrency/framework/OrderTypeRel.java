/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.Subscribe;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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
    int externalOrderID = -1;
    int internalOrderIDEntry=-1;
    //private final Object syncObject = new Object();
    private Drop sync;
    private LimitedQueue recentOrders;
    private static final Logger logger = Logger.getLogger(OrderTypeRel.class.getName());
    boolean recalculate=false;
    SimpleDateFormat loggingFormat=new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
    

    public OrderTypeRel(int id, int orderid,BeanConnection c, OrderEvent event, double ticksize, ExecutionManager oms) {
        try {
            sync=new Drop();
            this.c = c;
            this.id = id;
            this.externalOrderID=orderid;
            recentOrders = new LimitedQueue(10);
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
        } catch (Exception evt) {
            logger.log(Level.SEVERE, null, evt);
        }
    }

    @Override
    public void run() {
        try {
            Subscribe.tes.addBidAskListener(this);
            Subscribe.tes.addOrderStatusListener(this);
            for (BeanConnection c : Parameters.connection) {
                c.getWrapper().addOrderStatusListener(this);
                c.getWrapper().addBidAskListener(this);
            }
            MainAlgorithm.tes.addBidAskListener(this);
            // synchronized (syncObject) {
            try {
                sync.take();
                logger.log(Level.INFO, "OrderTypeRel: Closing Manager for " + Parameters.symbol.get(id).getDisplayname());
                if(Trade.getAccountName(oms.getDb(),"opentrades_"+oms.orderReference+":"+internalOrderIDEntry+":"+c.getAccountName()).equals("")){
                    oms.getDb().delKey("opentrades", oms.orderReference+":"+internalOrderIDEntry+":"+c.getAccountName());
                }
                Subscribe.tes.removeBidAskListener(this);
                Subscribe.tes.removeOrderStatusListener(this);
                for (BeanConnection c : Parameters.connection) {
                    c.getWrapper().removeOrderStatusListener(this);
                    c.getWrapper().removeBidAskListener(this);
                }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            //}
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void bidaskChanged(BidAskEvent event) {
        try {
            boolean fatfinger = false;
            if (event.getSymbolID() == id || event.getSymbolID() == underlyingid) {
                //check if there is a case for updating rel price. Only time criteron at present.
                if (recentOrders.size() == 10 && (new Date().getTime() - (Long) recentOrders.get(0)) < 120000) {// More than 10 orders in last 2 minutes
                    recalculate=true;
                } else {
                    OrderBean ob = c.getOrders().get(externalOrderID);
                    if (ob != null) {
                        internalOrderIDEntry=ob.getInternalOrderIDEntry();
                        limitPrice = ob.getParentLimitPrice();
                        double tmpLimitPrice = limitPrice;
                        switch (side) {
                            case BUY:
                            case COVER:
                        if(recalculate){
                            limitPrice=Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, Parameters.symbol.get(id).getUnderlyingID(), EnumOrderSide.BUY, Parameters.symbol.get(id).getRight(), ticksize);
                            tmpLimitPrice = limitPrice;
                            logger.log(Level.INFO,"{0},{1},{2},{3},{4},Recalculated Limit Price at {5}",new Object[]
                            {oms.getS().getStrategy(),c.getAccountName(),Parameters.symbol.get(id).getDisplayname(),
                            ob.getParentInternalOrderID(),ob.getOrderID(),limitPrice});
                            recalculate=false;
                        }
                        double bidPrice = Parameters.symbol.get(id).getBidPrice();
                        double askPrice = Parameters.symbol.get(id).getAskPrice();                                switch (Parameters.symbol.get(id).getType()) {
                                    case "OPT":
                                        Parameters.symbol.get(id).getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
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
                                            tmpLimitPrice=calculatedPrice;
                                            if (Math.abs(limitPrice - (calculatedPrice - 10 * ticksize)) < 10 * ticksize) {
                                                //To prevent frequent orders when we are not near market, we limit updates only if
                                                //new limitprice is off by 10 ticksize.
                                                tmpLimitPrice = calculatedPrice - 10 * ticksize;

                                            }
                                        }
                                        if (!fatfinger) {
                                            if ((limitPrice <= bidPrice && bidPrice <= calculatedPrice)
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
                                        if (tmpLimitPrice != limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED && bidPrice > limitPrice) {
                                            recentOrders.add(new Date().getTime());
                                            e.setLimitPrice(tmpLimitPrice);
                                            e.setOrderStage(EnumOrderStage.AMEND);
                                            e.setAccount(c.getAccountName());
                                            e.setTag("BIDASKCHANGED");
                                            String log="Side:"+side+",Calculated Price:"+calculatedPrice+",LimitPrice:"+limitPrice+",BidPrice:"+bidPrice+",AskPrice:"+askPrice+",New Limit Price:"+tmpLimitPrice+",Current Order Status:"+ob.getChildStatus();
                                            oms.getDb().setHash("opentrades",oms.orderReference+":"+ob.getInternalOrderIDEntry()+":"+c.getAccountName(),loggingFormat.format(new Date()),log);

                                            logger.log(Level.INFO, "{0},{1},{2},{3},{4}, 201,OrderTypeRel, Side:{5}, CalculatedOptionPrice:{6}, CurrentLimitPriceWithBroker:{7}, BidPrice:{8}, NewLimitPrice:{9}, OrderStatus:{10}",
                                                    new Object[]{oms.getS().getStrategy(), c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), ob.getInternalOrderID(), ob.getOrderID(),
                                                side, calculatedPrice, limitPrice, bidPrice, tmpLimitPrice, ob.getChildStatus()});
                                            oms.orderReceived(e);
                                        }
                                        break;
                                    default:
                                        if (bidPrice > 0 && bidPrice > limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED) {
                                            tmpLimitPrice = bidPrice + ticksize;
                                            recentOrders.add(new Date().getTime());
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
                                bidPrice = Parameters.symbol.get(id).getAskPrice();
                                askPrice = Parameters.symbol.get(id).getAskPrice();
                                switch (Parameters.symbol.get(id).getType()) {
                                    case "OPT":
                                        Parameters.symbol.get(id).getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                                        if (Parameters.symbol.get(id).getOptionProcess() == null) {
                                            Parameters.symbol.get(id).SetOptionProcess();
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
                                            tmpLimitPrice=calculatedPrice;
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
                                        if (tmpLimitPrice != limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED && limitPrice > askPrice) {
                                            recentOrders.add(new Date().getTime());
                                            e.setLimitPrice(tmpLimitPrice);
                                            e.setOrderStage(EnumOrderStage.AMEND);
                                            e.setAccount(c.getAccountName());
                                            e.setTag("BIDASKCHANGED");
                                            String log="Side:"+side+",Calculated Price:"+calculatedPrice+",LimitPrice:"+limitPrice+",BidPrice:"+bidPrice+",AskPrice:"+askPrice+",New Limit Price:"+tmpLimitPrice+",Current Order Status:"+ob.getChildStatus();
                                            oms.getDb().setHash("opentrades",oms.orderReference+":"+ob.getInternalOrderIDEntry()+":"+c.getAccountName(),loggingFormat.format(new Date()),log);
                                            logger.log(Level.INFO, "{0},{1},{2},{3},{4}, 201,OrderTypeRel, Side:{5}, CalculatedOptionPrice:{6}, CurrentLimitPriceWithBroker:{7}, AskPrice:{8}, NewLimitPrice:{9},OrderStatus:{10}",
                                                    new Object[]{oms.getS().getStrategy(), c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), ob.getInternalOrderID(), externalOrderID,
                                                side, calculatedPrice, limitPrice, askPrice, tmpLimitPrice, ob.getChildStatus()});
                                            oms.orderReceived(e);
                                        }
                                        break;
                                    default:
                                        if (askPrice > 0 && askPrice < limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED) {
                                            recentOrders.add(new Date().getTime());
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
                }

            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        OrderBean ob = c.getOrders().get(event.getOrderID());
        if (ob != null) {
            if (this.c.equals(event.getC()) && event.getOrderID() == externalOrderID && (ob.getParentSymbolID()-1)==id) {
                if (event.getRemaining() == 0 ||event.getStatus().equals("Cancelled")) {
                    //synchronized (syncObject) {
                    this.sync.put("FINISHED");
//                    syncObject.notify();
                    //}
                }
            }
        }
    }
}
