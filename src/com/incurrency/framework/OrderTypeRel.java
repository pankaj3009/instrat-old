/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.RedisSubscribe;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class OrderTypeRel implements Runnable, BidAskListener, OrderStatusListener {

    private final String delimiter = "_";
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
//    private Drop sync;
    private LimitedQueue recentOrders;
    private static final Logger logger = Logger.getLogger(OrderTypeRel.class.getName());
    boolean recalculate = true;
    SimpleDateFormat loggingFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss.SSS");
    int orderspermin = 1;
    double worseamt = 0;
    double improveprob = 1;
    double improveamt = 0;
    int fatFingerWindow = 120; //in seconds
    long fatFingerStart = Long.MAX_VALUE;
    private boolean retracement = false;
    double plp = 0; //prior limit price
    int stickyperiod;
    private SynchronousQueue<String> sync = new SynchronousQueue<>();
    AtomicBoolean completed=new AtomicBoolean();
        
    public OrderTypeRel(int id, int orderid, BeanConnection c, OrderEvent event, double ticksize, ExecutionManager oms) {
        try {
            completed.set(Boolean.FALSE);
            this.c = c;
            this.id = id;
            this.externalOrderID = orderid;
            this.ticksize = ticksize;
            orderspermin = Utilities.getInt(event.getOrderAttributes().get("orderspermin"), 1);
            improveprob = Utilities.getDouble(event.getOrderAttributes().get("improveprob"), 1);
            improveamt = Utilities.getInt(event.getOrderAttributes().get("improveamt"), 0) * this.ticksize;
            fatFingerWindow = Utilities.getInt(event.getOrderAttributes().get("fatfingerwindow"), 120);
            stickyperiod = Utilities.getInt(event.getOrderAttributes().get("stickperiod"), 60);
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
            logger.log(Level.INFO, "501,OrderTypeRel Manager Created,{0}:{1}:{2}:{3}:{4},Initial Limit Price={5}",
                    new Object[]{oms.orderReference, c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), c.getOrders().get(externalOrderID).getInternalOrderID(), externalOrderID, e.getLimitPrice()});
            RedisSubscribe.tes.addBidAskListener(this);
            RedisSubscribe.tes.addOrderStatusListener(this);
            for (BeanConnection c1 : Parameters.connection) {
                c1.getWrapper().addOrderStatusListener(this);
                c1.getWrapper().addBidAskListener(this);
            }
            MainAlgorithm.tes.addBidAskListener(this);
            // synchronized (syncObject) {
            try {
            while(sync.poll(200, TimeUnit.MILLISECONDS)!=null){
                logger.log(Level.INFO, "501,OrderTypeRel Manager Closed,{0}:{1}:{2}:{3}:{4}",
                        new Object[]{oms.orderReference, c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), c.getOrders().get(externalOrderID).getInternalOrderID(), externalOrderID});
                if (Trade.getAccountName(oms.getDb(), "opentrades_" + oms.orderReference + ":" + internalOrderIDEntry + ":" + c.getAccountName()).equals("")) {
                    oms.getDb().delKey("opentrades", oms.orderReference + ":" + internalOrderIDEntry + ":" + c.getAccountName());
                }
                RedisSubscribe.tes.removeBidAskListener(this);
                RedisSubscribe.tes.removeOrderStatusListener(this);
                for (BeanConnection c1 : Parameters.connection) {
                    c1.getWrapper().removeOrderStatusListener(this);
                    c1.getWrapper().removeBidAskListener(this);
                }
            }
            } catch (Exception ex) {
                logger.log(Level.SEVERE, null, ex);
            }
            //}
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    public boolean underlyingTradePriceExists(BeanSymbol s, int waitSeconds) {
        int underlyingID = s.getUnderlyingID();
        if (underlyingID == -1) {
            return false;
        } else {
            int i = 0;
            while (s.getUnderlying().value() <= 0) {
                if (i < waitSeconds) {
                    try {
                        //see if price in redis
                        String today = DateUtil.getFormatedDate("yyyy-MM-dd", new Date().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));

                        ArrayList<Pair> pairs = Utilities.getPrices(Parameters.symbol.get(underlyingID), ":tick:close", DateUtil.getFormattedDate(today, "yyyy-MM-dd", Algorithm.timeZone), new Date());
                        if (pairs.size() > 0) {
                            int length = pairs.size();
                            double value = Utilities.getDouble(pairs.get(length - 1).getValue(), 0);
                            Parameters.symbol.get(underlyingID).setLastPrice(value);
                        }
                        Thread.sleep(1000);
                    } catch (Exception ex) {
                        logger.log(Level.SEVERE, null, ex);
                    }
                    Thread.yield();
                    i++;
                } else {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public synchronized void bidaskChanged(BidAskEvent event) {
        try {
            boolean fatfinger = false;
            if (event.getSymbolID() == id || event.getSymbolID() == underlyingid) {
                //check if there is a case for updating rel price. Only time criteron at present.
                if (recentOrders.size() == orderspermin
                        && (new Date().getTime() - (Long) recentOrders.get(0)) < 60000) {// Timestamp of the first of the "n" orders is *less* than 60 seconds prior. Stop!!
                    recalculate = false;
                } else {
                    recalculate = true;
                }
                if (recalculate) {
                    OrderBean ob = c.getOrders().get(externalOrderID);
                    BeanSymbol s = Parameters.symbol.get(id);
                    if (ob != null) {
                        internalOrderIDEntry = ob.getInternalOrderIDEntry();
                        limitPrice = ob.getParentLimitPrice();
                        double newLimitPrice = limitPrice;
                        switch (side) {
                            case BUY:
                            case COVER:
                                recalculate = false;
                                double bidPrice = s.getBidPrice();
                                double askPrice = s.getAskPrice();
                                switch (s.getType()) {
                                    case "OPT":
                                        s.getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                                        double calculatedPrice = 0;
                                        if (underlyingTradePriceExists(s, 1) && s.getCdte() > 0) {
                                            calculatedPrice = s.getOptionProcess().NPV();
                                            calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);
                                        } else if (s.getCdte() == 0) {
                                            Double strike = Utilities.getDouble(s.getOption(), 0);
                                            if (s.getRight().equals("CALL")) {
                                                calculatedPrice = s.getUnderlying().value() - strike;
                                            } else {
                                                calculatedPrice = strike - s.getUnderlying().value();
                                            }
                                            calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);
                                            if (calculatedPrice <= 0 || strike == 0 || s.getUnderlying().value() == 0) {
                                                calculatedPrice = 0;
                                            }
                                        }
                                        if (calculatedPrice == 0) { //underlying does not have a price. No recalculation.
                                            return;
                                        }
                                        /* Low Price--------High Price
                                         * (1) BP - LP - CP : Do Nothing. We are the best bid
                                         * (2) LP - BP - CP : Change to Best Bid
                                         * (3) BP - CP - LP : Change to Best Bid  
                                         * CP - BP - LP : Second Best Bid
                                         * LP - CP - BP : Second Best Bid
                                         * CP - LP - BP : Second Best Bid
                                         * Fat Finger protection
                                         * BP-CP / CP > +5% stay at CP.
                                         */

                                        if (bidPrice == 0 || (bidPrice - calculatedPrice) / (calculatedPrice) > 0.05) {
                                            fatfinger = true;
                                            if (fatFingerStart == Long.MAX_VALUE) {
                                                fatFingerStart = new Date().getTime();
                                            }
                                            newLimitPrice = calculatedPrice;
                                            if ((new Date().getTime() - fatFingerStart) > fatFingerWindow * 1000 && bidPrice > 0) {
                                                newLimitPrice = bidPrice - improveamt; //second best bid
                                            } else if (Math.abs(limitPrice - (calculatedPrice - 10 * ticksize)) < 10 * ticksize) {
                                                //To prevent frequent orders when we are not near market, we limit updates only if
                                                //new limitprice is off by 10 ticksize.
                                                newLimitPrice = calculatedPrice - 10 * ticksize;
                                            }
                                        }
                                        if (!fatfinger) {
                                            fatFingerStart = Long.MAX_VALUE;
                                        }

                                        if (!fatfinger) {
                                            if ((limitPrice < bidPrice && bidPrice <= calculatedPrice && bidPrice != plp)
                                                    || (bidPrice < calculatedPrice && calculatedPrice <= limitPrice)) {
                                                //Change to Best Bid
                                                plp = limitPrice;
                                                newLimitPrice = bidPrice + improveamt;
                                            } else if ((calculatedPrice <= bidPrice && bidPrice <= limitPrice)
                                                    || (limitPrice <= calculatedPrice && calculatedPrice <= bidPrice)
                                                    || (calculatedPrice <= limitPrice && limitPrice <= bidPrice)) {
                                                //Change to second best ask
                                                plp = limitPrice;
                                                newLimitPrice = bidPrice - Math.abs(improveamt);
                                            }
                                            double random = Math.random();
                                            if (random > improveprob && bidPrice > 0) {//no improvement, therefore worsen price
                                                if (recentOrders.size() > 0 && (new Date().getTime() - (Long) recentOrders.getLast()) > stickyperiod * 1000) {
                                                    plp = limitPrice;
                                                    newLimitPrice = bidPrice - Math.abs(improveamt);
                                                    retracement = true;
                                                }
                                            } else {
                                                retracement = false;
                                            }
                                        }

                                        if (newLimitPrice != limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED) {
                                            recentOrders.add(new Date().getTime());
                                            e.setLimitPrice(newLimitPrice);
                                            e.setOrderStage(EnumOrderStage.AMEND);
                                            e.setAccount(c.getAccountName());
                                            e.setTag("BIDASKCHANGED");
                                            String log = "Side:" + side + ",Calculated Price:" + calculatedPrice + ",LimitPrice:" + limitPrice + ",BidPrice:" + bidPrice + ",AskPrice:" + askPrice + ",New Limit Price:" + newLimitPrice + ",Current Order Status:" + ob.getChildStatus() + ",fatfinger:" + fatfinger;
                                            oms.getDb().setHash("opentrades", oms.orderReference + ":" + ob.getInternalOrderIDEntry() + ":" + c.getAccountName(), loggingFormat.format(new Date()), log);
                                            oms.orderReceived(e);

                                        }
                                        break;
                                    default:
                                        if (bidPrice > 0 && bidPrice > limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED) {
                                            double random = Math.random();
                                            if (random < improveprob) {
                                                newLimitPrice = bidPrice + improveamt;
                                            } else {
                                                if (recentOrders.size() > 0 && (new Date().getTime() - (Long) recentOrders.getLast()) > stickyperiod * 1000) {
                                                    newLimitPrice = bidPrice - Math.abs(improveamt);
                                                }
                                            }
                                            recentOrders.add(new Date().getTime());
                                            e.setLimitPrice(newLimitPrice);
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
                                recalculate = false;
                                bidPrice = s.getBidPrice();
                                askPrice = s.getAskPrice();
                                switch (s.getType()) {
                                    case "OPT":
                                        s.getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                                        double calculatedPrice = 0;
                                        if (underlyingTradePriceExists(s, 1) && s.getCdte() > 0) {
                                            calculatedPrice = Parameters.symbol.get(id).getOptionProcess().NPV();
                                            calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);
                                        } else if (s.getCdte() == 0) {
                                            Double strike = Utilities.getDouble(s.getOption(), 0);
                                            if (s.getRight().equals("CALL")) {
                                                calculatedPrice = s.getUnderlying().value() - strike;
                                            } else {
                                                calculatedPrice = strike - s.getUnderlying().value();
                                            }
                                            calculatedPrice = Utilities.roundTo(calculatedPrice, ticksize);
                                            if (calculatedPrice <= 0 || strike == 0 || s.getUnderlying().value() == 0) {
                                                calculatedPrice = 0;
                                            }
                                        }
                                        if (calculatedPrice == 0) {
                                            return;
                                        }
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
                                            newLimitPrice = calculatedPrice;
                                            if ((new Date().getTime() - fatFingerStart) > fatFingerWindow * 1000 && bidPrice > 0) {
                                                newLimitPrice = askPrice + improveamt; //second best offer
                                            } else if (Math.abs(limitPrice - (calculatedPrice - 10 * ticksize)) < 10 * ticksize) {
                                                //To prevent frequent orders when we are not near market, we limit updates only if
                                                //new limitprice is off by 10 ticksize.
                                                newLimitPrice = calculatedPrice + 10 * ticksize;
                                            }
                                        }

                                        if (!fatfinger) {
                                            fatFingerStart = Long.MAX_VALUE;
                                        }

                                        if (!fatfinger) {
                                            if ((calculatedPrice <= askPrice && askPrice < limitPrice && askPrice != plp)
                                                    || (limitPrice <= calculatedPrice && calculatedPrice < askPrice)) {
                                                //Change to Best Ask
                                                plp = limitPrice;
                                                newLimitPrice = askPrice - improveamt;
                                            } else if ((limitPrice <= askPrice && askPrice <= calculatedPrice)
                                                    || (askPrice <= calculatedPrice && calculatedPrice <= limitPrice)
                                                    || (askPrice <= limitPrice && limitPrice <= calculatedPrice)) {
                                                //Change to second best ask
                                                plp = limitPrice;
                                                newLimitPrice = askPrice + Math.abs(improveamt);
                                            }
                                            double random = Math.random();
                                            if (random > improveprob && askPrice > 0) {
                                                if (recentOrders.size() > 0 && (new Date().getTime() - (Long) recentOrders.getLast()) > stickyperiod * 1000) {
                                                    plp = limitPrice;
                                                    newLimitPrice = askPrice + Math.abs(improveamt);
                                                    retracement = true;
                                                } else {
                                                    retracement = false;
                                                }
                                            }
                                        }
                                        if (newLimitPrice != limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED) {
                                            recentOrders.add(new Date().getTime());
                                            e.setLimitPrice(newLimitPrice);
                                            e.setOrderStage(EnumOrderStage.AMEND);
                                            e.setAccount(c.getAccountName());
                                            e.setTag("BIDASKCHANGED");
                                            String log = "Side:" + side + ",Calculated Price:" + calculatedPrice + ",LimitPrice:" + limitPrice + ",BidPrice:" + bidPrice + ",AskPrice:" + askPrice + ",New Limit Price:" + newLimitPrice + ",Current Order Status:" + ob.getChildStatus() + ",fatfinger:" + fatfinger;
                                            oms.getDb().setHash("opentrades", oms.orderReference + ":" + ob.getInternalOrderIDEntry() + ":" + c.getAccountName(), loggingFormat.format(new Date()), log);
                                            oms.orderReceived(e);
                                        }
                                        break;
                                    default:
                                        if (askPrice > 0 && askPrice < limitPrice && ob.getParentStatus() != EnumOrderStatus.SUBMITTED) {
                                            double random = Math.random();
                                            if (random < improveprob) {
                                                newLimitPrice = askPrice - improveamt;
                                            } else {
                                                if (recentOrders.size() > 0 && (new Date().getTime() - (Long) recentOrders.getLast()) > stickyperiod * 1000) {
                                                    newLimitPrice = askPrice + Math.abs(improveamt);
                                                }
                                            }
                                            recentOrders.add(new Date().getTime());
                                            e.setLimitPrice(newLimitPrice);
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
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public synchronized void orderStatusReceived(OrderStatusEvent event) {
        OrderBean ob = c.getOrders().get(event.getOrderID());
        try{
        if (ob != null) {
            if (this.c.equals(event.getC()) && event.getOrderID() == externalOrderID && (ob.getParentSymbolID() - 1) == id) {
                if (!completed.get() && (event.getRemaining() == 0 || event.getStatus().equals("Cancelled"))) {
                    completed.set(Boolean.TRUE);
                    sync.offer("FINISHED", 1, TimeUnit.SECONDS);
                }
            }
        }
        }catch (Exception e){
            logger.log(Level.SEVERE,null,e);
        }
    }
}
