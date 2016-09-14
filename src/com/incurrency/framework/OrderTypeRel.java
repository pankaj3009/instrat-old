/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.Subscribe;
import java.text.SimpleDateFormat;
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
    int internalOrderIDEntry = -1;
    //private final Object syncObject = new Object();
    private Drop sync;
    private LimitedQueue recentOrders;
    private static final Logger logger = Logger.getLogger(OrderTypeRel.class.getName());
    boolean recalculate = false;
    SimpleDateFormat loggingFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
    int orderspermin = 1;
    double worseamt = 0;
    double improveprob = 1;
    double improveamt = 0;
    int fatFingerWindow=120; //in seconds
    long fatFingerStart=Long.MAX_VALUE;
    

    public OrderTypeRel(int id, int orderid, BeanConnection c, OrderEvent event, double ticksize, ExecutionManager oms) {
        try {
            sync = new Drop();
            this.c = c;
            this.id = id;
            this.externalOrderID = orderid;
            this.ticksize = ticksize;
            orderspermin = Utilities.getInt(event.getOrderAttributes().get("orderspermin"), 1);
            improveprob = Utilities.getDouble(event.getOrderAttributes().get("improveprob"), 1);
            improveamt = Utilities.getInt(event.getOrderAttributes().get("improveamt"), 0)*this.ticksize;
            fatFingerWindow = Utilities.getInt(event.getOrderAttributes().get("fatfingerwindow"), 120);
            recentOrders = new LimitedQueue(orderspermin);
//We need underlyingid, if we are doing options.
            //As there are only two possibilities for underlying(as of now), we test for both.
            if (Parameters.symbol.get(id).getType().equals("OPT")) {
                String expiry = Parameters.symbol.get(id).getExpiry();
                int symbolid = Utilities.getCashReferenceID(Parameters.symbol, id, "IND");
                if (symbolid == -1) {
                    symbolid = Utilities.getCashReferenceID(Parameters.symbol, id, "STK");
                }
                underlyingid = Utilities.getFutureIDFromBrokerSymbol(Parameters.symbol, symbolid, expiry);
            }
            this.e = event;
            side = event.getSide();
            limitPrice = event.getLimitPrice();
            this.oms = oms;
        } catch (Exception evt) {
            logger.log(Level.SEVERE, null, evt);
        }
    }

    @Override
    public void run() {
        try {
            logger.log(Level.INFO,"OrderTypeRel: Manager Created for symbol {0}with initial limit price {1}",new Object[]{Parameters.symbol.get(id).getDisplayname(),limitPrice});
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
                if (Trade.getAccountName(oms.getDb(), "opentrades_" + oms.orderReference + ":" + internalOrderIDEntry + ":" + c.getAccountName()).equals("")) {
                    oms.getDb().delKey("opentrades", oms.orderReference + ":" + internalOrderIDEntry + ":" + c.getAccountName());
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
                if (recentOrders.size() == orderspermin && (new Date().getTime() - (Long) recentOrders.get(0)) > 60000) {// Timestamp of the first of the "n" orders is more than 60 seconds earlier
                    recalculate = true;
                } 
                    OrderBean ob = c.getOrders().get(externalOrderID);
                    if (ob != null) {
                        internalOrderIDEntry = ob.getInternalOrderIDEntry();
                        limitPrice = ob.getParentLimitPrice();
                        double tmpLimitPrice = limitPrice;
                        switch (side) {
                            case BUY:
                            case COVER:
                                if (recalculate) {
                                    if(Parameters.symbol.get(id).getType().equals("OPT")){
                                    limitPrice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, Parameters.symbol.get(id).getUnderlyingID(), EnumOrderSide.BUY, Parameters.symbol.get(id).getRight(), ticksize);
                                    tmpLimitPrice = limitPrice;
                                    logger.log(Level.INFO, "{0},{1},{2},{3},{4},Recalculated Limit Price at {5}", new Object[]{oms.getS().getStrategy(), c.getAccountName(), Parameters.symbol.get(id).getDisplayname(),
                                        ob.getParentInternalOrderID(), ob.getOrderID(), limitPrice});
                                    }
                                    recalculate = false;
                                }
                                double bidPrice = Parameters.symbol.get(id).getBidPrice();
                                double askPrice = Parameters.symbol.get(id).getAskPrice();
                                switch (Parameters.symbol.get(id).getType()) {
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
                                            if(fatFingerStart==Long.MAX_VALUE){
                                                fatFingerStart=new Date().getTime();
                                            }
                                            tmpLimitPrice = calculatedPrice;
                                            if (Math.abs(limitPrice - (calculatedPrice - 10 * ticksize)) < 10 * ticksize) {
                                                //To prevent frequent orders when we are not near market, we limit updates only if
                                                //new limitprice is off by 10 ticksize.
                                                tmpLimitPrice = calculatedPrice - 10 * ticksize;
                                                
                                            }
                                        }
                                        if(!fatfinger){
                                            fatFingerStart=Long.MAX_VALUE;
                                        }
                                        
                                        if (!fatfinger || ((new Date().getTime()-fatFingerStart)>fatFingerWindow*1000 && bidPrice>0)) {
                                            if ((limitPrice <= bidPrice && bidPrice <= calculatedPrice)
                                                    || (bidPrice <= calculatedPrice && calculatedPrice <= limitPrice)) {
                                                //Change to Best Bid
                                                tmpLimitPrice = bidPrice + improveamt;
                                            } else if ((calculatedPrice <= bidPrice && bidPrice <= limitPrice)
                                                    || (limitPrice <= calculatedPrice && calculatedPrice <= bidPrice)
                                                    || (calculatedPrice <= limitPrice && limitPrice <= bidPrice)) {
                                                //Change to second best ask
                                                tmpLimitPrice = bidPrice - Math.abs(improveamt);
                                            }
                                        }
                                        if (tmpLimitPrice != limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED && bidPrice > limitPrice) {
                                            double random=Math.random();
                                            if (random> improveprob) {//no improvement, therefore worsen price
                                                tmpLimitPrice = bidPrice - Math.abs(improveamt);
                                            }
                                            recentOrders.add(new Date().getTime());
                                            e.setLimitPrice(tmpLimitPrice);
                                            e.setOrderStage(EnumOrderStage.AMEND);
                                            e.setAccount(c.getAccountName());
                                            e.setTag("BIDASKCHANGED");
                                            String log = "Side:" + side + ",Calculated Price:" + calculatedPrice + ",LimitPrice:" + limitPrice + ",BidPrice:" + bidPrice + ",AskPrice:" + askPrice + ",New Limit Price:" + tmpLimitPrice + ",Current Order Status:" + ob.getChildStatus()+",Random:"+random;
                                            oms.getDb().setHash("opentrades", oms.orderReference + ":" + ob.getInternalOrderIDEntry() + ":" + c.getAccountName(), loggingFormat.format(new Date()), log);

                                            logger.log(Level.INFO, "{0},{1},{2},{3},{4}, 201,OrderTypeRel, Side:{5}, CalculatedOptionPrice:{6}, CurrentLimitPriceWithBroker:{7}, BidPrice:{8}, NewLimitPrice:{9}, OrderStatus:{10}",
                                                    new Object[]{oms.getS().getStrategy(), c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), ob.getInternalOrderID(), ob.getOrderID(),
                                                side, calculatedPrice, limitPrice, bidPrice, tmpLimitPrice, ob.getChildStatus()});
                                            oms.orderReceived(e);

                                        }
                                        break;
                                    default:
                                        if (bidPrice > 0 && bidPrice > limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED) {
                                            double random=Math.random();
                                            if (random < improveprob) {
                                                tmpLimitPrice = bidPrice + improveamt;
                                            } else {
                                                tmpLimitPrice = bidPrice - Math.abs(improveamt);
                                            }
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
                                if (recalculate) {
                                    if(Parameters.symbol.get(id).getType().equals("OPT")){
                                    limitPrice = Utilities.getOptionLimitPriceForRel(Parameters.symbol, id, Parameters.symbol.get(id).getUnderlyingID(), EnumOrderSide.BUY, Parameters.symbol.get(id).getRight(), ticksize);
                                    tmpLimitPrice = limitPrice;
                                    logger.log(Level.INFO, "{0},{1},{2},{3},{4},Recalculated Limit Price at {5}", new Object[]{oms.getS().getStrategy(), c.getAccountName(), Parameters.symbol.get(id).getDisplayname(),
                                        ob.getParentInternalOrderID(), ob.getOrderID(), limitPrice});
                                    }
                                    recalculate = false;
                                }
                                bidPrice = Parameters.symbol.get(id).getBidPrice();
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
                                            if (fatFingerStart == Long.MAX_VALUE) {
                                                fatFingerStart = new Date().getTime();
                                            }
                                            tmpLimitPrice = calculatedPrice;
                                            if (Math.abs(limitPrice - (calculatedPrice - 10 * ticksize)) < 10 * ticksize) {
                                                //To prevent frequent orders when we are not near market, we limit updates only if
                                                //new limitprice is off by 10 ticksize.
                                                tmpLimitPrice = calculatedPrice + 10 * ticksize;
                                            }
                                        }

                                        if (!fatfinger) {
                                            fatFingerStart = Long.MAX_VALUE;
                                        }
                                        
                                        if (!fatfinger || ((new Date().getTime()-fatFingerStart)>fatFingerWindow*1000 && askPrice>0)) {
                                            if ((calculatedPrice <= askPrice && askPrice <= limitPrice)
                                                    || (limitPrice <= calculatedPrice && calculatedPrice <= askPrice)) {
                                                //Change to Best Ask
                                                tmpLimitPrice = askPrice - improveamt;
                                            } else if ((limitPrice <= askPrice && askPrice <= calculatedPrice)
                                                    || (askPrice <= calculatedPrice && calculatedPrice <= limitPrice)
                                                    || (askPrice <= limitPrice && limitPrice <= calculatedPrice)) {
                                                //Change to second best ask
                                                tmpLimitPrice = askPrice + Math.abs(improveamt);
                                            }
                                        }
                                        if (tmpLimitPrice != limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED && limitPrice > askPrice) {
                                            double random=Math.random();
                                            if (random > improveprob) {
                                                tmpLimitPrice = askPrice + Math.abs(improveamt);
                                            }
                                            recentOrders.add(new Date().getTime());
                                            e.setLimitPrice(tmpLimitPrice);
                                            e.setOrderStage(EnumOrderStage.AMEND);
                                            e.setAccount(c.getAccountName());
                                            e.setTag("BIDASKCHANGED");
                                            String log = "Side:" + side + ",Calculated Price:" + calculatedPrice + ",LimitPrice:" + limitPrice + ",BidPrice:" + bidPrice + ",AskPrice:" + askPrice + ",New Limit Price:" + tmpLimitPrice + ",Current Order Status:" + ob.getChildStatus()+",Random:"+random;
                                            oms.getDb().setHash("opentrades", oms.orderReference + ":" + ob.getInternalOrderIDEntry() + ":" + c.getAccountName(), loggingFormat.format(new Date()), log);
                                            logger.log(Level.INFO, "{0},{1},{2},{3},{4}, 201,OrderTypeRel, Side:{5}, CalculatedOptionPrice:{6}, CurrentLimitPriceWithBroker:{7}, AskPrice:{8}, NewLimitPrice:{9},OrderStatus:{10}",
                                                    new Object[]{oms.getS().getStrategy(), c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), ob.getInternalOrderID(), externalOrderID,
                                                side, calculatedPrice, limitPrice, askPrice, tmpLimitPrice, ob.getChildStatus()});
                                            oms.orderReceived(e);
                                        }
                                        break;
                                    default:
                                        if (askPrice > 0 && askPrice < limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED) {
                                            double random=Math.random();
                                            if (random < improveprob) {
                                                tmpLimitPrice = askPrice - improveamt;
                                            } else {
                                                tmpLimitPrice = askPrice + Math.abs(improveamt);
                                            }
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
                            default:
                                break;
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
            if (this.c.equals(event.getC()) && event.getOrderID() == externalOrderID && (ob.getParentSymbolID() - 1) == id) {
                if (event.getRemaining() == 0 || event.getStatus().equals("Cancelled")) {
                    //synchronized (syncObject) {
                    this.sync.put("FINISHED");
//                    syncObject.notify();
                    //}
                }
            }
        }
    }
}
