/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.RatesClient.Subscribe;
import com.ib.client.Order;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import static com.incurrency.framework.Algorithm.*;
/**
 *
 * @author admin
 */
public class ExecutionManager implements Runnable, OrderListener, OrderStatusListener, TWSErrorListener, BidAskListener {

    private final static Logger logger = Logger.getLogger(DataBars.class.getName());
    final ExecutionManager parentorder = this;
    static boolean logHeaderWritten = false;
    double tickSize;
    private Boolean aggression;
    String orderReference;
    Date endDate;
    public TradingEventSupport tes = new TradingEventSupport();
    private HashMap<OrderLink, Trade> trades = new HashMap(); //trades holds internal order id <init>, Trade object
    private double pointValue;
    private ArrayList<Integer> openPositionCount = new ArrayList<>();
    private ArrayList<Integer> maxOpenPositions = new ArrayList<>();
    private String timeZone = "";
    private String lastExecutionRequestTime = "";
    private ArrayList<String> accounts = new ArrayList<>();
    private ArrayList<ArrayList<Integer>> cancelledOrdersAcknowledgedByIB = new ArrayList<>();
    private ArrayList<ArrayList<LinkedAction>> cancellationRequestsForTracking = new ArrayList<>();
    private ArrayList<ArrayList<LinkedAction>> fillRequestsForTracking = new ArrayList<>();
    private Strategy s;
    private ArrayList notificationListeners = new ArrayList();
    //copies of global variables
    private ArrayList<Integer> deemedCancellation = new ArrayList<>();
    private final Object lockLinkedAction = new Object();
    private final String delimiter = "_";
    private double estimatedBrokerage=0;

    public ExecutionManager(Strategy s, Boolean aggression, double tickSize, Date endDate, String orderReference, double pointValue, Integer maxOpenPositions, String timeZone, ArrayList<String> accounts, HashMap<OrderLink, Trade> trades) {
        this.s = s;
        this.tickSize = tickSize;
        this.aggression = aggression;
        this.orderReference = orderReference;
        this.endDate = endDate;
        this.pointValue = pointValue;
        this.timeZone = timeZone;
        this.accounts = accounts;
        this.trades = trades;
        tes.addOrderListener(this); //subscribe to events published by tes owned by the strategy oms
        if (Subscribe.tes != null) {//subscribe to events published by pubsub 
            Subscribe.tes.addBidAskListener(this);
        }
        MainAlgorithm.tes.addBidAskListener(this);

        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addOrderStatusListener(this);
            c.getWrapper().addBidAskListener(this);
            c.getWrapper().addTWSErrorListener(this);
            this.maxOpenPositions.add(maxOpenPositions);
            this.openPositionCount.add(0);
            cancelledOrdersAcknowledgedByIB.add(new ArrayList<Integer>());
            cancellationRequestsForTracking.add(new ArrayList<LinkedAction>());//cancelled orders represent orders that do not need to be executed anymore
            fillRequestsForTracking.add(new ArrayList<LinkedAction>());
        }
        if (globalProperties.getProperty("deemedcancellation")!=null) {
            String init = globalProperties.getProperty("deemedcancellation").toString().trim();
            String values[] = init.split(",");
            for (String i : values) {
                deemedCancellation.add(Integer.valueOf(i));
            }
        }
        //first update combo positions
        ArrayList<Integer> comboOrderids = new ArrayList<>();
        for (Trade t : trades.values()) {
            if (Parameters.symbol.get(t.getEntrySymbolID()).getType().equals("COMBO")) {
                //update position
                comboOrderids.add(t.getEntryID());
                updateInitPositions(t, comboOrderids);
                //iter.remove();
            }
        }
        //then update single legs
        for (Map.Entry<OrderLink, Trade> trade : trades.entrySet()) {
            if (!Parameters.symbol.get(trade.getValue().getEntrySymbolID()).getType().equals("COMBO")) {
                updateInitPositions(trade.getValue(), comboOrderids);
            }
        }
        //print positions on initialization
        int i=-1;
        for (BeanConnection c : Parameters.connection) {
            i=i+1;
            Iterator iter = c.getPositions().entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Index, BeanPosition> p = (Map.Entry) iter.next();
                if (p.getKey().getStrategy().equals(orderReference)) {
                    if (p.getValue().getPosition() == 0) {
                        iter.remove();
                    } else {
                        logger.log(Level.INFO, "301, InitialTradePosition,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(p.getKey().getSymbolID()).getDisplayname() + delimiter + p.getValue().getPosition() + delimiter + p.getValue().getPrice()});

                            }
                    }
                }
            }
        /*
         for (Map.Entry<Index, BeanPosition> p : c.getPositions().entrySet()) {
         if (p.getKey().getStrategy().equals(orderReference)) {
         logger.log(Level.INFO, "301, InitialTradePosition,{0}",new Object[]{c.getAccountName()+delimiter+ orderReference+delimiter+ Parameters.symbol.get(p.getKey().getSymbolID()).getDisplayname()+delimiter+ p.getValue().getPosition()+delimiter+ p.getValue().getPrice()});
         }
         }
         */


