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
    int underlyingid;
    OrderEvent e;
    double ticksize = 0.05;
    EnumOrderSide side;
    double limitprice;
    ExecutionManager oms;
    boolean orderCompleted = false;
    final Object syncObject = new Object();
    private static final Logger logger = Logger.getLogger(OrderTypeRel.class.getName());

    public OrderTypeRel(int id, BeanConnection c, OrderEvent event, double ticksize, ExecutionManager oms) {
        this.c = c;
        this.id = id;
        underlyingid=Utilities.getReferenceID(Parameters.symbol, id, "STK");
        this.e = event;
        side = event.getSide();
        limitprice = event.getLimitPrice();
        this.ticksize = ticksize;
        this.oms = oms;
    }

    @Override
    public void run() {
        Subscribe.tes.addBidAskListener(this);
        Subscribe.tes.addOrderStatusListener(this);
        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addOrderStatusListener(this);
        }
        synchronized (syncObject) {
            try {
                syncObject.wait();
                logger.log(Level.INFO, "OrderTypeRel: Closing Manager");
                Subscribe.tes.removeBidAskListener(this);
                Subscribe.tes.removeOrderStatusListener(this);
            } catch (InterruptedException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }
    }

    @Override
    public void bidaskChanged(BidAskEvent event) {
        if(event.getSymbolID()==id){
        switch (side) {
            case BUY:
            case COVER:
                double bidprice = Parameters.symbol.get(id).getBidPrice();
                switch (Parameters.symbol.get(id).getType()) {
                    case "OPT": {
                        int underlyingid = Utilities.getReferenceID(Parameters.symbol, id, "STK");
                        Parameters.symbol.get(id).getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                        if (Parameters.symbol.get(id).getOptionProcess() == null) {
                            String strike = Parameters.symbol.get(id).getOption();
                            String right = Parameters.symbol.get(id).getRight();
                            Parameters.symbol.get(id).SetOptionProcess(new Date(), right, strike);
                        }
                        double calculatedPrice = Parameters.symbol.get(id).getOptionProcess().NPV();
                        double tmplimitprice = limitprice;
                        if (bidprice > limitprice & bidprice < calculatedPrice) {
                            tmplimitprice = bidprice + ticksize;
                            e.setLimitPrice(tmplimitprice);
                            e.setOrderStage(EnumOrderStage.AMEND);
                            e.setAccount(c.getAccountName());
                            e.setTag("BIDASKCHANGED");
                            oms.orderReceived(e);
                        } else if (limitprice > calculatedPrice) {
                            tmplimitprice = Math.min(bidprice, Utilities.roundTo(calculatedPrice, ticksize));
                            e.setLimitPrice(tmplimitprice);
                            e.setOrderStage(EnumOrderStage.AMEND);
                            e.setAccount(c.getAccountName());
                            e.setTag("BIDASKCHANGED");
                            oms.orderReceived(e);
                        }
                    logger.log(Level.INFO, "OrderTypeRel, Symbol:{0}, Side:{1}, CalculatedOptionPrice:{2}, CurrentLimitPriceWithBroker:{3}, NewLimitPrice:{4}",
                            new Object[]{Parameters.symbol.get(id).getDisplayname(), side, calculatedPrice, limitprice, tmplimitprice});
                    }

                    break;
                    default: {
                        if (bidprice > limitprice) {
                            double tmplimitprice = bidprice + ticksize;
                            e.setLimitPrice(tmplimitprice);
                            e.setOrderStage(EnumOrderStage.AMEND);
                            e.setAccount(c.getAccountName());
                            e.setTag("BIDASKCHANGED");
                            oms.orderReceived(e);

                        }
                        break;
                    }
                }
                break;
            case SHORT:
            case SELL:

                double askprice = Parameters.symbol.get(id).getAskPrice();
                switch (Parameters.symbol.get(id).getType()) {
                    case "OPT":
                        int underlyingid = Utilities.getReferenceID(Parameters.symbol, id, "STK");
                        Parameters.symbol.get(id).getUnderlying().setValue(Parameters.symbol.get(underlyingid).getLastPrice());
                        if (Parameters.symbol.get(id).getOptionProcess() == null) {
                            String strike = Parameters.symbol.get(id).getOption();
                            String right = Parameters.symbol.get(id).getRight();
                            Parameters.symbol.get(id).SetOptionProcess(new Date(), right, strike);
                        }
                        double calculatedPrice = Parameters.symbol.get(id).getOptionProcess().NPV();
                        double tmplimitprice = limitprice;
                        if (askprice < limitprice & askprice > calculatedPrice) {
                            tmplimitprice = askprice - ticksize;
                            e.setLimitPrice(tmplimitprice);
                            e.setOrderStage(EnumOrderStage.AMEND);
                            e.setAccount(c.getAccountName());
                            e.setTag("BIDASKCHANGED");
                            oms.orderReceived(e);

                        } else if (limitprice < calculatedPrice) {
                            tmplimitprice = Math.max(askprice, Utilities.roundTo(calculatedPrice, ticksize));
                            e.setLimitPrice(tmplimitprice);
                            e.setOrderStage(EnumOrderStage.AMEND);
                            e.setAccount(c.getAccountName());
                            e.setTag("BIDASKCHANGED");
                            oms.orderReceived(e);
                        }
                        logger.log(Level.INFO, "OrderTypeRel, Symbol:{0}, Side:{1}, CalculatedOptionPrice:{2}, CurrentLimitPriceWithBroker:{3}, NewLimitPrice:{4}",
                                new Object[]{Parameters.symbol.get(id).getDisplayname(), side, calculatedPrice, limitprice, tmplimitprice});



                        break;
                    default: {
                        if (askprice > 0 && askprice < limitprice) {
                            tmplimitprice = askprice - ticksize;
                            e.setLimitPrice(tmplimitprice);
                            e.setOrderStage(EnumOrderStage.AMEND);
                            e.setAccount(c.getAccountName());
                            e.setTag("BIDASKCHANGED");
                            oms.orderReceived(e);
                        }
                    }
                    break;
                }
            default:
                break;
        }
    }
    }

    @Override
    public void orderStatusReceived(OrderStatusEvent event) {
        OrderBean ob = c.getOrders().get(event.getOrderID());
        logger.log(Level.FINE, "OrderTypeRel : OrderID:{0},OrderID from ob:{1}, Remaining{2}", new Object[]{event.getOrderID(), ob.getOrderID(), event.getRemaining()});
        if (event.getOrderID() == ob.getOrderID()) {
            limitprice = ob.getParentLimitPrice();
            logger.log(Level.FINE, "Match OrderTypeRel : InternalOrderID:{0},Remaining{1}", new Object[]{event.getOrderID(), event.getRemaining()});
            if (event.getRemaining() == 0) {
                logger.log(Level.FINE, "OrderTypeRel: Waiting for lock");
                synchronized (syncObject) {
                    logger.log(Level.FINE, "OrderTypeRel: Lock obtained");
                    syncObject.notify();
                }
            }
        }
    }
}