        //Initialize timers
        new Timer(10000, cancelExpiredOrders).start();
        new Timer(2000, hastenCloseOut).start();
        //java.util.Timer  brokerage = new java.util.Timer("Brokerage Calculator");
        //brokerage.schedule(runBrokerage, new Date(), 120000);
        java.util.Timer orderProcessing = new java.util.Timer("Timer: Periodic Order and Execution Cleanup");
        orderProcessing.schedule(runGetOrderStatus, new Date(), 60000);
    }

    TimerTask runBrokerage = new TimerTask() {
        @Override
        public void run() {
            double entryCost = 0;
            double exitCost = 0;
            //calculate entry costs
            for (Trade t : trades.values()) {
                if (!t.getParentSymbol().contains(":")){//not a combo order
                for (BrokerageRate b : s.getBrokerageRate()) {
                    switch (b.primaryRule) {
                        case VALUE:
                            if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getEntrySide() == EnumOrderSide.BUY || t.getEntrySide() == EnumOrderSide.COVER))) {
                                entryCost = entryCost + (t.getEntryPrice() * t.getEntrySize() * b.primaryRate / 100) + (t.getEntryPrice() * t.getEntrySize() * b.primaryRate * b.secondaryRate / 10000);
                            }
                            break;
                        case SIZE:
                            if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getEntrySide() == EnumOrderSide.BUY || t.getEntrySide() == EnumOrderSide.COVER))) {
                                entryCost = entryCost + t.getEntrySize() * b.primaryRate + t.getEntrySize() * b.primaryRate * b.secondaryRate;
                            }
                            break;
                        case FLAT:
                            if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getEntrySide() == EnumOrderSide.BUY || t.getEntrySide() == EnumOrderSide.COVER))) {
                                entryCost = entryCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                            }
                            break;
                        case DISTRIBUTE:
                            if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getEntrySide() == EnumOrderSide.BUY || t.getEntrySide() == EnumOrderSide.COVER))) {
                                int tradesToday = trades.size() * 2;
                                if (tradesToday > 0) {
                                    entryCost = entryCost + b.primaryRate / tradesToday + (b.primaryRate / tradesToday) * b.secondaryRate;
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }
                //calculate exit costs
                for (BrokerageRate b : s.getBrokerageRate()) {
                    switch (b.primaryRule) {
                        case VALUE:
                            if (!t.getExitTime().equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getExitSide() == EnumOrderSide.BUY || t.getExitSide() == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && t.getExitTime().contains(t.getEntryTime().substring(0, 10))))) {
                                exitCost = exitCost + (t.getExitPrice() * t.getEntrySize() * b.primaryRate / 100) + (t.getEntryPrice() * t.getEntrySize() * b.primaryRate * b.secondaryRate / 10000);
                            }
                            break;
                        case SIZE:
                            if (!t.getExitTime().equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getExitSide() == EnumOrderSide.BUY || t.getExitSide() == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && t.getExitTime().contains(t.getEntryTime().substring(0, 10))))) {
                                exitCost = exitCost + t.getEntrySize() * b.primaryRate + t.getEntrySize() * b.primaryRate * b.secondaryRate;
                            }
                            break;
                        case FLAT:
                            if (!t.getExitTime().equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getExitSide() == EnumOrderSide.BUY || t.getExitSide() == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && t.getExitTime().contains(t.getEntryTime().substring(0, 10))))) {
                                exitCost = exitCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                            }
                            break;
                        case DISTRIBUTE:
                            int tradesToday = trades.size() * 2;
                            if (!t.getExitTime().equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getExitSide() == EnumOrderSide.BUY || t.getExitSide() == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && t.getExitTime().contains(t.getEntryTime().substring(0, 10))))) {
                                exitCost = exitCost + b.primaryRate / tradesToday + (b.primaryRate / tradesToday) * b.secondaryRate;
                            }
                            break;
                        default:
                            break;
                    }
                }
            }
            }
            ExecutionManager.this.setEstimatedBrokerage(entryCost+exitCost);

        }
    };
 
        
    
    private void updateInitPositions(Trade tr, ArrayList<Integer> combos) {
        int tempPosition;
        double tempPositionPrice;
        int id = TradingUtil.getEntryIDFromDisplayName(tr,Parameters.symbol);
        if (id >= 0 && (!combos.contains(tr.getEntryID()) || Parameters.symbol.get(tr.getEntrySymbolID()).getType().equals("COMBO"))) {//single leg trades or combo trade
            Index ind = new Index(orderReference, id);
            int i = -1;
            for (BeanConnection c : Parameters.connection) {
                i = i + 1;
                int tempOpenPosition = 0;
                if (c.getAccountName().equals(tr.getAccountName())) {
                    BeanPosition p = c.getPositions().get(ind) == null ? new BeanPosition() : c.getPositions().get(ind);
                    tempPosition = p.getPosition();
                    tempPositionPrice = p.getPrice();
                    double entryPrice = 0D;
                    switch (tr.getEntrySide()) {
                        case BUY:
                            entryPrice = tr.getEntryPrice();
                            tempPositionPrice = tempPosition + tr.getEntrySize() != 0 ? (tempPosition * tempPositionPrice + tr.getEntrySize() * entryPrice) / (tr.getEntrySize() + tempPosition) : 0D;
                            tempPosition = tempPosition + tr.getEntrySize();
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(this.pointValue);
                            c.getPositions().put(ind, p);
                            if(tr.getEntrySize()>0){
                            tempOpenPosition = this.openPositionCount.get(i);
                            this.openPositionCount.add(i, tempOpenPosition + 1);
                            logger.log(Level.INFO, "302, InitialOpenPosition,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + tr.getEntrySymbol()+delimiter+openPositionCount.get(i)});
                            }
                            break;
                        case SHORT:
                            entryPrice = tr.getEntryPrice();
                            tempPositionPrice = tempPosition - tr.getEntrySize() != 0 ? (tempPosition * tempPositionPrice - tr.getEntrySize() * entryPrice) / (-tr.getEntrySize() + tempPosition) : 0D;
                            tempPosition = tempPosition - tr.getEntrySize();
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(this.pointValue);
                            c.getPositions().put(ind, p);
                            if(tr.getEntrySize()>0){
                            tempOpenPosition = this.openPositionCount.get(i);
                            this.openPositionCount.add(i, tempOpenPosition + 1);
                            logger.log(Level.INFO, "302, InitialOpenPosition,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter +  tr.getEntrySymbol()+delimiter+openPositionCount.get(i)});
                            }
                            break;
                        default:
                            break;
                    }
                    switch (tr.getExitSide()) {
                        case COVER:
                            tempPositionPrice = tempPosition + tr.getEntrySize() != 0 ? (tempPosition * tempPositionPrice + tr.getEntrySize() * tr.getEntryPrice()) / (tr.getEntrySize() + tempPosition) : 0D;
                            tempPosition = tempPosition + tr.getEntrySize();
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(this.pointValue);
                            c.getPositions().put(ind, p);
                            break;
                        case SELL:
                            tempPositionPrice = tempPosition - tr.getEntrySize() != 0 ? (tempPosition * tempPositionPrice - tr.getEntrySize() * tr.getEntryPrice()) / (-tr.getEntrySize() + tempPosition) : 0D;
                            tempPosition = tempPosition - tr.getEntrySize();
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(this.pointValue);
                            c.getPositions().put(ind, p);
                            break;
                        default:
                            break;
                    }
                    //add childPositions and set it to null
                    for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(id).getCombo().entrySet()) {
                        p.getChildPosition().add(new BeanChildPosition(entry.getKey().getSerialno() - 1, entry.getValue()));
                    }
                }
            }
        } else if (id >= 0 && combos.contains(tr.getEntryID())) { //trade linked to combo. Update child positions
            //add child ids to combo position
            for (BeanConnection c : Parameters.connection) {
                if (c.getAccountName().equals(tr.getAccountName())) {
                    int parentid = getTrades().get(new OrderLink(tr.getEntryID(), 0, c.getAccountName())).getEntrySymbolID();
                    Index ind = new Index(orderReference, parentid);
                    BeanPosition p = c.getPositions().get(ind) == null ? new BeanPosition() : c.getPositions().get(ind);
                    for (BeanChildPosition cp : p.getChildPosition()) {
                        if (cp.getSymbolid() == id) {
                            tempPosition = cp.getPosition();
                            tempPositionPrice = cp.getPrice();
                            double entryPrice = 0D;
                            switch (tr.getEntrySide()) {
                                case BUY:
                                    entryPrice = tr.getEntryPrice();
                                    tempPositionPrice = tempPosition + tr.getEntrySize() != 0 ? (tempPosition * tempPositionPrice + tr.getEntrySize() * entryPrice) / (tr.getEntrySize() + tempPosition) : 0D;
                                    tempPosition = tempPosition + tr.getEntrySize();
                                    cp.setPosition(tempPosition);
                                    cp.setPrice(tempPositionPrice);
                                    cp.setPointValue(this.pointValue);
                                    c.getPositions().put(ind, p);
                                    break;
                                case SHORT:
                                    entryPrice = tr.getEntryPrice();
                                    tempPositionPrice = tempPosition - tr.getEntrySize() != 0 ? (tempPosition * tempPositionPrice - tr.getEntrySize() * entryPrice) / (-tr.getEntrySize() + tempPosition) : 0D;
                                    tempPosition = tempPosition - tr.getEntrySize();
                                    cp.setPosition(tempPosition);
                                    cp.setPrice(tempPositionPrice);
                                    cp.setPointValue(this.pointValue);
                                    c.getPositions().put(ind, p);
                                    break;
                                default:
                                    break;
                            }
                            switch (tr.getExitSide()) {
                                case COVER:
                                    tempPositionPrice = tempPosition + tr.getEntrySize() != 0 ? (tempPosition * tempPositionPrice + tr.getEntrySize() * tr.getEntryPrice()) / (tr.getEntrySize() + tempPosition) : 0D;
                                    tempPosition = tempPosition + tr.getEntrySize();
                                    cp.setPosition(tempPosition);
                                    cp.setPrice(tempPositionPrice);
                                    cp.setPointValue(this.pointValue);
                                    c.getPositions().put(ind, p);
                                    break;
                                case SELL:
                                    tempPositionPrice = tempPosition - tr.getEntrySize() != 0 ? (tempPosition * tempPositionPrice - tr.getEntrySize() * tr.getEntryPrice()) / (-tr.getEntrySize() + tempPosition) : 0D;
                                    tempPosition = tempPosition - tr.getEntrySize();
                                    cp.setPosition(tempPosition);
                                    cp.setPrice(tempPositionPrice);
                                    cp.setPointValue(this.pointValue);
                                    c.getPositions().put(ind, p);
                                    break;
                                default:
                                    break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void fireNotification(Notification notify) {
        NotificationEvent notifyEvent = new NotificationEvent(new Object(), notify);
        Iterator itrListeners = notificationListeners.iterator();
        while (itrListeners.hasNext()) {
            ((NotificationListener) itrListeners.next()).notificationReceived(notifyEvent);

        }
    }

    //<editor-fold defaultstate="collapsed" desc="Event Listeners">    
    @Override
    public void bidaskChanged(BidAskEvent event) {

        //if the symbol exists in ordersSymbols (order exists) && ordersToBeCancelled (the order has potential for dynamic management)
        // ***
        // Order is placed at amended price IF its and entry order and slippage has not been exceeded
        // OR its an exit order and slippage has been exceeded. For exit orders, the wait time is ignored if slippage is exceeded.

        int id = event.getSymbolID();
        for (BeanConnection c : Parameters.connection) {
            Index ind = new Index(orderReference, id);
            if (c.getActiveOrders().containsKey(ind) && getAggression()) {
                for (BeanOrderInformation tempOrderInfo : c.getActiveOrders().get(ind)) {
                    EnumOrderSide side = tempOrderInfo.getOrigEvent().getSide();
                    OrderBean ob = c.getOrders().get(tempOrderInfo.getOrderID());
                    double firstlimitprice = tempOrderInfo.getOrigEvent().getFirstLimitPrice(); //first limit price is the price initally generated by the strategy.
                    double maxslippage = tempOrderInfo.getOrigEvent().getMaxSlippage();
                    boolean waitOver = (!ob.getOrderType().equals(EnumOrderType.MKT)) && tempOrderInfo.getOrigEvent().getDynamicOrderDuration() > 0 && (tempOrderInfo.getExpireTime() - System.currentTimeMillis() < tempOrderInfo.getOrigEvent().getDynamicOrderDuration() * 60 * 1000);
                    boolean coverSlippageExceeded = (!ob.getOrderType().equals(EnumOrderType.MKT)) && (side == EnumOrderSide.COVER && ((Parameters.symbol.get(id).getLastPrice() - firstlimitprice) / firstlimitprice) > maxslippage);
                    boolean sellSlippageExceeded = (!ob.getOrderType().equals(EnumOrderType.MKT)) && (side == EnumOrderSide.SELL && ((firstlimitprice - Parameters.symbol.get(id).getLastPrice()) / firstlimitprice) > maxslippage);
                    //boolean comboPartiallyFilled = (ob.getParentSymbolID() != ob.getChildSymbolID() && ob.getParentStatus() == EnumOrderStatus.PARTIALFILLED) && !ob.getOrderType().equals(EnumOrderType.MKT);
                    boolean comboPartiallyFilled = (ob.getParentSymbolID() != ob.getChildSymbolID()) && !ob.getOrderType().equals(EnumOrderType.MKT);
                    if (ob != null && waitOver || coverSlippageExceeded || sellSlippageExceeded || comboPartiallyFilled) {
                        //amendement scenario is valid if exit orders have not exceeded slippage or dynamic order management time has kicked in or combo order is partially filled
                        if (!Parameters.symbol.get(id).getType().equals("COMBO")) {
                            double limitprice = tempOrderInfo.getOrigEvent().getLimitPrice();
                            double newlimitprice = c.getWrapper().getLimitPriceUsingAggression(id, limitprice, side);//limit price of child is returned
                            logger.log(Level.FINE, "307,NewLimitPriceCalculated,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + newlimitprice});
                            Boolean placeorder = newlimitprice > 0 && (Math.abs(newlimitprice - limitprice) > tickSize ? Boolean.TRUE : Boolean.FALSE)
                                    && (((side == EnumOrderSide.BUY || side == EnumOrderSide.SHORT) && Math.abs(newlimitprice - firstlimitprice) < maxslippage * firstlimitprice) || (side == EnumOrderSide.SELL || side == EnumOrderSide.COVER));
                            if (placeorder) {
                                OrderEvent eventnew = tempOrderInfo.getOrigEvent();
                                eventnew.setLimitPrice(newlimitprice);
                                eventnew.setOrderStage(EnumOrderStage.AMEND);
                                eventnew.setAccount(c.getAccountName());
                                eventnew.setTag("BIDASKCHANGED");
                                orderReceived(eventnew);
                            }

                        } else if (Parameters.symbol.get(id).getType().equals("COMBO")) {
                            //generate order amendments if needed.
                            if (comboPartiallyFilled && !(waitOver || coverSlippageExceeded || sellSlippageExceeded)) {
                                OrderEvent eventnew = tempOrderInfo.getOrigEvent();
                                eventnew.setLimitPrice(tempOrderInfo.getOrigEvent().getFirstLimitPrice());
                                eventnew.setOrderStage(EnumOrderStage.AMEND);
                                eventnew.setAccount(c.getAccountName());
                                eventnew.setTag("BIDASKCHANGED");
                                orderReceived(eventnew);
                            } else {
                                double newLimitPrice = 0;
                                if (ob.getParentOrderSide().equals(EnumOrderSide.BUY) || ob.getParentOrderSide().equals(EnumOrderSide.COVER)) {
                                    newLimitPrice = tempOrderInfo.getOrigEvent().getFirstLimitPrice() + Math.abs(tempOrderInfo.getOrigEvent().getFirstLimitPrice() * maxslippage);
                                } else {
                                    newLimitPrice = tempOrderInfo.getOrigEvent().getFirstLimitPrice() - Math.abs(tempOrderInfo.getOrigEvent().getFirstLimitPrice() * maxslippage);
                                }
                                logger.log(Level.FINE, "102,NewLimitPriceCalculated,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + newLimitPrice});
                                OrderEvent eventnew = tempOrderInfo.getOrigEvent();
                                eventnew.setLimitPrice(newLimitPrice);
                                eventnew.setOrderStage(EnumOrderStage.AMEND);
                                eventnew.setAccount(c.getAccountName());
                                eventnew.setTag("BIDASKCHANGED");
                                orderReceived(eventnew);
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void orderReceived(OrderEvent event) {
        //for each connection eligible for trading
        // System.out.println(Thread.currentThread().getName());
        try {
            if (event.getOrdReference().compareTo(orderReference) == 0) {
                //we first handle initial orders given by EnumOrderStage=INIT
                if (event.getOrderStage() == EnumOrderStage.INIT && (event.getReason() != EnumOrderReason.OCOSL || event.getReason() == EnumOrderReason.OCOTP)) {
                    int id = event.getSymbolBean().getSerialno() - 1;
                    for (BeanConnection c : Parameters.connection) {
                        boolean specificAccountSpecified = !event.getAccount().isEmpty();
                        if ("Trading".equals(c.getPurpose()) && (!specificAccountSpecified && accounts.contains(c.getAccountName()) || (specificAccountSpecified && event.getAccount().equals(c.getAccountName())))) {
                            //check if system is square
                            //logger.log(Level.INFO, "303, OrderDetails,{0}", new Object[]{c.getAccountName()+delimiter+ orderReference+delimiter+ event.getInternalorder()+delimiter+event.getInternalorderentry()+delimiter+event.get Parameters.symbol.get(id).getDisplayname()+delimiter+event.getSide()+delimiter+event.getOrderSize()+delimiter+event.getLimitPrice()+delimiter+event.getTriggerPrice()});
                            Index ind = new Index(event.getOrdReference(), id);
                            Integer position = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                            position = position != 0 ? 1 : 0;
                            int signedPositions = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                            Integer openorders = zilchOpenOrders(c, id, event.getOrdReference()) == Boolean.TRUE ? 0 : 1;
                            Integer entry = (event.getSide() == EnumOrderSide.BUY || event.getSide() == EnumOrderSide.SHORT) ? 1 : 0;
                            String rule = event.getStubs() != null ? "STUB" : Integer.toBinaryString(position) + Integer.toBinaryString(openorders) + Integer.toBinaryString(entry);
                            //POI (Position, Open Order, Initiation)
                            ArrayList<Integer> openBuy = getOpenOrdersForSide(c, id, EnumOrderSide.BUY);
                            ArrayList<Integer> openSell = getOpenOrdersForSide(c, id, EnumOrderSide.SELL);
                            ArrayList<Integer> openShort = getOpenOrdersForSide(c, id, EnumOrderSide.SHORT);
                            ArrayList<Integer> openCover = getOpenOrdersForSide(c, id, EnumOrderSide.COVER);
                            logger.log(Level.FINE, "307,OpenOrderSize,{0}", new Object[]{event.getSymbolBean().getDisplayname()+delimiter+openBuy.size()+delimiter+openSell.size()+delimiter+ openShort.size()+delimiter+ openCover.size()});

                            switch (rule) {
                                case "STUB":
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "STUB" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    processStubOrder(id, c, event);
                                    break;
                                case "000"://position=0, no openorder=0, exit order as entry=0
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "000" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    break;
                                case "001": //position=0, no openorder=0, entry order as entry=1
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "001" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    if (signedPositions == 0 && (event.getSide() == EnumOrderSide.BUY || event.getSide() == EnumOrderSide.SHORT)) {
                                        processEntryOrder(id, c, event);
                                    } else {
                                        logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{"UnexpectedCase_001"});
                                    }
                                    break;
                                case "100": //position=1, no open order=0, exit order 
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "100" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    if ((signedPositions > 0 && event.getSide() == EnumOrderSide.SELL) || (signedPositions < 0 && event.getSide() == EnumOrderSide.COVER)) {
                                        processExitOrder(id, c, event);
                                    } else {
                                        //Logically the else condition should never be hit as it requires an entry order. Case 100 is only for exit orders
                                        logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{"UnexpectedCase_100"});
                                        this.cancelOpenOrders(c, id, event.getOrdReference());
                                        this.squareAllPositions(c, id, event.getOrdReference());
                                    }
                                    break;
                                case "010": //position=0, open order, exit order
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "010" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    int size = 0;
                                    if (event.isScale()) {
                                        size = cleanScaleTrueOrders(c, id, event);
                                    } else {
                                        size = cleanScaleFalseOrders(c, id, event);
                                    }
                                    if (size == 0) {
                                        processExitOrder(id, c, event);
                                    }
                                    break;
                                case "011": //no position, open order exists, entry order
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "011" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    //cancel open orders and place new orders
                                    if (event.isScale()) {
//                                        logger.log(Level.INFO, "{0},{1},Execution Manager,Case:011. Scale In Allowed, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                        processEntryOrder(id, c, event);
                                    } else {
//                                        logger.log(Level.INFO, "{0},{1},Execution Manager,Case:011. Scale In Not allowed, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                        cleanScaleFalseOrders(c, id, event);
                                    }
                                    break;
                                case "101": //position, no open order, entry order received
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "101" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    if (event.isScale()) {
//                                        logger.log(Level.INFO, "{0},{1},Execution Manager,Case:101. Scale In Allowed, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                        processEntryOrder(id, c, event);
                                    } else {
                                        //system is broken for the symbol
                                        logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{"UnexpectedCase_303"});
//                                        logger.log(Level.INFO, "{0},{1},Execution Manager,Case: 101. Error:Entry order received while positions exist, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                    }
                                    break;

                                case "111"://position, open order, entry order received.
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "111" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    boolean combo = Parameters.symbol.get(id).getType().equals("COMBO") ? true : false;
                                    if (event.isScale()) {
                                        if (!combo && (openCover.size() > 0 && event.getSide() == EnumOrderSide.SHORT) || (openSell.size() > 0 && event.getSide() == EnumOrderSide.BUY)) {
                                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Reinstate Scale-in, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                            ArrayList<Integer> orderids = event.getSide() == EnumOrderSide.SHORT ? getOpenOrdersForSide(c, id, EnumOrderSide.COVER) : getOpenOrdersForSide(c, id, EnumOrderSide.SELL);
                                            this.cancelOpenOrders(c, id, event.getOrdReference());
                                            //there is a problem if the order could not be cancelled as it was already filled. We then need to bring back this event again.
                                            int connectionid = Parameters.connection.indexOf(c);
                                            synchronized (lockLinkedAction) {
                                                for (int orderid : orderids) {
                                                    ArrayList<OrderEvent> e = new ArrayList<>();
                                                    //ArrayList<LinkedAction> fillRequests = getFillRequestsForTracking().get(connectionid);
                                                    //fillRequests.add(new LinkedAction(c, orderid, event, EnumLinkedAction.REVERSEFILL));
                                                    getFillRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderid, event, EnumLinkedAction.REVERSEFILL));
                                                }
                                                lockLinkedAction.notifyAll();
                                            }

                                        } else if ((c.getPositions().get(ind).getPosition() > 0 && openBuy.size() > 0 && openShort.size() == 0 && openSell.size() == 0 && openCover.size() == 0)
                                                || (c.getPositions().get(ind).getPosition() < 0 && openBuy.size() == 0 && openShort.size() > 0 && openSell.size() == 0 && openCover.size() == 0)) {
                                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Scale-In allowed, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                            processEntryOrder(id, c, event);
                                        } else {
                                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Cleanse orders, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                            cleanScaleTrueOrders(c, id, event);
                                        }
                                    } else if (!event.isScale()) {
                                        if (!combo && (openCover.size() > 0 && event.getSide() == EnumOrderSide.SHORT) || (openSell.size() > 0 && event.getSide() == EnumOrderSide.BUY)) {
                                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Scale-in not allowed. Reinstate , Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                            ArrayList<Integer> orderids = event.getSide() == EnumOrderSide.SHORT ? getOpenOrdersForSide(c, id, EnumOrderSide.COVER) : getOpenOrdersForSide(c, id, EnumOrderSide.SELL);
                                            this.cancelOpenOrders(c, id, event.getOrdReference());
                                            //there is a problem if the order could not be cancelled as it was already filled. We then need to bring back this event again.
                                            int connectionid = Parameters.connection.indexOf(c);
                                            synchronized (lockLinkedAction) {
                                                for (int orderid : orderids) {
                                                    ArrayList<OrderEvent> e = new ArrayList<>();
                                                    //ArrayList<LinkedAction> fillRequests = getFillRequestsForTracking().get(connectionid);
                                                    //fillRequests.add(new LinkedAction(c, orderid, event, EnumLinkedAction.REVERSEFILL));
                                                    getFillRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderid, event, EnumLinkedAction.REVERSEFILL));
                                                }
                                            }
                                        } else {
                                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Cleanse Orders, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                            cleanScaleFalseOrders(c, id, event);
                                        }
                                    }
                                    break;
                                case "110"://position, open order, exit order received.
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "110" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                                    if (event.isScale()) {
                                        if (openBuy.size() > 0 || openShort.size() > 0) {
//                                            logger.log(Level.INFO, "{0},{1},Execution Manager,Case:110. Scale in. Cleanse orders, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                            cleanScaleTrueOrders(c, id, event);
                                        } else {
//                                            logger.log(Level.INFO, "{0},{1},Execution Manager,Case:110. Scale-In Exit, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                            processEntryOrder(id, c, event);
                                        }
                                    } else if (!event.isScale()) {
//                                        logger.log(Level.INFO, "{0},{1},Execution Manager,Case:110. Scale in not allowed. Cleanse orders, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                        cleanScaleTrueOrders(c, id, event);

                                    }
                                    break;
                                default: //print message with details
                                    logger.log(Level.INFO, "303,ExecutionFlow,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "NoFlowSelected" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
//                                    logger.log(Level.INFO, "{0},{1},Execution Manager,Case:Default, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                    break;
                            }
                        }
                    }

                } else if (event.getOrderStage() == EnumOrderStage.AMEND) {
                    int id = event.getSymbolBean().getSerialno() - 1;
                    //logger.log(Level.FINE, "{0},{1},Execution Manager,OrderReceived Amend. Symbol:{2}, OrderSide:{3}", new Object[]{"ALL", orderReference, Parameters.symbol.get(id).getSymbol(), event.getSide()});
                    for (BeanConnection c : Parameters.connection) {
                        if ("Trading".equals(c.getPurpose()) && accounts.contains(c.getAccountName())) {
                            if (event.getTag().equals("")) {
                                event.setTag("AMEND");
                                //logger.log(Level.INFO, "{0},{1},Execution Manager,OrderReceived Amend. Symbol:{2}, OrderSide:{3}", new Object[]{"ALL", orderReference, Parameters.symbol.get(id).getSymbol(), event.getSide()});
                            }
                            processOrderAmend(id, c, event);
                        }

                    }
                } else if (event.getOrderStage() == EnumOrderStage.CANCEL) {
                    int id = event.getSymbolBean().getSerialno() - 1;
                    //Do cancel processing for combo orders. To be added.
                    //logger.log(Level.INFO, "All Accounts,{0},Execution Manager,Cancel any open orders, Symbol:{1}, OrderSide:{2}", new Object[]{orderReference, Parameters.symbol.get(id).getSymbol(), event.getSide()});
                    for (BeanConnection c : Parameters.connection) {
                        if ("Trading".equals(c.getPurpose()) && accounts.contains(c.getAccountName())) {
                            //check if system is square && order id is to initiate
                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Cancel any open orders, Symbol:{2}, OrderSide:{3}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getSide()});
                            processOrderCancel(id, c, event);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    private int cleanScaleTrueOrders(BeanConnection c, int id, OrderEvent event) {
        ArrayList<Integer> orderids = new ArrayList<>();
        ArrayList<Integer> openBuy = getOpenOrdersForSide(c, id, EnumOrderSide.BUY);
        ArrayList<Integer> openSell = getOpenOrdersForSide(c, id, EnumOrderSide.SELL);
        ArrayList<Integer> openShort = getOpenOrdersForSide(c, id, EnumOrderSide.SHORT);
        ArrayList<Integer> openCover = getOpenOrdersForSide(c, id, EnumOrderSide.COVER);
        EnumOrderSide side = event.getSide();
        switch (side) {
            case BUY:
                orderids.addAll(openSell);
                orderids.addAll(openShort);
                orderids.addAll(openCover);
                break;
            case SELL:
                orderids.addAll(openBuy);
                orderids.addAll(openShort);
                orderids.addAll(openCover);
                break;
            case SHORT:
                orderids.addAll(openBuy);
                orderids.addAll(openSell);
                orderids.addAll(openCover);
                break;
            case COVER:
                orderids.addAll(openBuy);
                orderids.addAll(openSell);
                orderids.addAll(openShort);
                break;
            default:
                break;
        }
        if (orderids.size() > 0) {
            int connectionid = Parameters.connection.indexOf(c);
            synchronized (lockLinkedAction) {
                //for (int orderid : orderids) {
                //ArrayList<LinkedAction> cancelRequests = getCancellationRequestsForTracking().get(connectionid);
                ArrayList<OrderEvent> e = new ArrayList<>();
                //cancelRequests.add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.CLOSEPOSITION));
                //cancelRequests.add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.PROPOGATE));
                //getCancellationRequestsForTracking().set(connectionid, cancelRequests);
                getCancellationRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.CLOSEPOSITION));
                getCancellationRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.PROPOGATE));
                logger.log(Level.FINE, "307, LinkedActionAdded,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "CLOSEPOSITION" + delimiter + event.getInternalorder() + delimiter + orderids.get(0)+delimiter+event.getSymbolBean().getDisplayname()});
                logger.log(Level.FINE, "307, LinkedActionAdded,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "PROPOGATE" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                c.getWrapper().cancelOrder(c, orderids.get(0));
                //}
                lockLinkedAction.notifyAll();
            }
        }
        return orderids.size();
    }

    private int cleanScaleFalseOrders(BeanConnection c, int id, OrderEvent event) {
        ArrayList<Integer> orderids = new ArrayList<>();
        ArrayList<Integer> openBuy = getOpenOrdersForSide(c, id, EnumOrderSide.BUY);
        ArrayList<Integer> openSell = getOpenOrdersForSide(c, id, EnumOrderSide.SELL);
        ArrayList<Integer> openShort = getOpenOrdersForSide(c, id, EnumOrderSide.SHORT);
        ArrayList<Integer> openCover = getOpenOrdersForSide(c, id, EnumOrderSide.COVER);
        orderids.addAll(openBuy);
        orderids.addAll(openSell);
        orderids.addAll(openShort);
        orderids.addAll(openCover);

        if (orderids.size() > 0) {
            int connectionid = Parameters.connection.indexOf(c);
            synchronized (lockLinkedAction) {
                //for (int orderid : orderids) {
                //ArrayList<LinkedAction> cancelRequests = getCancellationRequestsForTracking().get(connectionid);
                ArrayList<OrderEvent> e = new ArrayList<>();
                //cancelRequests.add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.CLOSEPOSITION));
                //cancelRequests.add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.PROPOGATE));
                //getCancellationRequestsForTracking().set(connectionid, cancelRequests);
                getCancellationRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.CLOSEPOSITION));
                getCancellationRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.PROPOGATE));
                logger.log(Level.FINE, "307, LinkedActionAdded,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "CLOSEPOSITION" + delimiter + event.getInternalorder() + delimiter + orderids.get(0)+delimiter+event.getSymbolBean().getDisplayname()});
                logger.log(Level.FINE, "307, LinkedActionAdded,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "PROPOGATE" + delimiter + event.getInternalorder()+delimiter+event.getSymbolBean().getDisplayname()});
                c.getWrapper().cancelOrder(c, orderids.get(0));
                //}
                lockLinkedAction.notifyAll();
            }
        }
        return orderids.size();
    }

    ArrayList<Integer> getOpenOrdersForSide(BeanConnection c, int id, EnumOrderSide orderSide) {
        ArrayList<Integer> out = new ArrayList<>();
        Index ind = new Index(s.getStrategy(), id);
        boolean zilchorders = c.getOrdersSymbols().get(ind) == null ? true : false;
        if (!zilchorders) {
            ArrayList<SymbolOrderMap> orderMaps = c.getOrdersSymbols().get(ind);
            for (SymbolOrderMap orderMap : orderMaps) {
                if (c.getOrders().get(orderMap.externalOrderId).getParentOrderSide().equals(orderSide)) {
                    out.add(orderMap.externalOrderId);
                }
            }
        }
        return out;
    }

    void processEntryOrder(int id, BeanConnection c, OrderEvent event) {
        int connectionid = Parameters.connection.indexOf(c);
        int tempOpenPosition = this.getOpenPositionCount().get(connectionid);
        int tempMaxPosition = this.maxOpenPositions.get(connectionid);
        if (tempOpenPosition < tempMaxPosition && !s.getPlmanager().isDayProfitTargetHit() && !s.getPlmanager().isDayStopLossHit()) {//enter loop is sl/tp is not hit
            synchronized (c.lockOrderMapping) {
                c.getOrderMapping().put(new Index(s.getStrategy(), event.getInternalorder()), new ArrayList<Integer>());
            }
            HashMap<Integer, Order> orders = c.getWrapper().createOrder(event);
            if (orders.size() > 0) {//trading is not halted 
                this.getOpenPositionCount().set(connectionid, tempOpenPosition + 1);
                logger.log(Level.INFO, "303, EntryOrder,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder()});
                ArrayList<Integer> orderids = c.getWrapper().placeOrder(c, event, orders);

                //update orderid structures
                //activeOrders - done
                //ordersInProgress - done
                //orderSymbols -done
                //orderMapping -done
                //ordersToBeCancelled - done
                //ordersToBeFastTracked - not needed
                //ordersToBeRetried - not needed
                //ordersMissed - not needed
                long tempexpire = event.getEffectiveFrom().equals("") ? System.currentTimeMillis() + event.getExpireTime() * 60 * 1000 : DateUtil.parseDate("yyyyMMdd HH:mm:ss", event.getEffectiveFrom()).getTime() + event.getExpireTime() * 60 * 1000;
                if (event.getExpireTime() != 0) {//orders have an expiration time. put them in cancellation queue
                    for (int orderid : orderids) {
                        synchronized (c.lockOrdersToBeCancelled) {
                            c.getOrdersToBeCancelled().put(orderid, new BeanOrderInformation(id, c, orderid, tempexpire, event));
                        }
                        //logger.log(Level.FINE, "Expiration time in object getOrdersToBeCancelled={0}", DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", tempexpire));
                    }
                }
                ArrayList<SymbolOrderMap> symbolOrders = new ArrayList<>();
                ArrayList<Integer> linkedOrderIds = new ArrayList<>();
                for (int orderid : orderids) {
                    c.getActiveOrders().put(new Index(orderReference, id), new BeanOrderInformation(id, c, orderid, tempexpire, event));
                    symbolOrders.add(new SymbolOrderMap(id, orderid));
                    linkedOrderIds.add(orderid);
                    if (!c.getOrdersInProgress().contains(orderid)) {
                        c.getOrdersInProgress().add(orderid);
                    }
                }
                for (Integer orderid : linkedOrderIds) {
                    c.getOrders().get(orderid).setParentLimitPrice(event.getLimitPrice());
                }

                c.getOrdersSymbols().put(new Index(orderReference, id), symbolOrders);
                synchronized (c.lockOrderMapping) {
                    c.getOrderMapping().put(new Index(orderReference, event.getInternalorder()), orderids);
                }
            }
        } else {
            logger.log(Level.INFO, "303,EntryOrderNotSent, {0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder()});
        }
    }

    void processStubOrder(int id, BeanConnection c, OrderEvent event) {
        HashMap<Integer, Order> orders = new HashMap<>();
        orders = c.getWrapper().createOrder(event);
        logger.log(Level.INFO, "303,StubOrder, {0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder()});
        ArrayList<Integer> orderids = c.getWrapper().placeOrder(c, event, orders);
        //orderid structures impacted
        //activeOrders - yes
        //ordersInProgress - yes
        //orderSymbols - yes
        //orderMapping - yes
        //ordersToBeCancelled - no
        //ordersToBeFastTracked - yes
        //ordersToBeRetried - no
        //ordersMissed - no
        ArrayList<SymbolOrderMap> symbolOrders = new ArrayList<>();
        ArrayList<Integer> linkedOrderIds = new ArrayList<>();
        for (int orderid : orderids) {
            c.getActiveOrders().put(new Index(orderReference, id), new BeanOrderInformation(id, c, orderid, 0, event));
            symbolOrders.add(new SymbolOrderMap(id, orderid));
            linkedOrderIds.add(orderid);
            if (!c.getOrdersInProgress().contains(orderid)) {
                c.getOrdersInProgress().add(orderid);
            }
        }
        c.getOrdersSymbols().put(new Index(orderReference, id), symbolOrders);
        synchronized (c.lockOrderMapping) {
            ArrayList<Integer> tempMapping = c.getOrderMapping().get(new Index(orderReference, event.getInternalorder()));
            tempMapping.addAll(orderids);
            c.getOrderMapping().put(new Index(orderReference, event.getInternalorder()), tempMapping);
        }
    }

    void processExitOrder(int id, BeanConnection c, OrderEvent event) {
        Index ind = new Index(event.getOrdReference(), id);
        int positions = c.getPositions().get(ind) == null ? 0 : Math.abs(c.getPositions().get(ind).getPosition());
        HashMap<Integer, Order> orders = new HashMap<>();
        if (!event.isScale()) {
            event.setOrderSize(positions);
            orders = c.getWrapper().createOrder(event);
        } else if (event.isScale()) {
            orders = c.getWrapper().createOrder(event);
        }
        ArrayList<Integer> orderids = c.getWrapper().placeOrder(c, event, orders);
        logger.log(Level.INFO, "303,ExitOrder, {0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder() + delimiter + event.getOrderSize()});

        //orderid structures impacted
        //activeOrders - yes
        //ordersInProgress - yes
        //orderSymbols - yes
        //orderMapping - yes
        //ordersToBeCancelled - no
        //ordersToBeFastTracked - yes
        //ordersToBeRetried - no
        //ordersMissed - no

        long tempexpire = event.getEffectiveFrom().equals("") ? System.currentTimeMillis() + event.getExpireTime() * 60 * 1000 : DateUtil.parseDate("yyyyMMdd HH:mm:ss", event.getEffectiveFrom()).getTime() + event.getExpireTime() * 60 * 1000;
        if (event.getExpireTime() != 0) {//orders have an expiration time. put them in fasttrack queue
            for (int orderid : orderids) {
                c.getOrdersToBeFastTracked().put(orderid, new BeanOrderInformation(id, c, orderid, tempexpire, event));
                logger.log(Level.INFO, "307,ExitOrderCancellationQueueAdded, {0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder() + delimiter + orderid});

            }
        }

        ArrayList<SymbolOrderMap> symbolOrders = new ArrayList<>();
        ArrayList<Integer> linkedOrderIds = new ArrayList<>();
        for (int orderid : orderids) {
            c.getActiveOrders().put(new Index(orderReference, id), new BeanOrderInformation(id, c, orderid, tempexpire, event));
            symbolOrders.add(new SymbolOrderMap(id, orderid));
            linkedOrderIds.add(orderid);
            if (!c.getOrdersInProgress().contains(orderid)) {
                c.getOrdersInProgress().add(orderid);
            }
        }
        c.getOrdersSymbols().put(new Index(orderReference, id), symbolOrders);
        synchronized (c.lockOrderMapping) {
            c.getOrderMapping().put(new Index(orderReference, event.getInternalorder()), orderids);
        }
    }

    void processOrderAmend(int id, BeanConnection c, OrderEvent event) {
        //synchronized to ensure that only one combo amendment enters this loop
        //An issue when there are multiple combos linked to this same execution manager
        Index ind = new Index(event.getOrdReference(), id);
        int size = 0;
        int internalorderid = event.getInternalorder();
        ArrayList<Integer> orderids;
        synchronized (c.lockOrderMapping) {
            orderids = c.getOrderMapping().get(new Index(event.getOrdReference(), internalorderid));
        }
        HashMap<Integer, Double> limitPrices = new HashMap<>();
        if (event.isScale()) {
            size = event.getOrderSize();
        } else {
            size = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
        }
        if (!orderids.isEmpty()) { //orders exist for the internal order id
            if (c.getOrders().get(orderids.get(0)).getParentStatus() != EnumOrderStatus.COMPLETEFILLED) {
                //amend orders
                //<Symbolid,order>
                HashMap<Integer, Order> ordersHashMap = c.getWrapper().createOrderFromExisting(c, internalorderid, event.getOrdReference());
                boolean combo = false;
                int parentid = -1;

                if (event.getSymbolBean().getType().equals("COMBO")) {
                    combo = true;
                    parentid = event.getSymbolBean().getSerialno() - 1;
                }

                if (combo && parentid > -1) {//amend combo orders
                    HashMap<Integer, Order> amendedOrders = new HashMap<>();
                    synchronized (c.lockOrderMapping) {
                        //limit prices will contain hashmap<childid, limit price> 
                        //if childid is filled, limit price will = fill price
                        //limitprices size = combo size
                        limitPrices = c.getWrapper().amendLimitPricesUsingAggression(parentid, event.getLimitPrice(), c.getOrderMapping().get(new Index(orderReference, internalorderid)), event.getSide());
                    }

                    HashMap<Integer, Boolean> ordersToBePlaced = this.ComoOrdersToBePlaced(c, event.getInternalorder());
                    for (Map.Entry<Integer, Order> entry : ordersHashMap.entrySet()) {
                        if (ordersToBePlaced.get(entry.getKey())) {
                            Order order = entry.getValue();
                            double newLimitPrice = limitPrices.get(entry.getKey());
                            if (Math.abs(order.m_lmtPrice - newLimitPrice) >= tickSize) {
                                order.m_lmtPrice = limitPrices.get(entry.getKey());
                                amendedOrders.put(entry.getKey(), order);
                            }
                        }
                    }
                    if (event.getTag().equals("AMEND")) {//update ob limit prices and active order orignal limit price
                        for (int orda : c.getOrderMapping().get(new Index(event.getOrdReference(), event.getInternalorder()))) {
                            c.getOrders().get(orda).setParentLimitPrice(event.getLimitPrice());
                        }
                        for (BeanOrderInformation boi : c.getActiveOrders().get(ind)) {
                            boi.getOrigEvent().setFirstLimitPrice(event.getLimitPrice());
                        }
                    }

                    if (amendedOrders.size() > 0 && (c.getOrders().get(orderids.get(0)).getParentStatus() == EnumOrderStatus.ACKNOWLEDGED || c.getOrders().get(orderids.get(0)).getParentStatus() == EnumOrderStatus.PARTIALFILLED)) {
                        c.getWrapper().placeOrder(c, event, amendedOrders);
                        logger.log(Level.INFO, "303,AmendmentOrder, {0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder()});
                        double parentlimitprice = 0D;
                        for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                            int cid = entry.getKey().getSerialno() - 1;
                            parentlimitprice = parentlimitprice + limitPrices.get(cid) * entry.getValue();
                        }

                        for (Integer ordid : c.getOrderMapping().get(new Index(event.getOrdReference(), event.getInternalorder()))) {
                            c.getOrders().get(ordid).setParentLimitPrice(parentlimitprice);
                        }
                    }
                } else if (!combo) {
                    HashMap<Integer, Order> amendedOrders = new HashMap<>();
                    for (Map.Entry<Integer, Order> entry : ordersHashMap.entrySet()) {
                        Order ord = entry.getValue();
                        if (Math.abs(ord.m_auxPrice - event.getTriggerPrice()) > tickSize || Math.abs(ord.m_lmtPrice - event.getLimitPrice()) > tickSize) {
                            ord.m_auxPrice = event.getTriggerPrice() > 0 ? event.getTriggerPrice() : 0;
                            ord.m_lmtPrice = event.getLimitPrice() > 0 ? event.getLimitPrice() : 0;
                            if (event.getLimitPrice() > 0 & event.getTriggerPrice() == 0) {
                                ord.m_orderType = "LMT";
                                ord.m_lmtPrice = event.getLimitPrice();
                            } else if (event.getLimitPrice() == 0 && event.getTriggerPrice() > 0 && (event.getSide() == EnumOrderSide.SELL || event.getSide() == EnumOrderSide.COVER)) {
                                ord.m_orderType = "STP";
                                ord.m_lmtPrice = event.getLimitPrice();
                                ord.m_auxPrice = event.getTriggerPrice();
                            } else if (event.getLimitPrice() > 0 && event.getTriggerPrice() > 0) {
                                ord.m_orderType = "STP LMT";
                                ord.m_lmtPrice = event.getLimitPrice();
                                ord.m_auxPrice = event.getTriggerPrice();
                            } else {
                                ord.m_orderType = "MKT";
                                ord.m_lmtPrice = 0;
                                ord.m_auxPrice = 0;
                            }
                            //ord.m_totalQuantity = size;
                            amendedOrders.put(id, ord);
                            if (event.getTag().equals("AMEND")) {//update ob limit prices and active order orignal limit price
                                for (int orda : c.getOrderMapping().get(new Index(event.getOrdReference(), event.getInternalorder()))) {
                                    c.getOrders().get(orda).setParentLimitPrice(event.getLimitPrice());
                                }
                                for (BeanOrderInformation ordb : c.getActiveOrders().get(new Index(event.getOrdReference(), event.getInternalorder()))) {
                                    ordb.getOrigEvent().setLimitPrice(event.getLimitPrice());
                                }
                            }
                            if (amendedOrders.size() > 0) {
                                c.getWrapper().placeOrder(c, event, amendedOrders);
                                logger.log(Level.INFO, "305,AmendmentOrder, {0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder()});
                                c.getOrders().get(ord.m_orderId).setParentLimitPrice(event.getLimitPrice());
                                //update orders information. Do we need the two lines below as its already set in TWSConnection.placeOrders
                                //c.getOrders().get(orderid).setTriggerPrice(ob.m_auxPrice);
                                //c.getOrders().get(orderid).setLimitPrice(ob.m_lmtPrice);                            }
                            }
                        }
                    }
                }
            }
        }
    }

    private HashMap<Integer, Boolean> ComoOrdersToBePlaced(BeanConnection c, int internalOrderID) {
        HashMap<Integer, Boolean> comboOrders = new HashMap<>();// Symbol id, order to be placed
        for (int orderid : c.getOrderMapping().get(new Index(orderReference, internalOrderID))) {
            if (c.getOrders().get(orderid).getChildStatus() == EnumOrderStatus.COMPLETEFILLED) {
                comboOrders.put(c.getOrders().get(orderid).getChildSymbolID() - 1, Boolean.FALSE);
            } else {
                comboOrders.put(c.getOrders().get(orderid).getChildSymbolID() - 1, Boolean.TRUE);
            }
        }
        return comboOrders;
    }

    void processOrderCancel(int id, BeanConnection c, OrderEvent event) {
        Index ind = new Index(event.getOrdReference(), id);
        int orderid = 0;
        ArrayList<Integer> orderids = new ArrayList<>();
        switch (event.getSide()) {
            case BUY:
                orderids = this.getOpenOrdersForSide(c, id, EnumOrderSide.BUY);
                break;
            case SELL:
                orderids = this.getOpenOrdersForSide(c, id, EnumOrderSide.SELL);
                break;
            case SHORT:
                orderids = this.getOpenOrdersForSide(c, id, EnumOrderSide.SHORT);
                break;
            case COVER:
                orderids = this.getOpenOrdersForSide(c, id, EnumOrderSide.COVER);
                break;
            case TRAILBUY:
                orderids = this.getOpenOrdersForSide(c, id, EnumOrderSide.TRAILBUY);
                break;
            case TRAILSELL:
                orderids = this.getOpenOrdersForSide(c, id, EnumOrderSide.TRAILSELL);
                break;
            default:
                break;
        }
        orderid = orderids.size() >= 1 ? orderids.get(0) : 0;
        if (orderid > 0) {
            logger.log(Level.INFO, "306,CancellationOrder, {0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder() + delimiter + orderid});
            c.getWrapper().cancelOrder(c, orderid);
        }
    }

    @Override
    public synchronized void orderStatusReceived(OrderStatusEvent event) {
        try {
            int orderid = event.getOrderID();
            //update HashMap orders
            BeanConnection c = event.getC();
            synchronized (event.getC().lockOrders) {
                OrderBean ob = c.getOrders().get(orderid);
                if (ob != null && ob.getOrderReference().compareTo(orderReference) == 0) {
                    int parentid = ob.getParentSymbolID() - 1;
                    int childid = ob.getChildSymbolID() - 1;
                    EnumOrderStatus fillStatus = EnumOrderStatus.SUBMITTED;
                    if (ob.getParentSymbolID() == ob.getChildSymbolID()) {//single leg
                        if (event.getRemaining() == 0) {
                            fillStatus = EnumOrderStatus.COMPLETEFILLED;
                        } else if (event.getRemaining() > 0 && event.getAvgFillPrice() > 0 && !"Cancelled".equals(event.getStatus())) {
                            fillStatus = EnumOrderStatus.PARTIALFILLED;
                        } else if (("Cancelled".equals(event.getStatus()) || "Inactive".equals(event.getStatus())) && ob.getChildFillSize() == 0) {
                            fillStatus = EnumOrderStatus.CANCELLEDNOFILL;
                        } else if (("Cancelled".equals(event.getStatus()) || "Inactive".equals(event.getStatus())) && ob.getChildFillSize() != 0) {
                            fillStatus = EnumOrderStatus.CANCELLEDPARTIALFILL;
                        } else if ("Submitted".equals(event.getStatus())) {
                            fillStatus = EnumOrderStatus.ACKNOWLEDGED;
                        }
                    } else {//combo order
                        ArrayList<Integer> linkedOrderIds = TradingUtil.getLinkedOrderIds(orderid, c);
                        HashMap<Integer, Integer> incompleteFills = new HashMap<>();
                        boolean noFill = true;
                        boolean acknowledged = true;
                        linkedOrderIds.remove(Integer.valueOf(orderid));
                        for (int orderidi : linkedOrderIds) {
                            OrderBean obi = c.getOrders().get(orderidi);
                            /*
                             while(obi==null){//wait till the order has been placed for linked ids
                             obi = c.getOrders().get(orderidi);
                             }
                             */
                            if (obi != null) {
                                int incomplete = obi.getChildOrderSize() - obi.getChildFillSize();
                                if (obi.getChildFillSize() > 0) {
                                    noFill = noFill && false;
                                }
                                if (incomplete > 0) {
                                    incompleteFills.put(obi.getOrderID(), incomplete);
                                }
                                acknowledged = acknowledged && !obi.getChildStatus().equals(EnumOrderStatus.SUBMITTED);
                            } else {
                                System.out.println("-------------Order ID is null!!----------");
                                incompleteFills.put(-1, 1);//add any dummy value in incomplete fills
                                acknowledged = false;

                            }
                        }
                        if (incompleteFills.isEmpty() && event.getRemaining() <= 0) {
                            fillStatus = EnumOrderStatus.COMPLETEFILLED;
                        } else if (incompleteFills.size() >= 0 && event.getFilled() > 0 && !"Cancelled".equals(event.getStatus())) {
                            fillStatus = EnumOrderStatus.PARTIALFILLED;
                        } else if (("Cancelled".equals(event.getStatus()) || "Inactive".equals(event.getStatus())) && noFill) {
                            fillStatus = EnumOrderStatus.CANCELLEDNOFILL;
                        } else if (("Cancelled".equals(event.getStatus()) || "Inactive".equals(event.getStatus())) && !noFill) {
                            fillStatus = EnumOrderStatus.CANCELLEDPARTIALFILL;
                        } else if ("Submitted".equals(event.getStatus()) && !ob.getChildStatus().equals(EnumOrderStatus.ACKNOWLEDGED)) {
                            fillStatus = EnumOrderStatus.ACKNOWLEDGED;
                        }

                    }

                    logger.log(Level.INFO, "308, inStratOrderStatus,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid + delimiter + fillStatus});

                    switch (fillStatus) {
                        case COMPLETEFILLED:
                            updateFilledOrders(event.getC(), orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                            break;
                        case PARTIALFILLED:
                            updatePartialFills(event.getC(), orderid, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                            break;
                        case CANCELLEDNOFILL:
                        case CANCELLEDPARTIALFILL:
                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Cancelled Partial Fill,Symbol:{2},OrderID:{3}", new Object[]{event.getC().getAccountName(), orderReference, Parameters.symbol.get(parentid).getSymbol(), orderid});
                            updateCancelledOrders(event.getC(), parentid, orderid);
                            break;
                        case ACKNOWLEDGED:
                            updateAcknowledgement(event.getC(), parentid, orderid);
                            //logger.log(Level.FINE, "{0},{1},Execution Manager,Order Acknowledged by IB, Parent Symbol: {2}, Child Symbol: {3}, Orderid: {4}", new Object[]{event.getC().getAccountName(), orderReference, Parameters.symbol.get(parentid).getSymbol(), Parameters.symbol.get(childid).getSymbol(), orderid});
                        default:
                            break;

                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void TWSErrorReceived(TWSErrorEvent event) {
        if (deemedCancellation != null && deemedCancellation.contains(event.getErrorCode())&& !event.getErrorMessage().contains("Cannot cancel the filled order")) {//135 is thrown if there is no specified order id with TWS.
            int id = event.getConnection().getOrders().get(event.getId()).getParentSymbolID() - 1;
            String ref = event.getConnection().getOrders().get(event.getId()).getOrderReference();
            if (orderReference.equals(ref)) {
                logger.log(Level.INFO, "103,OrderCancelledEvent,{0}", new Object[]{event.getConnection().getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getId() + delimiter + event.getErrorCode() + delimiter + event.getErrorMessage()});
                //this.tes.fireOrderStatus(event.getConnection(), event.getId(), "Cancelled", 0, 0, 0, 0, 0, 0D, 0, "");
                this.updateCancelledOrders(event.getConnection(), id, event.getId());

            }
        }
        if (event.getErrorCode() == 200) {
            //contract id not found
        } else if (event.getErrorCode() == 202 && event.getErrorMessage().contains("Equity with Loan Value")) { //insufficient margin
            int id = event.getConnection().getOrders().get(event.getId()).getParentSymbolID() - 1;
            int orderid = event.getId();
            logger.log(Level.INFO, "103,InsufficientMargin,{0}", new Object[]{event.getConnection().getAccountName()+delimiter+ orderReference+delimiter+ Parameters.symbol.get(id).getSymbol()+delimiter+ event.getErrorCode()+delimiter+ event.getId()+delimiter+ event.getErrorMessage()});
            //generate cancellation of order id
            event.getConnection().getOrdersToBeFastTracked().remove(orderid);
            //this.getActiveOrders().remove(id); //commented this as activeorders is a part of OMS and impacts all accounts. Insufficient margin is related to a specific account
        } else if (event.getErrorCode() == 202 && event.getErrorMessage().contains("Order Canceled - reason:The order price is outside of the allowable price limits")) {
            int id = event.getConnection().getOrders().get(event.getId()).getParentSymbolID() - 1;
            //send email
            Thread t = new Thread(new Mail("Order placed by inStrat for symbol" + Parameters.symbol.get(id).getSymbol() + " over strategy " + s.getStrategy() + "was outside permissible range. Please check inStrat status", "Algorithm SEVERE ALERT"));
            t.start();
            logger.log(Level.INFO, "103,OrderCancelled,{0}", new Object[]{event.getConnection().getAccountName()+delimiter+ orderReference+delimiter+ Parameters.symbol.get(id).getSymbol()+delimiter+ event.getErrorCode()+delimiter+ event.getId()+delimiter+ event.getErrorMessage()});
            this.tes.fireOrderStatus(event.getConnection(), event.getId(), "Cancelled", 0, 0, 0, 0, 0, 0D, 0, "");
        } else if (event.getErrorCode() == 202 && event.getErrorMessage().contains("Order Canceled - reason:")) {
            if (event.getConnection().getOrders().get(event.getId()) != null) {
                int id = event.getConnection().getOrders().get(event.getId()).getParentSymbolID() - 1;
                logger.log(Level.INFO, "103,OrderCancelled,{0}", new Object[]{event.getConnection().getAccountName()+delimiter+ orderReference+delimiter+ Parameters.symbol.get(id).getSymbol()+delimiter+ event.getErrorCode()+delimiter+ event.getId()+delimiter+ event.getErrorMessage()});
                this.tes.fireOrderStatus(event.getConnection(), event.getId(), "Cancelled", 0, 0, 0, 0, 0, 0D, 0, "");
            }
        }

    }

    private void reduceStub(BeanConnection c, int internalorderid, int parentid, EnumOrderSide parentSide) {
        HashMap<Integer, Integer> stubs = comboStubSize(c, internalorderid, parentid, 0); //holds <childid,stubsize>
        Boolean stubSizeZero = true;
        for (Integer stub : stubs.values()) {
            if (stub != 0) {
                stubSizeZero = stubSizeZero && false;
            }
        }
        if (!stubSizeZero) {
            OrderEvent e = new OrderEvent(new Object(), internalorderid, internalorderid, Parameters.symbol.get(parentid), parentSide, EnumOrderReason.REGULARENTRY, EnumOrderType.MKT, 0, 0, 0, orderReference, 0, EnumOrderStage.INIT, 0, 0D, true, "DAY", true, "", "", stubs);
            e.setAccount(c.getAccountName());
            tes.fireOrderEvent(e);
        }
    }

    private void completeStub(BeanConnection c, int internalorderid, int parentid, int orderid) {
        OrderBean ob = c.getOrders().get(orderid);
        int baselineSize = ob.getParentOrderSide() == EnumOrderSide.BUY || ob.getParentOrderSide() == EnumOrderSide.COVER ? ob.getParentOrderSize() : -ob.getParentOrderSize();
        HashMap<Integer, Integer> stubs = comboStubSize(c, internalorderid, parentid, baselineSize); //holds <childid,stubsize>
        //reverse stub signs
        for (Map.Entry<Integer, Integer> stub : stubs.entrySet()) {
            stubs.put(stub.getKey(), -stub.getValue());
        }
        Boolean stubSizeZero = true;
        for (Integer stub : stubs.values()) {
            if (stub != 0) {
                stubSizeZero = stubSizeZero && false;
            }
        }
        if (!stubSizeZero) {
            OrderEvent e = new OrderEvent(new Object(), internalorderid, internalorderid, Parameters.symbol.get(parentid), ob.getParentOrderSide(), EnumOrderReason.REGULAREXIT, EnumOrderType.MKT, 0, 0, 0, orderReference, 0, EnumOrderStage.INIT, 0, 0D, true, "DAY", true, "", "", stubs);
            e.setAccount(c.getAccountName());
            tes.fireOrderEvent(e);
        }
    }

    /**
     * Returns an <symbolid,orderid> HashMap containing the symbols and
     * corresponding positions. This value is the summary of the position. There
     * could be outstanding orders that are not reflected in the position
     *
     * @param c
     * @param parentid
     * @return
     */
    private HashMap<Integer, Integer> positionsInMarket(BeanConnection c, int parentid) {
        HashMap<Integer, Integer> out = new HashMap<>();
        ArrayList<Integer> combo = new ArrayList<>();
        if (Parameters.symbol.get(parentid).getType().equals("COMBO")) {
            for (BeanSymbol ob : Parameters.symbol.get(parentid).getCombo().keySet()) {
                combo.add(ob.getSerialno() - 1);
            }
        } else {
            combo.add(parentid);
        }
        for (OrderBean ob : c.getOrders().values()) {
            int id = ob.getChildSymbolID() - 1;
            if (combo.contains(Integer.valueOf(id)) && ob.getOrderReference().equals(orderReference)) {
                //Submitted,ACKNOWLEDGED,COMPLETEFILLED,PARTIALFILLED,CANCELLEDPARTIALFILL,CANCELLEDNOFILL
                int last = 0;
                int current = 0;
                last = out.get(id);
                current = (ob.getChildOrderSide().equals(EnumOrderSide.BUY) || ob.getChildOrderSide().equals(EnumOrderSide.COVER)) ? ob.getChildFillSize() : -ob.getChildFillSize();
                out.put(id, last + current);
            }
        }

        return out;
    }

    private int getStubSize(BeanConnection c, int childid, int parentid, int position) {
        BeanPosition p = c.getPositions().get(new Index(orderReference, parentid));
        int expectedChildPosition = 0;
        int actualChildPosition = 0;
        int stubSize = 0;

        for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
            int id = entry.getKey().getSerialno() - 1;
            if (id == childid) {//get child expected position
                for (BeanChildPosition cp : p.getChildPosition()) {
                    if (cp.getSymbolid() == childid) {
                        expectedChildPosition = cp.getBuildingblockSize() * position;
                        actualChildPosition = cp.getPosition();
                        break;
                    }
                }
            }
        }
        stubSize = expectedChildPosition - actualChildPosition;
        return stubSize;
    }

    private synchronized void fireLinkedActions(BeanConnection c, int orderid) {
        //this function only supports linked actions for cancellation. What about linked action for fills?
        OrderBean ob = c.getOrders().get(orderid);
        int parentid = ob.getParentSymbolID() - 1;
        int internalorderid = ob.getInternalOrderID();
        //if this was a requested cancellation, fire any event if needed
        int connectionid = Parameters.connection.indexOf(c);
        synchronized (lockLinkedAction) {
            Iterator<LinkedAction> iter;
            ArrayList<LinkedAction> cancelledOrders = (ArrayList<LinkedAction>) getCancellationRequestsForTracking().get(connectionid).clone();
            //iter = cancelledOrders.iterator();
            ArrayList<LinkedAction> itemsToRemove = new ArrayList<>();
            for (LinkedAction f : cancelledOrders) {
                //while (iter.hasNext()) {
                //  LinkedAction f = iter.next();
                if (f.orderID == orderid) {
                    //logger.log(Level.INFO, "{0},{1},Execution Manager,Cancellation Success. Linked Order being generated, OrderID Cancelled:{2}, symbol:{3}", new Object[]{c.getAccountName(), orderReference, orderid, Parameters.symbol.get(parentid).getSymbol()});
                    fireLinkedAction(c, orderid, f.action, f);
                    cleanseOrdersToBeRetried(c, parentid, ob.getParentOrderSide());
                    //iter.remove();
                    itemsToRemove.add(f);
                }
            }
            ArrayList<LinkedAction> filledOrders = this.getFillRequestsForTracking().get(connectionid);
            iter = filledOrders.iterator();
            for (LinkedAction f : filledOrders) {
                //while (iter.hasNext()) {
                //LinkedAction f = iter.next();
                if (f.orderID == orderid) {
                    //logger.log(Level.INFO, "{0},{1},Execution Manager,OrderFilled. Linked Order being generated, OrderID Cancelled:{2}, symbol:{3}", new Object[]{c.getAccountName(), orderReference, orderid, Parameters.symbol.get(parentid).getSymbol()});
                    fireLinkedAction(c, orderid, f.action, f);
                    cleanseOrdersToBeRetried(c, parentid, ob.getParentOrderSide());
                    //iter.remove();
                    itemsToRemove.add(f);
                }
            }
            for (LinkedAction f : itemsToRemove) {
                logger.log(Level.FINE, "307,LinkedActionRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + f.action + delimiter + internalorderid + delimiter + f.orderID});
                getCancellationRequestsForTracking().get(connectionid).remove(f);
            }
            for (LinkedAction f : itemsToRemove) {
                logger.log(Level.FINE, "307,LinkedActionRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + f.action + delimiter + internalorderid + delimiter + f.orderID});
                getFillRequestsForTracking().get(connectionid).remove(f);
            }
            lockLinkedAction.notifyAll();
        }



    }

    private synchronized void removeLinkedActions(BeanConnection c, int orderid){
        OrderBean ob = c.getOrders().get(orderid);
        int parentid = ob.getParentSymbolID() - 1;
        int internalorderid = ob.getInternalOrderID();
        //if this was a requested cancellation, fire any event if needed
        int connectionid = Parameters.connection.indexOf(c);
        synchronized (lockLinkedAction) {
            Iterator<LinkedAction> iter;
            ArrayList<LinkedAction> cancelledOrders = (ArrayList<LinkedAction>) getCancellationRequestsForTracking().get(connectionid).clone();
            //iter = cancelledOrders.iterator();
            ArrayList<LinkedAction> itemsToRemove = new ArrayList<>();
            for (LinkedAction f : cancelledOrders) {
                if (f.orderID == orderid) {
                    //fireLinkedAction(c, orderid, f.action, f);
                    cleanseOrdersToBeRetried(c, parentid, ob.getParentOrderSide());
                    //iter.remove();
                    itemsToRemove.add(f);
                }
            }
            ArrayList<LinkedAction> filledOrders = this.getFillRequestsForTracking().get(connectionid);
            iter = filledOrders.iterator();
            for (LinkedAction f : filledOrders) {
                if (f.orderID == orderid) {
                    //logger.log(Level.INFO, "{0},{1},Execution Manager,OrderFilled. Linked Order being generated, OrderID Cancelled:{2}, symbol:{3}", new Object[]{c.getAccountName(), orderReference, orderid, Parameters.symbol.get(parentid).getSymbol()});
                    //fireLinkedAction(c, orderid, f.action, f);
                    cleanseOrdersToBeRetried(c, parentid, ob.getParentOrderSide());
                    itemsToRemove.add(f);
                }
            }
            for (LinkedAction f : itemsToRemove) {
                logger.log(Level.FINE, "307,LinkedActionRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + f.action + delimiter + internalorderid + delimiter + f.orderID});
                getCancellationRequestsForTracking().get(connectionid).remove(f);
            }
            for (LinkedAction f : itemsToRemove) {
                logger.log(Level.FINE, "307,LinkedActionRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + f.action + delimiter + internalorderid + delimiter + f.orderID});
                getFillRequestsForTracking().get(connectionid).remove(f);
            }
            lockLinkedAction.notifyAll();
        }



    }
    private void fireLinkedAction(BeanConnection c, int orderid, EnumLinkedAction nextAction, LinkedAction f) {
        OrderBean ob = c.getOrders().get(orderid);
        int parentid = ob.getParentSymbolID() - 1;
        OrderEvent e;
        int size;
        switch (nextAction) {
            case COMPLETEFILL:
                size = ob.getParentOrderSize() - ob.getParentFillSize();
                e = OrderEvent.fastClose(Parameters.symbol.get(parentid), ob.getParentOrderSide(), size, orderReference);
                e.setAccount(c.getAccountName());
                logger.log(Level.FINE, "307,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + ob.getInternalOrderID()});
                tes.fireOrderEvent(e);
                break;
            case REVERSEFILL:
                size = ob.getParentOrderSize() - ob.getParentFillSize();
                e = OrderEvent.fastClose(Parameters.symbol.get(parentid), reverse(ob.getParentOrderSide()), size, orderReference);
                e.setAccount(c.getAccountName());
                logger.log(Level.FINE, "307,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + ob.getInternalOrderID()});
                tes.fireOrderEvent(e);
                break;
            case CLOSEPOSITION:
                size = c.getPositions().get(new Index(orderReference, parentid)) != null ? c.getPositions().get(new Index(orderReference, parentid)).getPosition() : 0;
                if (size > 0) {
                    EnumOrderSide side = size > 0 ? EnumOrderSide.SELL : EnumOrderSide.COVER;
                    e = OrderEvent.fastClose(Parameters.symbol.get(parentid), side, size, orderReference);
                    e.setAccount(c.getAccountName());
                    logger.log(Level.FINE, "307,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + ob.getInternalOrderID()});
                    tes.fireOrderEvent(e);
                }
                break;
            case REVERSESTUB:
                int parentposition = ob.getParentFillSize();
                int childstub = 0;
                EnumOrderSide childside;
                EnumOrderSide parentside;
                for (BeanChildPosition cp : c.getPositions().get(new Index(orderReference, parentid)).getChildPosition()) {
                    childstub = cp.getPosition() - parentposition * cp.getBuildingblockSize();
                    if (childstub > 0) {
                        childside = EnumOrderSide.SELL;
                        if (cp.getBuildingblockSize() > 0) {
                            parentside = EnumOrderSide.SELL;
                        } else {
                            parentside = EnumOrderSide.COVER;
                        }

                    } else if (childstub < 0) {
                        childside = EnumOrderSide.COVER;
                        if (cp.getBuildingblockSize() > 0) {
                            parentside = EnumOrderSide.COVER;
                        } else {
                            parentside = EnumOrderSide.SELL;
                        }
                    } else {
                        childside = EnumOrderSide.UNDEFINED;
                        parentside = EnumOrderSide.UNDEFINED;
                    }
                    e = OrderEvent.fastClose(Parameters.symbol.get(ob.getParentSymbolID() - 1), parentside, 1, orderReference);
                    e.setAccount(c.getAccountName());
                    logger.log(Level.FINE, "307,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + ob.getInternalOrderID()});
                    tes.fireOrderEvent(e);
                }
                break;
            case PROPOGATE:
                logger.log(Level.FINE, "307,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + ob.getInternalOrderID()});
                tes.fireOrderEvent(f.e);
                break;
            default:
                break;
        }
    }

    private boolean cleanseOrdersToBeRetried(BeanConnection c, int parentid, EnumOrderSide side) { //this refers parentid
        Iterator ordersToBeRetried = c.getOrdersToBeRetried().entrySet().iterator();
        while (ordersToBeRetried.hasNext()) {
            Map.Entry<Long, OrderEvent> order = (Map.Entry<Long, OrderEvent>) ordersToBeRetried.next();
            OrderEvent orderEvent = order.getValue();
            if ((orderEvent.getSymbolBean().getSerialno() - 1) == parentid && orderEvent.getSide() == side) {
                ordersToBeRetried.remove();
                return true;
            }
        }
        return false;
    }

    private EnumOrderSide reverse(EnumOrderSide side) {
        EnumOrderSide out;
        switch (side) {
            case BUY:
                out = EnumOrderSide.SELL;
                break;
            case SELL:
                out = EnumOrderSide.BUY;
                break;
            case SHORT:
                out = EnumOrderSide.COVER;
                break;
            case COVER:
                out = EnumOrderSide.SHORT;
                break;
            default:
                out = EnumOrderSide.UNDEFINED;
                break;
        }
        return out;
    }
    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="Timer Events">
    ActionListener cancelExpiredOrders = new ActionListener() { //call this every 10 seconds
        @Override
        public void actionPerformed(ActionEvent e) {
            for (BeanConnection c : Parameters.connection) {
                int cancelledOrderSize = 0;
                synchronized (c.lockOrdersToBeCancelled) {
                    cancelledOrderSize = c.getOrdersToBeCancelled().size();
                }
                if (cancelledOrderSize > 0) {
                    ArrayList<Integer> temp = new ArrayList();
                    ArrayList<Integer> symbols = new ArrayList();
                    synchronized (c.lockOrdersToBeCancelled) {
                        for (Integer key : c.getOrdersToBeCancelled().keySet()) {
                            //logger.log(Level.FINE, "Expiration Time:{0},System Time:{1}", new Object[]{DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", c.getOrdersToBeCancelled().get(key).getExpireTime()), DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", System.currentTimeMillis())});
                            synchronized (c.lockOrdersToBeCancelled) {
                                if (c.getOrdersToBeCancelled().get(key).getExpireTime() < System.currentTimeMillis() && c.getOrdersToBeCancelled().get(key).getOrigEvent().getOrdReference().compareTo(orderReference) == 0) {
                                    //logger.log(Level.INFO, "cancelExpiredOrders Account: {0}, Symbol:{1}, OrderID:{2}", new Object[]{c.getAccountName(), Parameters.symbol.get(c.getOrders().get(key).getParentSymbolID() - 1).getSymbol(), key});
                                    logger.log(Level.FINE, "307,ExitExpiration,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(c.getOrdersToBeCancelled().get(key).getSymbolid()).getDisplayname() + delimiter + key});
                                    c.getWrapper().cancelOrder(c, key);
                                    if ((c.getOrders().get(key).getParentOrderSide() == EnumOrderSide.BUY || c.getOrders().get(key).getParentOrderSide() == EnumOrderSide.SHORT)
                                            && (c.getOrders().get(key).getChildStatus() != EnumOrderStatus.COMPLETEFILLED)) {
                                        c.getOrdersMissed().add(key);
                                    }

                                    temp.add(key); //holds all orders that have now been cancelled
                                    synchronized (c.lockOrdersToBeCancelled) {
                                        symbols.add(c.getOrdersToBeCancelled().get(key).getSymbolid());
                                    }

                                }
                            }
                        }
                        for (int ordersToBeDeleted : temp) {
                            synchronized (c.lockOrdersToBeCancelled) {
                                c.getOrdersToBeCancelled().remove(ordersToBeDeleted);
                                logger.log(Level.FINE, "307,ExitOrderCancellationQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + ordersToBeDeleted});
                            }
                            //c.getOrdersInProgress().remove(ordersToBeDeleted); This is being removed once we get acknowledgement of cancellation from api

                        }
                        //We place cancellation orders if the order being cancelled is a combo leg

                        for (int symbolsToBeDeleted : symbols) {
                            Set activeOrders = c.getActiveOrders().get(new Index(orderReference, symbolsToBeDeleted));
                            Iterator iter = activeOrders.iterator();
                            while (iter.hasNext()) {
                                BeanOrderInformation activeOrder = (BeanOrderInformation) iter.next();
                                if (temp.contains(activeOrder.getOrderID())) {
                                    logger.log(Level.FINE, "307,DynamicQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(activeOrder.getSymbolid()).getDisplayname() + delimiter + activeOrder.getOrderID()});
                                    //logger.log(Level.FINE, "Expired symbols being deleted from active orders queue. Symbol :{0},OrderID: {1} ", new Object[]{symbolsToBeDeleted, activeOrder.getOrderID()});
                                    iter.remove();
                                }
                            }
                        }
                    }
                }
            }
        }
    };
    
    ActionListener hastenCloseOut = new ActionListener() { //call this every 1 second
        @Override
        public void actionPerformed(ActionEvent e) {
            for (BeanConnection c : Parameters.connection) {
                if (c.getOrdersToBeFastTracked().size() > 0) {
                    ArrayList<Integer> temp = new ArrayList();
                    ArrayList<Integer> symbols = new ArrayList();
                    for (Integer key : c.getOrdersToBeFastTracked().keySet()) {
                        BeanOrderInformation boi = c.getOrdersToBeFastTracked().get(key);
                        if (boi.getExpireTime() < System.currentTimeMillis()
                                && (c.getOrders().get(key).getChildStatus() != EnumOrderStatus.COMPLETEFILLED)
                                && c.getOrdersToBeFastTracked().get(key).getOrigEvent().getOrdReference().compareTo(orderReference) == 0) {
                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Hasten CloseOut, Symbol:{2}, OrderID:{3}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(c.getOrders().get(key).getParentSymbolID() - 1).getSymbol(), key});
                            boi.getOrigEvent().setLimitPrice(0);
                            boi.getOrigEvent().setTriggerPrice(0);
                            boi.getOrigEvent().setOrderStage(EnumOrderStage.INIT);
                            boi.getOrigEvent().setExpireTime(0);
                            boi.getOrigEvent().setOrderType(EnumOrderType.MKT);
                            int connectionid = Parameters.connection.indexOf(c);
                            synchronized (lockLinkedAction) {
                                //ArrayList<LinkedAction> cancelRequests = getCancellationRequestsForTracking().get(connectionid);
                                //cancelRequests.add(new LinkedAction(c, key, boi.getOrigEvent(), EnumLinkedAction.PROPOGATE));
                                getCancellationRequestsForTracking().get(connectionid).add(new LinkedAction(c, key, boi.getOrigEvent(), EnumLinkedAction.PROPOGATE));
                                logger.log(Level.FINE, "307, LinkedActionAdded,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "PROPOGATE" + delimiter + boi.getOrigEvent().getInternalorder()+delimiter+boi.getOrigEvent().getSymbolBean().getDisplayname()});
                                c.getWrapper().cancelOrder(c, key);

                                //parentorder.addOrdersToBeRetried(boi.getSymbolid(), c, boi.getOrigEvent());
                                //fastClose(c, key);
                                temp.add(key); //holds all orders that have now been cancelled
                                //symbols.add(c.getOrdersToBeFastTracked().get(key).getSymbolid());
                                lockLinkedAction.notifyAll();
                            }
                        }
                    }
                    for (int ordersToBeDeleted : temp) {
                        c.getOrdersToBeFastTracked().remove(ordersToBeDeleted);
                        logger.log(Level.FINE, "307,HastenQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + ordersToBeDeleted});

                    }
                    for (int symbolsToBeDeleted : symbols) {
                        Set activeOrders = c.getActiveOrders().get(new Index(orderReference, symbolsToBeDeleted));
                        Iterator iter = activeOrders.iterator();
                        while (iter.hasNext()) {
                            BeanOrderInformation activeOrder = (BeanOrderInformation) iter.next();
                            if (temp.contains(activeOrder.getOrderID())) {
                                logger.log(Level.FINE, "307,DynamicQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(activeOrder.getSymbolid()).getDisplayname() + delimiter + activeOrder.getOrderID()});
                                iter.remove();
                            }
                        }
                    }

                }
            }
        }
    };
    TimerTask runGetOrderStatus = new TimerTask() {
        @Override
        public void run() {
            getOpenOrders();
        }
    };

    //</editor-fold>
    public void getOpenOrders() {
        com.ib.client.ExecutionFilter filter = new com.ib.client.ExecutionFilter();
        for (BeanConnection c : Parameters.connection) {
            filter.m_clientId = c.getClientID();
            if ("".compareTo(this.lastExecutionRequestTime) != 0) {
                filter.m_time = this.lastExecutionRequestTime;
            }
            this.lastExecutionRequestTime = DateUtil.getFormatedDate("yyyyMMdd-HH:mm:ss", new Date().getTime());
            c.getWrapper().requestOpenOrders();
            c.getWrapper().requestExecutionDetails(filter);
        }
    }
    
    public void squareAllPositions(BeanConnection c, int id, String strategy) {//works on parent order
        Index ind = new Index(strategy, id);
        int position = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
        //       ArrayList <Contract> contracts = c.getWrapper().createContract(id);
        HashMap<Integer, Order> orders = new HashMap<>();
        ArrayList<Integer> orderids;
        OrderEvent event = new OrderEvent(new Object());
        if (position > 0) {
            int internalorderid = s.getInternalOrderID();
            event = new OrderEvent(new Object(), internalorderid, internalorderid, Parameters.symbol.get(id), EnumOrderSide.SELL, EnumOrderReason.REGULAREXIT, EnumOrderType.MKT, Math.abs(position), 0D, 0D, orderReference, 0, EnumOrderStage.INIT, 0, 0D, true, "DAY", false, "", "", null);
            orders = c.getWrapper().createOrder(event);
            logger.log(Level.INFO, "309,SquareAllPositions,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + "SELL"});

        } else if (position < 0) {
            int internalorderid = s.getInternalOrderID();
            event = new OrderEvent(new Object(), internalorderid, internalorderid, Parameters.symbol.get(id), EnumOrderSide.COVER, EnumOrderReason.REGULAREXIT, EnumOrderType.MKT, Math.abs(position), 0D, 0D, orderReference, 0, EnumOrderStage.INIT, 0, 0D, true, "DAY", false, "", "", null);
            orders = c.getWrapper().createOrder(event);
            logger.log(Level.INFO, "309,SquareAllPositions,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + "BUY"});

        }
        //update orderid structures
        //activeOrders - done
        //ordersInProgress - done
        //orderSymbols -done
        //orderMapping -done
        //ordersToBeCancelled - done
        //ordersToBeFastTracked - not needed
        //ordersToBeRetried - not needed
        //ordersMissed - not needed
        ArrayList<SymbolOrderMap> symbolOrders = new ArrayList<>();
        ArrayList<Integer> linkedOrderIds = new ArrayList<>();
        orderids = c.getWrapper().placeOrder(c, event, orders);
        for (int orderid : orderids) {
            c.getActiveOrders().put(new Index(orderReference, id), new BeanOrderInformation(id, c, orderid, 0, event));
            symbolOrders.add(new SymbolOrderMap(id, orderid));
            linkedOrderIds.add(orderid);
            if (!c.getOrdersInProgress().contains(orderid)) {
                c.getOrdersInProgress().add(orderid);
                logger.log(Level.FINE, "307,OrderProgressQueueAdded,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid+delimiter+ Parameters.symbol.get(id).getDisplayname()});

            }
        }
        c.getOrdersSymbols().put(new Index(orderReference, id), symbolOrders);
        synchronized (c.lockOrderMapping) {
            c.getOrderMapping().put(new Index(orderReference, event.getInternalorder()), orderids);
        }


    }

    public Boolean zilchOpenOrders(BeanConnection c, int id, String strategy) {
        Index ind = new Index(strategy, id);
        Boolean zilchorders = c.getOrdersSymbols().get(ind) == null ? Boolean.TRUE : Boolean.FALSE;
        if (!zilchorders) {
            ArrayList<SymbolOrderMap> orderMaps = c.getOrdersSymbols().get(ind);
            for (SymbolOrderMap orderMap : orderMaps) {
                if (orderMap.externalOrderId > 0) {
                    //logger.log(Level.INFO, "{0},{1},Execution Manager, Open Orders,Open Order:{2}", new Object[]{c.getAccountName(), strategy, orderMap.externalOrderId});
                    return Boolean.FALSE;
                }
            }
            zilchorders = Boolean.TRUE;
        }
        return zilchorders;
    }

    public void cancelOpenOrders(BeanConnection c, int id, String strategy) {
        Index ind = new Index(strategy, id);
        ArrayList<SymbolOrderMap> orderMaps = c.getOrdersSymbols().get(ind);
        ArrayList<Integer> temp = new ArrayList();
        ArrayList<Integer> symbols = new ArrayList();
        Boolean orderNotCancelled = true;
        for (SymbolOrderMap orderMap : orderMaps) {
            if (orderMap.externalOrderId > 0) {
                //if order exists and open ordType="Buy" or "Short"
                //check an earlier cancellation request is not pending and if all ok then cancel
                for (Integer key : c.getOrdersToBeFastTracked().keySet()) {
                    if (key == orderMap.externalOrderId) {
                        temp.add(key);
                        symbols.add(c.getOrdersToBeFastTracked().get(key).getSymbolid());
                        //logger.log(Level.INFO, "{0},{1},Execution Manager,Cancellation Request Received. Order will be removed from hasten queue , Symbol:{2}, OrderID:{3}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(c.getOrders().get(key).getParentSymbolID() - 1).getSymbol(), key});
                    }
                }
                if (!c.getOrders().get(orderMap.externalOrderId).isCancelRequested() && orderNotCancelled) {
                    //logger.log(Level.INFO, "{0},{1},Execution Manager,Cancellation Request being sent to broker, Symbol: {2}, OrderID: {3}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(c.getOrders().get(orderMap.externalOrderId).getParentSymbolID() - 1).getSymbol(), orderMap.externalOrderId});
                    logger.log(Level.INFO, "309,CancelOpenOrders,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderMap.externalOrderId});
                    c.getWrapper().cancelOrder(c, orderMap.externalOrderId);
                    orderNotCancelled = false;
                }
            }
            for (int ordersToBeDeleted : temp) {
                logger.log(Level.FINE, "307,HastenQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + ordersToBeDeleted});
                c.getOrdersToBeFastTracked().remove(ordersToBeDeleted);

            }
            for (int symbolsToBeDeleted : symbols) {
                Set activeOrders = c.getActiveOrders().get(new Index(orderReference, symbolsToBeDeleted));
                Iterator iter = activeOrders.iterator();
                while (iter.hasNext()) {
                    BeanOrderInformation activeOrder = (BeanOrderInformation) iter.next();
                    if (temp.contains(activeOrder.getOrderID())) {
                        //logger.log(Level.FINE, "Symbols being deleted from active orders queue. Symbol :{0},OrderID: {1} ", new Object[]{symbolsToBeDeleted, activeOrder.getOrderID()});
                        logger.log(Level.FINE, "307,DynamicQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(activeOrder.getSymbolid()).getDisplayname() + delimiter + activeOrder.getOrderID()});
                        iter.remove();
                    }
                }
            }
        }
    }

    private boolean updateFilledOrders(BeanConnection c, int orderid, int filled, double avgFillPrice, double lastFillPrice) {
        OrderBean ob = c.getOrders().get(orderid);
        int internalorderid = ob.getInternalOrderID();
        int parentid = ob.getParentSymbolID() - 1;
        int childid = ob.getChildSymbolID() - 1;
        boolean combo = parentid != childid;
        String strategy = ob.getOrderReference();
        Index ind = new Index(strategy, parentid);
        ArrayList newComboFills;
        int cpid = 0;


        Boolean orderProcessed = false;
        //update OrderBean
        int fill = filled - ob.getChildFillSize();
        if (lastFillPrice == 0 && fill > 0) { //execution info from execDetails. calculate lastfillprice
            lastFillPrice = (filled * avgFillPrice - ob.getChildFillSize() * ob.getFillPrice()) / fill;
            //logger.log(Level.FINE, "{0},{1},Execution Manager,Calculated incremental fill price, Fill Size: {2}, Fill Price: {3}", new Object[]{c.getAccountName(), orderReference, fill, lastFillPrice});
        }

        ArrayList priorFillDetails = new ArrayList();
        if (fill > 0) {
            //1. Update orderBean
            priorFillDetails = comboFillSize(c, internalorderid, parentid);
            ob.setChildFillSize(filled);
            ob.setFillPrice(avgFillPrice);
            ob.setChildStatus(EnumOrderStatus.COMPLETEFILLED);
            if (parentid != childid) {
                newComboFills = comboFillSize(c, ob.getInternalOrderID(), parentid);
                if (((int) newComboFills.get(0)) == ob.getParentOrderSize()) {
                } else {
                    ob.setParentStatus(EnumOrderStatus.PARTIALFILLED);
                }
            }
            //2. Initialize BeanPosition
            BeanPosition p = c.getPositions().get(ind) == null ? new BeanPosition() : c.getPositions().get(ind);
            if (c.getPositions().get(ind) == null) {//add combos legs
                for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                    p.getChildPosition().add(new BeanChildPosition(entry.getKey().getSerialno() - 1, entry.getValue()));
                }
            }
            p.setSymbolid(parentid);
            //3. Update BeanPosition
            if (p.getChildPosition().isEmpty()) {
                //3a. Update Single Leg Position
                int origposition = p.getPosition();
                if (c.getOrders().get(orderid).getChildOrderSide() == EnumOrderSide.SELL || c.getOrders().get(orderid).getChildOrderSide() == EnumOrderSide.SHORT || c.getOrders().get(orderid).getParentOrderSide() == EnumOrderSide.TRAILSELL) {
                    fill = -fill;
                    //logger.log(Level.FINE, "Reversed fill sign as sell or short. Symbol:{1}, Fill={2}", new Object[]{Parameters.symbol.get(parentid).getSymbol(), fill});
                }
                double realizedPL = (origposition + fill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + fill * lastFillPrice) * pointValue + p.getProfit() : p.getProfit();
                double positionPrice = (origposition + fill) == 0 ? 0 : (p.getPosition() * p.getPrice() + fill * lastFillPrice) / (origposition + fill);
                //logger.log(Level.FINE, "308,inStratCompleteFill,{0}",new Object[]{c.getAccountName()+delimiter+ orderReference+delimiter+ Parameters.symbol.get(parentid).getDisplayname()+delimiter+ origposition +delimiter+ fill+delimiter+ positionPrice+delimiter+ realizedPL});
                logger.log(Level.FINE, "308,inStratCompleteFill,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(parentid).getDisplayname() + delimiter + p.getPosition() + delimiter + p.getPrice() + delimiter + fill + delimiter + lastFillPrice});
                p.setPointValue(pointValue);
                p.setPrice(positionPrice);
                p.setProfit(realizedPL);
                p.setPosition(origposition + fill);
                c.getPositions().put(ind, p);
            } else {
                //3b. Update combo position
                ArrayList startingLowerBoundPosition = lowerBoundParentPosition(p);
                ArrayList startingUpperBoundPosition = upperBoundParentPosition(p);
                //3b.1 Update Child legs
                ArrayList<BeanChildPosition> childPositions = p.getChildPosition();
                for (BeanChildPosition cp : childPositions) {
                    if (cp.getSymbolid() == childid) {
                        //update this childposition
                        int origposition = cp.getPosition();
                        if (c.getOrders().get(orderid).getChildOrderSide() == EnumOrderSide.SELL || c.getOrders().get(orderid).getChildOrderSide() == EnumOrderSide.SHORT || c.getOrders().get(orderid).getParentOrderSide() == EnumOrderSide.TRAILSELL) {
                            fill = -fill;
                            //logger.log(Level.FINE, "Reversed fill sign as sell or short. Symbol:{1}, Fill={2}", new Object[]{Parameters.symbol.get(childid).getSymbol(), fill});
                        }
                        double realizedPL = (origposition * cp.getPrice() + fill * lastFillPrice) * pointValue;
                        double positionPrice = (origposition + fill) == 0 ? 0 : (cp.getPosition() * cp.getPrice() + fill * lastFillPrice) / (origposition + fill);
                        //logger.log(Level.FINE, "{0},{1},Execution Manager,P&L Calculated,Symbol:{2},Position:{3},Position Price:{4},Realized P&L:{5}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(childid).getSymbol(), origposition + fill, positionPrice, realizedPL});
                        cp.setPointValue(pointValue);
                        cp.setPrice(positionPrice);
                        cp.setProfit(realizedPL);
                        cp.setPosition(origposition + fill);
                        cpid = p.getChildPosition().indexOf(cp);
                        c.getPositions().put(ind, p);
                    }
                }
                //3b.2 Update Parent Legs
                ArrayList lowerBoundPosition = lowerBoundParentPosition(p);
                ArrayList upperBoundPosition = upperBoundParentPosition(p);
                int parentNewPosition = ob.getParentOrderSide().equals(EnumOrderSide.BUY) || ob.getParentOrderSide().equals(EnumOrderSide.SHORT) ? (int) lowerBoundPosition.get(0) : (int) upperBoundPosition.get(0);
                if (parentNewPosition != p.getPosition()) {//there is a combo parent fill
                    int origposition = p.getPosition();
                    int parentfill = parentNewPosition - p.getPosition();
                    double parentfillprice = 0D;
                    double positionPrice = p.getPrice();
                    if (ob.getParentOrderSide().equals(EnumOrderSide.BUY) || ob.getParentOrderSide().equals(EnumOrderSide.SHORT)) {
                        parentfillprice = ((double) lowerBoundPosition.get(1) * (int) lowerBoundPosition.get(0) - p.getPosition() * p.getPrice()) / parentfill;
                        positionPrice = (double) lowerBoundPosition.get(1);
                    } else {
                        if (origposition + parentfill == 0) {
                            parentfillprice = ((Double) upperBoundPosition.get(1) - p.getPosition() * p.getPrice()) / parentfill;
                            positionPrice = 0;
                        } else {
                            parentfillprice = ((Double) upperBoundPosition.get(1) * (Integer) upperBoundPosition.get(0) - p.getPosition() * p.getPrice()) / parentfill;
                            positionPrice = (double) upperBoundPosition.get(1);

                        }
                    }
                    double realizedPL = (origposition + parentfill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + parentfill * parentfillprice) * pointValue + p.getProfit() : p.getProfit();
                    p.setPrice(positionPrice);
                    p.setPosition(parentNewPosition);
                    p.setPointValue(1);
                    p.setProfit(realizedPL);

                }
            }

            ArrayList<SymbolOrderMap> orderMaps = c.getOrdersSymbols().get(ind);
            if (orderMaps.size() > 0) {
                Iterator entries = orderMaps.iterator();
                while (entries.hasNext()) {
                    SymbolOrderMap orderMap = (SymbolOrderMap) entries.next();
                    if (orderMap.externalOrderId == orderid) {
                        entries.remove();
                        orderProcessed = true;
                    }
                }
            }
            if (combo) {
                logger.log(Level.INFO, "308,inStratCompleteFillParent,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(parentid).getDisplayname() + delimiter + p.getPosition() + delimiter + p.getPrice()});
                logger.log(Level.INFO, "308,inStratCompleteFillChild,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(childid).getDisplayname() + delimiter + p.getChildPosition().get(cpid).getPosition() + delimiter + p.getChildPosition().get(cpid).getPrice() + delimiter + fill + delimiter + lastFillPrice});
            }

            //send email
            if (!combo) {
                Thread t = new Thread(new Mail(c.getOwnerEmail(), "Order Completely Executed. Account: " + c.getAccountName() + ", Strategy: " + strategy + ", Symbol: " + Parameters.symbol.get(parentid).getSymbol() + ", Fill Size: " + fill + ", Symbol Position: " + p.getPosition() + ", Fill Price: " + avgFillPrice, "Algorithm Alert - " + strategy.toUpperCase()));
                t.start();
            } else {
                Thread t = new Thread(new Mail(c.getOwnerEmail(), "Order Completely Executed. Account: " + c.getAccountName() + ", Strategy: " + strategy + ", Combo Symbol: " + Parameters.symbol.get(parentid).getSymbol() + ",Filled Child: " + Parameters.symbol.get(childid).getSymbol() + ", Child Fill Size: " + fill + ", Child Position: " + p.getChildPosition().get(cpid).getPosition() + ", Child Fill Price: " + avgFillPrice + ", Combo Position: " + p.getPosition() + ",Combo Price: " + p.getPrice(), "Algorithm Alert - " + strategy.toUpperCase()));
                t.start();
            }
            synchronized (c.lockOrdersToBeCancelled) {
                if (c.getOrdersToBeCancelled().containsKey(orderid) && c.getOrders().get(orderid).getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                    c.getOrdersToBeCancelled().remove(orderid); //remove filled orders from cancellation queue
                    logger.log(Level.FINE, "307,ExitOrderCancellationQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid+delimiter+Parameters.symbol.get(parentid).getDisplayname()});
                }
            }
            Set<BeanOrderInformation> boiSet = c.getActiveOrders().get(ind);
            Iterator iter = boiSet.iterator();
            while (iter.hasNext()) {
                BeanOrderInformation boi = (BeanOrderInformation) iter.next();
                if (boi.getOrderID() == orderid) {
                    logger.log(Level.FINE, "307,DynamicQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(boi.getSymbolid()).getDisplayname() + delimiter + boi.getOrderID()});
                    iter.remove();
                    break;
                }
            }

            if (c.getOrdersToBeFastTracked().containsKey(orderid) && c.getOrders().get(orderid).getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersToBeFastTracked().remove(orderid);
            }
            if (c.getOrdersInProgress().contains(orderid) && ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersInProgress().remove(Integer.valueOf(orderid));
                logger.log(Level.FINE, "307,OrderProgressQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid+delimiter+Parameters.symbol.get(parentid).getDisplayname()});
            }
            if (c.getOrdersMissed().contains(orderid) && ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersMissed().remove(Integer.valueOf(orderid));
                logger.log(Level.FINE, "307,OrderMissedQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid});
            }
            int tradeFill=0;
            switch(ob.getParentOrderSide()){
                case BUY:
                case SHORT:
                    tradeFill=filled;
                    break;
                case SELL:
                case COVER:
                    tradeFill=Math.abs(fill);
                    break;
                default:
                    break;
            }

            //For exits we send incremental fill = abs(fill) as tradeupdate ** for exits only ** aggregates fills.
            //For entry, there each fill with the same internal order id is updated.
            updateTrades(c, ob, p, tradeFill, avgFillPrice);
            if (ob.getParentOrderSide() == EnumOrderSide.SELL || ob.getParentOrderSide() == EnumOrderSide.COVER) {
                if (fill != 0) { //do not reduce open position count if duplicate message, in which case fill == 0
                    int connectionid = Parameters.connection.indexOf(c);
                    int tmpOpenPositionCount = this.getOpenPositionCount().get(connectionid);
                    int openpositioncount = tmpOpenPositionCount - 1;
                    this.getOpenPositionCount().set(connectionid, openpositioncount);
                    logger.log(Level.INFO, "302,OpenPosition,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + openpositioncount});
                }
            }
            //if this was a requested cancellation, fire any event if needed
            fireLinkedActions(c, orderid);
        }
        return orderProcessed;
    }

    private boolean updateCancelledOrders(BeanConnection c, int id, int orderid) {
        OrderBean ob = c.getOrders().get(orderid);
        int parentid = ob.getParentSymbolID() - 1;
        int internalorderid = ob.getInternalOrderID();
        boolean stubOrderPlaced=false;
        //ob.setCancelRequested(false);
        if (ob.getChildFillSize() > 0) {
            ob.setChildStatus(EnumOrderStatus.CANCELLEDPARTIALFILL);
        } else {
            ob.setChildStatus(EnumOrderStatus.CANCELLEDNOFILL);
        }

        //update orderid structures
        //orderMapping -not needed
        //ordersMissed - not needed
        //ordersToBeFastTracked - not needed        
        Index ind = new Index(ob.getOrderReference(), id);
        //orderSymbols -done
        ArrayList<SymbolOrderMap> orderMaps = c.getOrdersSymbols().get(ind);
        if (orderMaps.size() > 0) {
            Iterator entries = orderMaps.iterator();
            while (entries.hasNext()) {
                SymbolOrderMap orderMap = (SymbolOrderMap) entries.next();
                if (orderMap.externalOrderId == orderid) {
                    logger.log(Level.FINE, "307,DynamicQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + orderid});
                    entries.remove();
                }
            }
        }

        //ordersInProgress - done
        if (c.getOrdersInProgress().contains(new Integer(orderid))) {
            logger.log(Level.FINE, "307,OrderProgressQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid+delimiter+ Parameters.symbol.get(id).getDisplayname()});
            c.getOrdersInProgress().remove(new Integer(orderid));
        }

        //ordersToBeCancelled - done

        synchronized (c.lockOrdersToBeCancelled) {
            if (c.getOrdersToBeCancelled().containsKey(orderid)) {
                logger.log(Level.FINE, "307,ExitOrderCancellationQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid+delimiter+Parameters.symbol.get(parentid).getDisplayname()});
                c.getOrdersToBeCancelled().remove(orderid);
            }
        }
        //activeOrders - done
        Set<BeanOrderInformation> boiSet = c.getActiveOrders().get(new Index(ob.getOrderReference(), ob.getParentSymbolID() - 1));
        Iterator iterActiveOrders = boiSet.iterator();
        while (iterActiveOrders.hasNext()) {
            BeanOrderInformation boi = (BeanOrderInformation) iterActiveOrders.next();
            if (boi.getOrderID() == orderid) {
                logger.log(Level.FINE, "307,DynamicQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(boi.getSymbolid()).getDisplayname() + delimiter + boi.getOrderID()});
                iterActiveOrders.remove();
                break;
            }
        }
        //ordersToBeRetried - not needed. Orders to be retried is cleansed only if there are linked orders
        //Reduce position count if needed
        if (c.getOrders().get(orderid).getParentOrderSide() == EnumOrderSide.BUY || c.getOrders().get(orderid).getParentOrderSide() == EnumOrderSide.SHORT) {
            //reduce open position count
            int connectionid = Parameters.connection.indexOf(c);
            ArrayList<Integer> cancelledOrdersForConnection = cancelledOrdersAcknowledgedByIB.get(connectionid);
            if (!cancelledOrdersForConnection.contains(internalorderid)) {
                cancelledOrdersForConnection.add(internalorderid);
                cancelledOrdersAcknowledgedByIB.set(connectionid, cancelledOrdersForConnection);
                int tmpOpenPositionCount = this.getOpenPositionCount().get(connectionid);
                int openpositioncount = tmpOpenPositionCount - 1;
                this.getOpenPositionCount().set(connectionid, openpositioncount);
                logger.log(Level.INFO, "302,OpenPosition,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + openpositioncount});

            }
        }

        //Process combo stubs
        if (ob.getChildSymbolID() != ob.getParentSymbolID() && (ob.getParentOrderSide().equals(EnumOrderSide.BUY) || ob.getParentOrderSide().equals(EnumOrderSide.SHORT))) {
            if (c.getOrdersSymbols().get(ind).isEmpty()) {
                reduceStub(c, ob.getInternalOrderID(), parentid, ob.getParentOrderSide());
                stubOrderPlaced=true;
            } else {
                for (SymbolOrderMap orderMap : c.getOrdersSymbols().get(ind)) {
                    OrderBean ob1 = c.getOrders().get(orderMap.externalOrderId);
                    if (ob1.getChildStatus().equals(EnumOrderStatus.SUBMITTED) || ob1.getChildStatus().equals(EnumOrderStatus.ACKNOWLEDGED) && !ob1.isCancelRequested()) {
                        this.cancelOpenOrders(c, parentid, orderReference);
                    }
                }

            }
        } else if (ob.getChildSymbolID() != ob.getParentSymbolID() && (ob.getParentOrderSide().equals(EnumOrderSide.SELL) || ob.getParentOrderSide().equals(EnumOrderSide.COVER))) {
            if (c.getOrdersSymbols().get(ind).isEmpty()) {
                completeStub(c, ob.getInternalOrderID(), parentid, orderid);
                stubOrderPlaced=true;
            } else {
                for (SymbolOrderMap orderMap : c.getOrdersSymbols().get(ind)) {
                    if (c.getOrders().get(orderMap.externalOrderId).equals(EnumOrderStatus.SUBMITTED) || c.getOrders().get(orderMap.externalOrderId).equals(EnumOrderStatus.ACKNOWLEDGED));
                    this.cancelOpenOrders(c, parentid, orderReference);
                }
            }
        }
        if(!stubOrderPlaced){
        fireLinkedActions(c, orderid);
        }else{
            removeLinkedActions(c,orderid);
        }
        return true;
    }

    private boolean updatePartialFills(BeanConnection c, int orderid, int filled, double avgFillPrice, double lastFillPrice) {
        OrderBean ob = c.getOrders().get(orderid);
        int parentid = ob.getParentSymbolID() - 1;
        int internalOrderID = ob.getInternalOrderID();
        int childid = ob.getChildSymbolID() - 1;
        String strategy = ob.getOrderReference();
        Index ind = new Index(strategy, parentid);
        boolean combo = parentid != childid;
        int cpid = 0;

        //identify incremental fill
        int fill = filled - ob.getChildFillSize();
        if (lastFillPrice == 0 && fill > 0) { //execution info from execDetails. calculate lastfillprice
            lastFillPrice = (filled * avgFillPrice - ob.getChildFillSize() * ob.getFillPrice()) / fill;
        }
        ArrayList priorFillDetails = new ArrayList();
        if (fill > 0) {
            //1. Update orderbean
            priorFillDetails = comboFillSize(c, internalOrderID, parentid);
            ob.setChildFillSize(filled);
            ob.setFillPrice(avgFillPrice);
            ob.setChildStatus(EnumOrderStatus.PARTIALFILLED);
            //2. Initialize BeanPosition
            BeanPosition p = c.getPositions().get(ind) == null ? new BeanPosition() : c.getPositions().get(ind);
            if (c.getPositions().get(ind) == null) {
                //add combos information 
                for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                    p.getChildPosition().add(new BeanChildPosition(entry.getKey().getSerialno() - 1, entry.getValue()));
                }
            }

            p.setSymbolid(parentid);
            p.setPointValue(pointValue);

            //3. Update Bean Positions
            //3a. Update Single Leg Position
            if (p.getChildPosition().isEmpty()) {//single leg position
                int origposition = p.getPosition();
                if (c.getOrders().get(orderid).getChildOrderSide() == EnumOrderSide.SELL || c.getOrders().get(orderid).getChildOrderSide() == EnumOrderSide.SHORT || c.getOrders().get(orderid).getParentOrderSide() == EnumOrderSide.TRAILSELL) {
                    fill = -fill;
                }
                //logger.log(Level.FINE, "{0},{1},Execution Manager,Updated positions on partial fill,Symbol:{2}, Incremental Fill Reported:{3},Total Fill={4},Position within Program={5}, Side={6}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(childid).getSymbol(), fill, filled, ob.getChildFillSize(), ob.getParentOrderSide()});
                double realizedPL = (origposition + fill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + fill * lastFillPrice) * pointValue + p.getProfit() : p.getProfit();
                double positionPrice = (origposition + fill) == 0 ? 0 : (p.getPosition() * p.getPrice() + fill * lastFillPrice) / (origposition + fill);
                logger.log(Level.FINE, "308,inStratPartialFill,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(parentid).getDisplayname() + delimiter + p.getPosition() + delimiter + p.getPrice() + delimiter + fill + delimiter + lastFillPrice});
                p.setPrice(positionPrice);
                p.setProfit(realizedPL);
                p.setPosition(origposition + fill);
                c.getPositions().put(ind, p);
            } else {
                //3b. Update Combo Position
                ArrayList startingLowerBoundPosition = lowerBoundParentPosition(p);
                ArrayList startingUpperBoundPosition = upperBoundParentPosition(p);
                ArrayList<BeanChildPosition> childPositions = p.getChildPosition();
                //3b.1 Update Child legs
                for (BeanChildPosition cp : childPositions) {
                    if (cp.getSymbolid() == childid) {
                        //update this childposition
                        int origposition = cp.getPosition();
                        if (c.getOrders().get(orderid).getChildOrderSide() == EnumOrderSide.SELL || c.getOrders().get(orderid).getChildOrderSide() == EnumOrderSide.SHORT || c.getOrders().get(orderid).getParentOrderSide() == EnumOrderSide.TRAILSELL) {
                            fill = -fill;
                            //logger.log(Level.FINE, "Reversed fill sign as sell or short. Symbol:{1}, Fill={2}", new Object[]{Parameters.symbol.get(childid).getSymbol(), fill});
                        }
                        double realizedPL = (origposition * cp.getPrice() + fill * lastFillPrice) * pointValue;
                        double positionPrice = (origposition + fill) == 0 ? 0 : (cp.getPosition() * cp.getPrice() + fill * lastFillPrice) / (origposition + fill);
                        //logger.log(Level.FINE, "{0},{1},Execution Manager,P&L Calculated,Symbol:{2},Position:{3},Position Price:{4},Realized P&L:{5}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(childid).getSymbol(), origposition + fill, positionPrice, realizedPL});
                        cp.setPointValue(pointValue);
                        cp.setPrice(positionPrice);
                        cp.setProfit(realizedPL);
                        cp.setPosition(origposition + fill);
                        cpid = p.getChildPosition().indexOf(cp);
                        c.getPositions().put(ind, p);
                    }
                }
                //3b.2 update parent legs
                ArrayList lowerBoundPosition = lowerBoundParentPosition(p);
                ArrayList upperBoundPosition = upperBoundParentPosition(p);
                int parentNewPosition = ob.getParentOrderSide().equals(EnumOrderSide.BUY) || ob.getParentOrderSide().equals(EnumOrderSide.SHORT) ? (int) lowerBoundPosition.get(0) : (int) upperBoundPosition.get(0);
                if (parentNewPosition != p.getPosition()) {//there is a combo parent fill
                    int origposition = p.getPosition();
                    int parentfill = parentNewPosition - p.getPosition();
                    double parentfillprice = 0D;
                    double positionPrice;
                    if (ob.getParentOrderSide().equals(EnumOrderSide.BUY) || ob.getParentOrderSide().equals(EnumOrderSide.SHORT)) {
                        parentfillprice = ((double) lowerBoundPosition.get(1) * (int) lowerBoundPosition.get(0) - p.getPosition() * p.getPrice()) / parentfill;
                        positionPrice = (double) lowerBoundPosition.get(1);
                    } else {
                        parentfillprice = ((double) upperBoundPosition.get(1) * (int) upperBoundPosition.get(0) - p.getPosition() * p.getPrice()) / parentfill;
                        positionPrice = (double) upperBoundPosition.get(1);
                    }
                    double realizedPL = (origposition + parentfill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + parentfill * parentfillprice) * pointValue + p.getProfit() : p.getProfit();
                    p.setPrice(positionPrice);
                    p.setPosition(parentNewPosition);
                    p.setPointValue(1);
                    p.setProfit(realizedPL);

                }
            }
            if (ob.getChildOrderSize() - ob.getChildFillSize() == 0) {
                ob.setChildStatus(EnumOrderStatus.COMPLETEFILLED);
                //logger.log(Level.FINE, "DEBUG: Child Symbol: {0},ChildStatus:{1}", new Object[]{Parameters.symbol.get(childid).getSymbol(), ob.getChildStatus()});
            }

            for (Integer linkedOrderId : TradingUtil.getLinkedOrderIds(orderid, c)) {
                if(c.getOrders().get(linkedOrderId)!=null){
                c.getOrders().get(linkedOrderId).setParentStatus(EnumOrderStatus.PARTIALFILLED);
                }
                }

            if (combo) {
                logger.log(Level.INFO, "308,inStratPartialFillParent,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(parentid).getDisplayname() + delimiter + p.getPosition() + delimiter + p.getPrice()});
                logger.log(Level.INFO, "308,inStratPartialFillChild,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(childid).getDisplayname() + delimiter + p.getChildPosition().get(cpid).getPosition() + delimiter + p.getChildPosition().get(cpid).getPrice() + delimiter + fill + delimiter + lastFillPrice});
            }
            //send email
            if (!combo) {
                Thread t = new Thread(new Mail(c.getOwnerEmail(), "Order Partially Executed. Account: " + c.getAccountName() + ", Strategy: " + strategy + ", Symbol: " + Parameters.symbol.get(parentid).getSymbol() + ", Fill Size: " + fill + ", Symbol Position: " + p.getPosition() + ", Fill Price: " + avgFillPrice, "Algorithm Alert - " + strategy.toUpperCase()));
                t.start();
            } else {
                Thread t = new Thread(new Mail(c.getOwnerEmail(), "Order Partially Executed. Account: " + c.getAccountName() + ", Strategy: " + strategy + ", Combo Symbol: " + Parameters.symbol.get(parentid).getSymbol() + ",Filled Child: " + Parameters.symbol.get(childid).getSymbol() + ", Child Fill Size: " + fill + ", Child Position: " + p.getChildPosition().get(cpid).getPosition() + ", Child Fill Price: " + avgFillPrice + ", Combo Position: " + p.getPosition() + ",Combo Price: " + p.getPrice(), "Algorithm Alert - " + strategy.toUpperCase()));
                t.start();
            }

            synchronized (c.lockOrdersToBeCancelled) {
                if (c.getOrdersToBeCancelled().containsKey(orderid) && c.getOrders().get(orderid).getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                    logger.log(Level.FINE, "307,ExitORderCancellationQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid+delimiter+Parameters.symbol.get(parentid).getDisplayname()});
                    c.getOrdersToBeCancelled().remove(orderid); //remove filled orders from cancellation queue
                }
            }

            Set<BeanOrderInformation> boiSet = c.getActiveOrders().get(ind);
            Iterator iterActiveOrders = boiSet.iterator();
            while (iterActiveOrders.hasNext()) {
                BeanOrderInformation boi = (BeanOrderInformation) iterActiveOrders.next();
                if (boi.getOrderID() == orderid && c.getOrders().get(orderid).getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                    logger.log(Level.FINE, "307,DynamicQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(boi.getSymbolid()).getDisplayname() + delimiter + boi.getOrderID()});
                    iterActiveOrders.remove();
                    break;
                }
            }

            if (c.getOrdersToBeFastTracked().containsKey(orderid) && c.getOrders().get(orderid).getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersToBeFastTracked().remove(orderid);
                //logger.log(Level.FINE, "{0},{1},Execution Manager,Removed order id from fast track queue, OrderID: {2}", new Object[]{c.getAccountName(), orderReference, orderid});
            }

            if (c.getOrdersInProgress().contains(orderid) && ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersInProgress().remove(Integer.valueOf(orderid));
                logger.log(Level.FINE, "307,OrderProgressQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid+delimiter+ Parameters.symbol.get(parentid).getDisplayname()});
            }

            if (c.getOrdersMissed().contains(orderid) && ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersMissed().remove(Integer.valueOf(orderid));
                logger.log(Level.FINE, "307,OrderMissedQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid});
            }
            if (ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                ArrayList<SymbolOrderMap> orderMaps = c.getOrdersSymbols().get(ind);
                if (orderMaps.size() > 0) {
                    Iterator entries = orderMaps.iterator();
                    while (entries.hasNext()) {
                        SymbolOrderMap orderMap = (SymbolOrderMap) entries.next();
                        if (orderMap.externalOrderId == orderid) {
                            entries.remove();
                            //logger.log(Level.FINE, "{0},{1},Execution Manager,Removed orders from symbolordermap on complete child fill. Symbol: {2},Order ID removed: {3}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(childid).getSymbol(), orderid});
                        }
                    }
                }
            }
            int tradeFill=0;
            switch(ob.getParentOrderSide()){
                case BUY:
                case SHORT:
                    tradeFill=filled;
                    break;
                case SELL:
                case COVER:
                    tradeFill=Math.abs(fill);
                    break;
                default:
                    break;
            }
            //For exits we send incremental fill = abs(fill) as tradeupdate ** for exits only ** aggregates fills.
            //For entry, there each fill with the same internal order id is updated.
            updateTrades(c, ob, p, tradeFill, avgFillPrice);
            //if this was a requested cancellation, fire any event if needed
            fireLinkedActions(c, orderid);
        }
        return true;
    }

    private void updateTrades(BeanConnection c, OrderBean ob, BeanPosition p, int filled, double avgFillPrice) {
        try{
        int parentid = ob.getParentSymbolID() - 1;
        int childid = ob.getChildSymbolID() - 1;
        int orderid = ob.getOrderID();
        int tempinternalOrderIDEntry = 0;
        boolean entry = ob.getParentOrderSide() == EnumOrderSide.BUY || ob.getParentOrderSide() == EnumOrderSide.SHORT ? true : false;
        logger.log(Level.INFO,"311,Debugging_UpdateTrades,{0}",new Object[]{c.getAccountName()+delimiter+orderReference+delimiter+Parameters.symbol.get(parentid).getDisplayname()+delimiter+filled+delimiter+ob.getInternalOrderID()+delimiter+ob.getInternalOrderIDEntry()});
        //update trades
        //update trades if order is completely filled. For either combo or regular orders, this will be reflected
        if (entry) {
            //entry order. check if trade object exists
            tempinternalOrderIDEntry = ob.getInternalOrderID();
            logger.log(Level.INFO,"311,EntryID,{0}",new Object[]{c.getAccountName()+delimiter+orderReference+delimiter+orderid+delimiter+tempinternalOrderIDEntry});
        } else {
            int firstOpenOrder=getFirstInternalOpenOrder(parentid, ob.getParentOrderSide(), c.getAccountName());
            tempinternalOrderIDEntry = ob.getInternalOrderIDEntry()>0?Math.min(ob.getInternalOrderIDEntry(), firstOpenOrder):firstOpenOrder; //we pick the miniumn as a trade might have been reinstated This will cause the orderid reported by the algo > acutal order id availabe in trades() in the oms
            logger.log(Level.INFO,"311,ExitMatchingEntryIDFound,{0}",new Object[]{c.getAccountName()+delimiter+orderReference+delimiter+orderid+delimiter+tempinternalOrderIDEntry+delimiter+firstOpenOrder});
        }
        if (p.getChildPosition().isEmpty()) {//single leg order
            Trade tempTrade;
            if (entry) {
                tempTrade = getTrades().get(new OrderLink(tempinternalOrderIDEntry, orderid, c.getAccountName()));
            } else {
                tempTrade = getEntryTrade(c, tempinternalOrderIDEntry, parentid);
            }
            if (tempTrade != null) {
                if (entry) {
                    tempTrade.updateEntry(parentid, ob.getParentOrderSide(), avgFillPrice, filled, tempinternalOrderIDEntry, orderid, timeZone, c.getAccountName());
                    logger.log(Level.INFO, "311,TradeUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + tempTrade.getParentSymbol() + delimiter + tempTrade.getEntrySide() + delimiter + avgFillPrice + delimiter + filled + delimiter + tempTrade.getEntryID() + delimiter + tempTrade.getEntryOrderID()+delimiter+ob.getOrderID()+delimiter+ob.getInternalOrderID()+delimiter+ob.getOrderID()+delimiter+ob.getInternalOrderID() });

                } else {
                    int exitSize=tempTrade.getExitSize();
                    double exitPrice=tempTrade.getExitPrice();
                    int newexitSize=exitSize+filled;
                    double newexitPrice=(exitPrice*exitSize+filled*avgFillPrice)/(newexitSize);
                    tempTrade.updateExit(parentid, ob.getReason(), ob.getParentOrderSide(), newexitPrice, newexitSize, ob.getInternalOrderID(), orderid, timeZone, c.getAccountName());
                    logger.log(Level.INFO,"Debugging_ExecutionManager_TradeUpdate_exit,{0}",new Object[]{exitSize+delimiter+exitPrice+delimiter+filled+delimiter+avgFillPrice+delimiter+newexitSize+delimiter+newexitPrice});
                    logger.log(Level.INFO, "311,TradeUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + tempTrade.getParentSymbol() + delimiter + tempTrade.getEntrySide() + delimiter + avgFillPrice + delimiter + filled + delimiter + tempTrade.getEntryID() + delimiter + tempTrade.getEntryOrderID()+delimiter+ob.getOrderID()+delimiter+ob.getInternalOrderID() });
                    }
            } else {
                if (entry) {
                    tempTrade = new Trade(parentid, parentid, ob.getReason(), ob.getParentOrderSide(), avgFillPrice, filled, tempinternalOrderIDEntry, orderid, timeZone, c.getAccountName());
                    logger.log(Level.INFO, "311,TradeCreate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + tempTrade.getParentSymbol() + delimiter + tempTrade.getEntrySide() + delimiter + avgFillPrice + delimiter + filled + delimiter + tempTrade.getEntryID() + delimiter + tempTrade.getEntryOrderID()+delimiter+ob.getOrderID()+delimiter+ob.getInternalOrderID() });

                } else {
                    logger.log(Level.INFO, "103,ExitUpdateError,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "NullTradeObject" + delimiter + tempinternalOrderIDEntry + delimiter + orderid});
                }
            }
            if(entry){
            OrderLink tempOrderLink = new OrderLink(tempinternalOrderIDEntry, orderid, c.getAccountName());
            getTrades().put(tempOrderLink, tempTrade);
            logger.log(Level.FINE, "311,TradeInsert,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + tempTrade.getParentSymbol() + delimiter + tempTrade.getEntrySide() + delimiter + avgFillPrice + delimiter + filled + delimiter + tempTrade.getEntryID() + delimiter + tempTrade.getEntryOrderID()+delimiter+tempOrderLink.getAccountName()+delimiter+tempOrderLink.getExternalOrderID()+delimiter+tempOrderLink.getInternalOrderID()+delimiter+ob.getOrderID()+delimiter+ob.getInternalOrderID()});
            }
        } else {//combo order
            logger.log(Level.INFO,"Debugging_InComboOrder");
            ArrayList in = comboFillSize(c, ob.getInternalOrderID(), parentid);
            //logger.log(Level.FINE, "{0},{1},Execution Manager,Complete Fill Combo internal order id being updated,Internal Order ID : {2}", new Object[]{c.getAccountName(), orderReference, tempinternalOrderIDEntry});
            Trade tempTradeChild;
            if (entry) {
                tempTradeChild = getTrades().get(new OrderLink(tempinternalOrderIDEntry, orderid, c.getAccountName()));
            } else {
                tempTradeChild = getEntryTrade(c, tempinternalOrderIDEntry, childid);
            }
            Trade tempTradeParent = getTrades().get(new OrderLink(tempinternalOrderIDEntry, 0, c.getAccountName()));
            if (tempTradeParent != null) {
                //logger.log(Level.FINE, "Execution Manager,Complete Fill Combo Trade Object updated for entry. id:{0},orderSide:{1}, avgFillPrice:{2},filled:{3}, internalorderid:{4}, timezone:{5}, accountname:{6}", new Object[]{parentid, ob.getParentOrderSide(), avgFillPrice, filled, tempinternalOrderIDEntry, timeZone, c.getAccountName()});
                if (entry) {
                    tempTradeParent.updateEntry(parentid, ob.getParentOrderSide(), (double) in.get(1), Math.abs((int) in.get(0)), tempinternalOrderIDEntry, 0, timeZone, c.getAccountName());
                logger.log(Level.INFO, "311,TradeParentUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + tempTradeParent.getParentSymbol() + delimiter + tempTradeParent.getEntrySide() + delimiter + avgFillPrice + delimiter + filled + delimiter + tempTradeParent.getEntryID() + delimiter + tempTradeParent.getEntryOrderID()+delimiter+ob.getOrderID()+delimiter+ob.getInternalOrderID() });

                } else {
                    int exitSize=tempTradeParent.getExitSize();
                    double exitPrice=tempTradeParent.getExitPrice();
                    exitSize=exitSize+Math.abs((int) in.get(0));
                    exitPrice=(exitPrice*exitSize+Math.abs((int) in.get(0))*(double) in.get(1))/(exitSize);
                    tempTradeParent.updateExit(parentid, ob.getReason(), ob.getParentOrderSide(), exitPrice, exitSize, ob.getInternalOrderID(), 0, timeZone, c.getAccountName());
                     logger.log(Level.INFO, "311,TradeParentUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + tempTradeParent.getParentSymbol() + delimiter + tempTradeParent.getEntrySide() + delimiter + avgFillPrice + delimiter + filled + delimiter + tempTradeParent.getEntryID() + delimiter + tempTradeParent.getEntryOrderID()+delimiter+ob.getOrderID()+delimiter+ob.getInternalOrderID() });
                }
            } else {
                if (entry) {
                    tempTradeParent = new Trade(parentid, parentid, ob.getReason(), ob.getParentOrderSide(), (double) in.get(1), Math.abs((int) in.get(0)), tempinternalOrderIDEntry, 0, timeZone, c.getAccountName());
                 logger.log(Level.INFO, "311,TradeParentCreate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + tempTradeParent.getParentSymbol() + delimiter + tempTradeParent.getEntrySide() + delimiter + avgFillPrice + delimiter + filled + delimiter + tempTradeParent.getEntryID() + delimiter + tempTradeParent.getEntryOrderID()+delimiter+ob.getOrderID()+delimiter+ob.getInternalOrderID() });

                } else {
                    logger.log(Level.INFO, "103,ExitUpdateError,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "NullTradeObject" + delimiter + tempinternalOrderIDEntry + delimiter + orderid});
                }
            }
            if (tempTradeChild != null) {
                if (entry) {
                    tempTradeChild.updateEntry(childid, ob.getChildOrderSide(), avgFillPrice, filled, tempinternalOrderIDEntry, orderid, timeZone, c.getAccountName());
                    logger.log(Level.INFO,"311,ChildUpdated,{0}",new Object[]{c.getAccountName()+delimiter+orderReference+delimiter+orderid+delimiter+in.get(1)+delimiter+in.get(0)});                    
                } else {
                    tempTradeChild.updateExit(childid, ob.getReason(), ob.getChildOrderSide(), avgFillPrice, filled, ob.getInternalOrderID(), orderid, timeZone, c.getAccountName());
                    logger.log(Level.INFO,"311,ChildUpdated,{0}",new Object[]{c.getAccountName()+delimiter+orderReference+delimiter+orderid+delimiter+in.get(1)+delimiter+in.get(0)});                    
                }
            } else {
                if (entry) {
                    tempTradeChild = new Trade(childid, parentid, ob.getReason(), ob.getChildOrderSide(), avgFillPrice, filled, tempinternalOrderIDEntry, orderid, timeZone, c.getAccountName());
                    logger.log(Level.INFO,"311,ChildCreated,{0}",new Object[]{c.getAccountName()+delimiter+orderReference+delimiter+orderid+delimiter+in.get(1)+delimiter+in.get(0)});                    
                } else {
                    logger.log(Level.INFO, "103,ExitUpdateError,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "NullTradeObject" + delimiter + tempinternalOrderIDEntry + delimiter + orderid});
                }
            }
            getTrades().put(new OrderLink(tempinternalOrderIDEntry, 0, c.getAccountName()), tempTradeParent);
            if(entry){
                getTrades().put(new OrderLink(tempinternalOrderIDEntry, orderid, c.getAccountName()), tempTradeChild);
            }
            //logger.log(Level.INFO, "{0},{1},Execution Manager,Trade Updated for reporting,Parent Symbol: {2},Child Symbol: {3},Internal Order ID: {4},External OrderID: {5},Size: {6},FillPrice: {7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(parentid).getSymbol(), Parameters.symbol.get(childid).getSymbol(), tempinternalOrderIDEntry, orderid, filled, avgFillPrice});
        }
        }catch(Exception e){
            logger.log(Level.INFO,"101",e);
        }
    }

    private Trade getEntryTrade(BeanConnection c, int internalorderid, int childid) {
        String accountName = c.getAccountName();
        ArrayList<Trade> superset = new ArrayList<>();
        for (Trade tr : getTrades().values()) {
            if (tr.getEntryID() == internalorderid && tr.getEntrySymbolID() == childid && tr.getAccountName().equals(accountName)) {
                return tr;
            }
        }
        return null;
    }

    private ArrayList lowerBoundParentPosition(BeanPosition p) {
        ArrayList out = new ArrayList();
        int position = 0;
        double positionPrice = 0;
        ArrayList<BeanChildPosition> childPositions = p.getChildPosition();
        for (BeanChildPosition cp : childPositions) {
            cp.setParentPositionPotential(cp.getPosition() / cp.getBuildingblockSize());
        }
        int minPosition = Integer.MIN_VALUE;
        int maxPosition = Integer.MAX_VALUE;
        for (BeanChildPosition cp : childPositions) {
            minPosition = Math.max(minPosition, cp.getParentPositionPotential() < 0 ? cp.getParentPositionPotential() : 0);
            maxPosition = Math.min(maxPosition, cp.getParentPositionPotential() > 0 ? cp.getParentPositionPotential() : 0);
        }
        position = minPosition < 0 ? minPosition : maxPosition;
        out.add(position);
        //calculate position price

        for (BeanChildPosition cp : childPositions) {
            positionPrice = positionPrice + cp.getPrice() * cp.getBuildingblockSize();
        }
        out.add(positionPrice);
        return out;
    }

    private ArrayList upperBoundParentPosition(BeanPosition p) {
        ArrayList out = new ArrayList();
        int position = 0;
        double positionPrice = 0;
        ArrayList<BeanChildPosition> childPositions = p.getChildPosition();
        for (BeanChildPosition cp : childPositions) {
            cp.setParentPositionPotential(cp.getPosition() / cp.getBuildingblockSize());
        }
        int minPosition = Integer.MAX_VALUE;
        int maxPosition = Integer.MIN_VALUE;
        for (BeanChildPosition cp : childPositions) {
            minPosition = Math.min(minPosition, cp.getParentPositionPotential() < 0 ? cp.getParentPositionPotential() : 0);
            maxPosition = Math.max(maxPosition, cp.getParentPositionPotential() > 0 ? cp.getParentPositionPotential() : 0);
        }
        position = minPosition < 0 ? minPosition : maxPosition;
        out.add(position);
        //calculate position price
        double originalPositionPrice = 0;
        if (position == 0) {
            for (BeanChildPosition cp : childPositions) {
                positionPrice = positionPrice + cp.getProfit();

            }
        } else {
            for (BeanChildPosition cp : childPositions) {
                positionPrice = positionPrice + cp.getPrice() * cp.getBuildingblockSize();
            }
        }
        out.add(positionPrice);
        return out;
    }

    /**
     * Returns a two element list containing the fillsize of the combo and the
     * corresponding fill price.
     * <p>
     * The returned value is a signed number for the fillsize available at index
     * 0 and is also conservative. The actual fill could have stubs. To identify
     * stubs use function
     *
     * @param c - BeanConnection
     * @param internalorderid internal orderid generated by the strategy or the
     * framework
     * @param parentid id of the parent symbol
     * @return
     *
     */
    private ArrayList comboFillSize(BeanConnection c, int internalorderid, int parentid) {
        //logger.log(Level.FINE, "ComboFillSize, Parameters in,BeanConnection:{0},internalorderid:{1},parentid:{2}", new Object[]{c.getAccountName(), internalorderid, parentid});
        ArrayList out = new ArrayList();
        HashMap<Integer, Integer> fill = new HashMap<>();//<symbolid,fillsize>
        HashMap<Integer, Double> fillprice = new HashMap<>();
        int orderSize = 0;
        for (int orderid : c.getOrderMapping().get(new Index(orderReference, internalorderid))) {
            OrderBean ob = c.getOrders().get(orderid);
            if(ob==null){//all combo order have not been placed as yet. return 0
                out.add(0);
                out.add(0D);
                return out;
            }
        }
        //logger.log(Level.FINE, "{0},{1},Execution Manager, Calculating fill size for combo,InternalOrderID: {2}, parentid: {3},  OrderMapping:{4}", new Object[]{c.getAccountName(), orderReference, internalorderid, parentid, c.getOrderMapping()});
        for (int orderid : c.getOrderMapping().get(new Index(orderReference, internalorderid))) {
            OrderBean ob = c.getOrders().get(orderid);
            if (orderSize == 0) { //run this condition just once
                orderSize = ob.getParentOrderSide().equals(EnumOrderSide.BUY) || ob.getParentOrderSide().equals(EnumOrderSide.COVER) ? ob.getParentOrderSize() : -ob.getParentOrderSize();
            }
            int id = ob.getChildSymbolID() - 1;
            int last = 0;
            int current = 0;
            double lastfillprice = 0D;
            double currentfillprice = 0D;
            last = fill.get(id) == null ? 0 : fill.get(id);
            current = (ob.getChildOrderSide().equals(EnumOrderSide.BUY) || ob.getChildOrderSide().equals(EnumOrderSide.COVER)) ? ob.getChildFillSize() : -ob.getChildFillSize();
            fill.put(id, last + current);
            lastfillprice = fillprice.get(id) == null ? 0D : fillprice.get(id);
            currentfillprice = fill.get(id) != 0 ? (ob.getFillPrice() * current + lastfillprice * last) / fill.get(id) : 0D;
            fillprice.put(id, currentfillprice);
        }
        //calculate fill size
        int combosize = 0;
        int maxcombosize = Integer.MAX_VALUE;
        int mincombosize = Integer.MIN_VALUE;
        for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
            int childid = entry.getKey().getSerialno() - 1;
            if (orderSize > 0) {
                maxcombosize = Math.min(maxcombosize, fill.get(childid) / entry.getValue());
            } else if (orderSize < 0) {
                mincombosize = Math.max(mincombosize, fill.get(childid) / entry.getValue());
            }            
        }
        combosize = orderSize > 0 ? maxcombosize : orderSize < 0 ? mincombosize : 0;


        //calculate fill price
        double comboprice = 0;
        if (combosize != 0) {
            for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                int childid = entry.getKey().getSerialno() - 1;
                comboprice = comboprice + entry.getValue() * fillprice.get(childid);
            }
            //comboprice = comboprice / Math.abs(combosize);
            comboprice = comboprice / 1;
        }
        out.add(combosize);
        out.add(comboprice);
        return out;

    }

    /**
     * Returns a HashMap <K,V> containing <Symbol id, stub size> for a given
     * internal order id. The value specifies the excess fill. So a positive
     * value means that the symbol has an excess stub. Similiary a negative
     * value means that the symbol has a deficient stub.
     *
     * @param c
     * @param internalorderid
     * @param parentid
     * @return
     */
    private HashMap<Integer, Integer> comboStubSize(BeanConnection c, int internalorderid, int parentid, int baselineSize) {
        HashMap<Integer, Integer> out = new HashMap();
        HashMap<Integer, Integer> comboSpecs = new HashMap();
        baselineSize = baselineSize == 0 ? (int) comboFillSize(c, internalorderid, parentid).get(0) : baselineSize;
        HashMap<Integer, Integer> comboChildFillSize = new HashMap();
        for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
            comboSpecs.put(entry.getKey().getSerialno() - 1, entry.getValue());
            comboChildFillSize.put(entry.getKey().getSerialno() - 1, entry.getValue() * baselineSize);
        }
        synchronized (c.lockOrderMapping) {
            for (int orderid : c.getOrderMapping().get(new Index(orderReference, internalorderid))) {
                OrderBean ob = c.getOrders().get(orderid);
                int childid = ob.getChildSymbolID() - 1;
                int childFillSize = (ob.getChildOrderSide().equals(EnumOrderSide.BUY) || ob.getChildOrderSide().equals(EnumOrderSide.COVER)) ? ob.getChildFillSize() : -ob.getChildFillSize();
                int stub = childFillSize - comboChildFillSize.get(childid);
                int earlierValue = out.get(childid) == null ? 0 : out.get(childid);
                out.put(childid, earlierValue + stub);
            }
            return out;
        }
    }

    private int getFirstInternalOpenOrder(int id, EnumOrderSide side, String accountName) {
        ArrayList<Trade> allTrades = new <Trade> ArrayList();
        Iterator tradeitr = getTrades().entrySet().iterator();
        for (Map.Entry<OrderLink, Trade> entry : getTrades().entrySet()) {
            if (entry.getKey().getAccountName().equals(accountName)) {
                allTrades.add(entry.getValue());
            }
        }
        Collections.sort(allTrades, new TradesCompare());
        String symbol = Parameters.symbol.get(id).getDisplayname();
        String type = Parameters.symbol.get(id).getType();
        String expiry = Parameters.symbol.get(id).getExpiry() == null ? "" : Parameters.symbol.get(id).getExpiry();
        String right = Parameters.symbol.get(id).getRight() == null ? "" : Parameters.symbol.get(id).getRight();
        String option = Parameters.symbol.get(id).getOption() == null ? "" : Parameters.symbol.get(id).getOption();
        EnumOrderSide entrySide = side == EnumOrderSide.SELL ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
        for (Trade tr : allTrades) {
            if (tr.getEntrySymbol().compareTo(symbol) == 0 && tr.getEntrySide() == entrySide) {
                if (tr.getEntrySize() > tr.getExitSize()) {
                    //logger.log(Level.FINE, "{0},{1},Execution Manager,Internal order id being used, Internal Order ID:{2}", new Object[]{accountName, orderReference, tr.getEntryID()});
                    return tr.getEntryID();
                }
            }
        }
        return 0;
    }

    private void updateAcknowledgement(BeanConnection c, int parentid, int orderID) {
        OrderBean ob = c.getOrders().get(orderID);
        Index ind = new Index(orderReference, parentid);
        ob.setChildStatus(EnumOrderStatus.ACKNOWLEDGED);
        ArrayList<Integer> linkedOrderIds = TradingUtil.getLinkedOrderIds(orderID, c);
        boolean parentStatus = true;
        for (int ordi : linkedOrderIds) {
            OrderBean obi = c.getOrders().get(ordi);
            if (obi != null) {
                if (obi.getChildStatus() == EnumOrderStatus.ACKNOWLEDGED || obi.getChildStatus() == EnumOrderStatus.COMPLETEFILLED || obi.getChildStatus() == EnumOrderStatus.PARTIALFILLED) {
                    parentStatus = parentStatus && true;
                } else {
                    parentStatus = parentStatus && false;
                }
            }
        }
        if (parentStatus) {
            for (int ordi : linkedOrderIds) {
                OrderBean obi = c.getOrders().get(ordi);
                if (obi != null) {
                    obi.setParentStatus(EnumOrderStatus.ACKNOWLEDGED);
                }
            }
        }
        //logger.log(Level.INFO, "{0},{1},Execution Manager,Order State, OrderID: {2}, Parent Order Status:{3}, Child Order Status:{4}", new Object[]{c.getAccountName(), orderReference, orderID, ob.getParentStatus(), ob.getChildStatus()});
    }

    //<editor-fold defaultstate="collapsed" desc="getter-setters">
    /**
     * @return the trades
     */
    public HashMap<OrderLink, Trade> getTrades() {
        return trades;
    }

    /**
     * @param trades the trades to set
     */
    public void setTrades(HashMap<OrderLink, Trade> trades) {
        this.trades = trades;
    }

    /**
     * @return the aggr
     */
    public Boolean getAggression() {
        return aggression;
    }

    /**
     * @param aggr the aggr to set
     */
    public void setAggression(Boolean aggression) {
        this.aggression = aggression;
    }
    //</editor-fold>

    public synchronized ArrayList<Integer> getOpenPositionCount() {
        return openPositionCount;
    }

    /**
     * @param openPositionCount the openPositionCount to set
     */
    public synchronized void setOpenPositionCount(ArrayList<Integer> openPositionCount) {
        this.openPositionCount = openPositionCount;
    }

    /**
     * @return the cancellationRequestsForTracking
     */
    public synchronized ArrayList<ArrayList<LinkedAction>> getCancellationRequestsForTracking() {
        return cancellationRequestsForTracking;
    }

    /**
     * @param cancellationRequestsForTracking the
     * cancellationRequestsForTracking to set
     */
    public synchronized void setCancellationRequestsForTracking(ArrayList<ArrayList<LinkedAction>> cancellationRequestsForTracking) {
        this.cancellationRequestsForTracking = cancellationRequestsForTracking;
    }

    /**
     * @return the fillRequestsForTracking
     */
    public ArrayList<ArrayList<LinkedAction>> getFillRequestsForTracking() {
        return fillRequestsForTracking;
    }

    /**
     * @param fillRequestsForTracking the fillRequestsForTracking to set
     */
    public void setFillRequestsForTracking(ArrayList<ArrayList<LinkedAction>> fillRequestsForTracking) {
        this.fillRequestsForTracking = fillRequestsForTracking;
    }

    public void addNotificationListeners(NotificationListener l) {
        notificationListeners.add(l);
    }

    public void removeNotificationListeners(NotificationListener l) {
        notificationListeners.remove(l);
    }

    @Override
    public synchronized void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                wait();
            } catch (InterruptedException ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }
    }

    /**
     * @return the estimatedBrokerage
     */
    public double getEstimatedBrokerage() {
        return estimatedBrokerage;
    }

    /**
     * @param estimatedBrokerage the estimatedBrokerage to set
     */
    public void setEstimatedBrokerage(double estimatedBrokerage) {
        this.estimatedBrokerage = estimatedBrokerage;
    }
}