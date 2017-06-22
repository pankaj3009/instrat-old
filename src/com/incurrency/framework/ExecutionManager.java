/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.ib.client.Order;
import static com.incurrency.framework.Algorithm.*;
import static com.incurrency.framework.EnumPrimaryApplication.DISTRIBUTE;
import static com.incurrency.framework.EnumPrimaryApplication.FLAT;
import static com.incurrency.framework.EnumPrimaryApplication.SIZE;
import static com.incurrency.framework.EnumPrimaryApplication.VALUE;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import org.jquantlib.time.BusinessDayConvention;
import org.jquantlib.time.JDate;

/**
 *
 * @author admin
 */
public class ExecutionManager implements Runnable, OrderListener, OrderStatusListener, TWSErrorListener {

    private final static Logger logger = Logger.getLogger(DataBars.class.getName());
    final ExecutionManager parentorder = this;
    static boolean logHeaderWritten = false;
    double tickSize;
    private Boolean aggression;
    String orderReference;
    Date endDate;
    public TradingEventSupport tes = new TradingEventSupport();
    private Database<String, String> db; //trades holds internal order id <init>, Trade object
    private double pointValue;
    private ArrayList<Integer> openPositionCount = new ArrayList<>();
    private ArrayList<Integer> maxOpenPositions = new ArrayList<>();
    private String timeZone = "";
    private String lastExecutionRequestTime = "";
    private ArrayList<String> accounts = new ArrayList<>();
    private ArrayList<ArrayList<Integer>> cancelledOrdersAcknowledgedByIB = new ArrayList<>();
    private ArrayList<ArrayList<LinkedAction>> cancellationRequestsForTracking = new ArrayList<>();
    private List<ArrayList<LinkedAction>> fillRequestsForTracking = Collections.synchronizedList(new ArrayList<ArrayList<LinkedAction>>());
    private Strategy s;
    private ArrayList notificationListeners = new ArrayList();
    //copies of global variables
    private ArrayList<Integer> deemedCancellation = new ArrayList<>();
    private final Object lockLinkedAction = new Object();
    private final String delimiter = "_";
    private double estimatedBrokerage = 0;
    private ConcurrentHashMap<Integer, EnumOrderStatus> orderStatus = new ConcurrentHashMap<>();

    public ExecutionManager(Strategy s, Boolean aggression, double tickSize, Date endDate, String orderReference, double pointValue, Integer maxOpenPositions, String timeZone, ArrayList<String> accounts, String executionFile) {
        this.s = s;
        this.tickSize = tickSize;
        this.aggression = aggression;
        this.orderReference = orderReference;
        this.endDate = endDate;
        this.pointValue = pointValue;
        this.timeZone = timeZone;
        this.accounts = accounts;
        //load db
        if (!Algorithm.useRedis) {
            logger.log(Level.SEVERE, "Redis needs to be set as the store for trade records");
        } else {
            db = Algorithm.db;
        }
        tes.addOrderListener(this); //subscribe to events published by tes owned by the strategy oms

        for (BeanConnection c : Parameters.connection) {
            c.getWrapper().addOrderStatusListener(this);
            c.getWrapper().addTWSErrorListener(this);
            this.maxOpenPositions.add(maxOpenPositions);
            this.openPositionCount.add(0);
            cancelledOrdersAcknowledgedByIB.add(new ArrayList<Integer>());
            cancellationRequestsForTracking.add(new ArrayList<LinkedAction>());//cancelled orders represent orders that do not need to be executed anymore
            fillRequestsForTracking.add(new ArrayList<LinkedAction>());
        }
        if (globalProperties.getProperty("deemedcancellation") != null) {
            String init = globalProperties.getProperty("deemedcancellation").toString().trim();
            String values[] = init.split(",");
            for (String i : values) {
                deemedCancellation.add(Integer.valueOf(i));
            }
        }
        //first update combo positions
        ArrayList<Integer> comboOrderids = new ArrayList<>();
        for (String key : db.getKeys("opentrades_" + this.orderReference)) {
            if (key.contains("_" + s.getStrategy())) {
                String parentdisplayname = Trade.getParentSymbol(db, key);
                int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
                if (parentid >= 0 && Parameters.symbol.get(parentid).getType().equals("COMBO")) {
                    comboOrderids.add(Trade.getEntryOrderIDExternal(db, key));
                    updateInitPositions(key, comboOrderids);
                }
            }
        }
        //then update single legs
        for (String key : db.getKeys("opentrades_" + this.orderReference)) {
            if (key.contains("_" + s.getStrategy())) {
                String parentdisplayname = Trade.getParentSymbol(db, key);
                int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
                if (parentid >= 0 && !Parameters.symbol.get(parentid).getType().equals("COMBO")) {
                    updateInitPositions(key, comboOrderids);
                }
            }
        }
        //print positions on initialization
        int i = -1;
        for (BeanConnection c : Parameters.connection) {
            i = i + 1;
            Iterator iter = c.getPositions().entrySet().iterator();
            while (iter.hasNext()) {
                Map.Entry<Index, BeanPosition> p = (Map.Entry) iter.next();
                if (p.getKey().getStrategy().equals(orderReference)) {
                    if (p.getValue().getPosition() == 0) {
                        iter.remove();
                    } else {
                        logger.log(Level.INFO, "500, InitialTradePosition,{0}:{1}:{2}:{3}:{4},Position={5}:PositionPrice:{6}",
                                new Object[]{orderReference, c.getAccountName(), Parameters.symbol.get(p.getKey().getSymbolID()).getDisplayname(), -1, -1, String.valueOf(p.getValue().getPosition()), String.valueOf(p.getValue().getPrice())});
                    }
                }
            }
        }

        //update connection with mtm data
        for (BeanConnection c : Parameters.connection) {
            HashMap<Index, BeanPosition> positions = c.getPositions();
            Iterator it = positions.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Index, BeanPosition> position = (Map.Entry) it.next();
                if (position.getKey().getStrategy().equals(orderReference)) {
                    Object object = c.getMtmByStrategy().get(orderReference);
                    double mtmStrategy = Utilities.getDouble(object, 0);
                    mtmStrategy = mtmStrategy + position.getValue().getUnrealizedPNLPriorDay();
                    c.getMtmByStrategy().put(orderReference, mtmStrategy);
                    c.getMtmBySymbol().put(position.getKey(), position.getValue().getUnrealizedPNLPriorDay());
                }
            }
        }

        //Initialize timers
        new Timer(10000, cancelExpiredOrders).start();
        //java.util.InitializeTimer  brokerage = new java.util.Timer("Brokerage Calculator");
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
            for (String key : getDb().getKeys("closedtrades")) {
                String parentdisplayname = Trade.getParentSymbol(db, key);
                int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
                double entryPrice = Trade.getEntryPrice(db, key);
                double exitPrice = Trade.getExitPrice(db, key);
                int entrySize = Trade.getEntrySize(db, key);
                int exitSize = Trade.getExitSize(db, key);
                String exitTime = Trade.getExitTime(db, key);
                String entryTime = Trade.getEntryTime(db, key);
                EnumOrderSide entrySide = Trade.getEntrySide(db, key);
                EnumOrderSide exitSide = Trade.getExitSide(db, key);

                if (!Parameters.symbol.get(parentid).getType().equals("COMBO")) {
                    for (BrokerageRate b : getS().getBrokerageRate()) {
                        switch (b.primaryRule) {
                            case VALUE:
                                if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER)))) {
                                    entryCost = entryCost + (entryPrice * entrySize * b.primaryRate / 100) + (entryPrice * entrySize * b.primaryRate * b.secondaryRate / 10000);
                                }
                                break;
                            case SIZE:
                                if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
                                    entryCost = entryCost + entrySize * b.primaryRate + entrySize * b.primaryRate * b.secondaryRate;
                                }
                                break;
                            case FLAT:
                                if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
                                    entryCost = entryCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                                }
                                break;
                            case DISTRIBUTE:
                                if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
                                    int tradesToday = getDb().getKeys("opentrades").size() * 2;
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
                    for (BrokerageRate b : getS().getBrokerageRate()) {
                        switch (b.primaryRule) {
                            case VALUE:
                                if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                                    exitCost = exitCost + (exitPrice * exitSize * b.primaryRate / 100) + (exitPrice * exitSize * b.primaryRate * b.secondaryRate / 10000);
                                }
                                break;
                            case SIZE:
                                if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                                    exitCost = exitCost + exitSize * b.primaryRate + exitSize * b.primaryRate * b.secondaryRate;
                                }
                                break;
                            case FLAT:
                                if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                                    exitCost = exitCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                                }
                                break;
                            case DISTRIBUTE:
                                int tradesToday = getDb().getKeys("opentrades").size() * 2;
                                if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                                    exitCost = exitCost + b.primaryRate / tradesToday + (b.primaryRate / tradesToday) * b.secondaryRate;
                                }
                                break;
                            default:
                                break;
                        }
                    }
                }
            }
            ExecutionManager.this.setEstimatedBrokerage(entryCost + exitCost);
        }
    };

    private void updateInitPositions(String key, ArrayList<Integer> combos) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        int tempPosition;
        double tempPositionPrice;
        JDate today = new JDate(TradingUtil.getAlgoDate());
        String todayString = sdf.format(today.isoDate());
        JDate yesterday = today.sub(1);
        yesterday = Algorithm.ind.adjust(yesterday, BusinessDayConvention.Preceding);
        String yesterdayString = sdf.format(yesterday.isoDate());
        String childdisplayName = Trade.getEntrySymbol(db, key);
        String parentdisplayName = Trade.getParentSymbol(db, key);
        int entryorderidint = Trade.getEntryOrderIDInternal(db, key);
        int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayName);
        int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayName);
        double entryPrice = Trade.getEntryPrice(db, key);;
        int entrySize = Trade.getEntrySize(db, key);
        double exitPrice = Trade.getExitPrice(db, key);;
        int exitSize = Trade.getExitSize(db, key);
        double mtmPrice;
        // double mtmPrice = Trade.getMtm(db, parentdisplayName,todayString);
        // if(mtmPrice==0){
        mtmPrice = Trade.getMtm(db, parentdisplayName, yesterdayString);
        // }
        if (childid >= 0 && parentid >= 0 && (!combos.contains(entryorderidint) || Parameters.symbol.get(parentid).getType().equals("COMBO"))) {//single leg trades or combo trade
            Index ind = new Index(orderReference, parentid);
            int i = -1;
            for (BeanConnection c : Parameters.connection) {
                i = i + 1;
                int tempOpenPosition = 0;
                if (c.getAccountName().equals(Trade.getAccountName(db, key))) {
                    BeanPosition p = c.getPositions().get(ind) == null ? new BeanPosition() : c.getPositions().get(ind);
                    tempPosition = p.getPosition();
                    tempPositionPrice = p.getPrice();
                    switch (Trade.getEntrySide(db, key)) {
                        case BUY:
                            tempPositionPrice = tempPosition + entrySize != 0 ? (tempPosition * tempPositionPrice + entrySize * entryPrice) / (entrySize + tempPosition) : 0D;
                            tempPosition = tempPosition + entrySize;
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(this.pointValue);
                            p.setStrategy(this.orderReference);
                            c.getPositions().put(ind, p);
                            if (Trade.getEntryTime(db, key).substring(0, 10).compareTo(todayString) < 0) {
                                double priorUnrealizedPNLPriorDay = p.getUnrealizedPNLPriorDay();
                                p.setUnrealizedPNLPriorDay(priorUnrealizedPNLPriorDay + entrySize * (mtmPrice - entryPrice));
                            }
                            if (entrySize > 0) {
                                tempOpenPosition = this.openPositionCount.get(i);
                                this.openPositionCount.add(i, tempOpenPosition + 1);
                                logger.log(Level.INFO, "500, InitialOpenPositionCount,{0}:{1}:{2}:{3}:{4},OpenPositionCount={5}",
                                        new Object[]{orderReference, c.getAccountName(), Trade.getEntrySymbol(db, key), -1, -1, String.valueOf(openPositionCount.get(i))});
                            }
                            break;
                        case SHORT:
                            tempPositionPrice = tempPosition - entrySize != 0 ? (tempPosition * tempPositionPrice - entrySize * entryPrice) / (-entrySize + tempPosition) : 0D;
                            tempPosition = tempPosition - entrySize;
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(this.pointValue);
                            p.setStrategy(this.orderReference);
                            c.getPositions().put(ind, p);
                            if (Trade.getEntryTime(db, key).substring(0, 10).compareTo(todayString) < 0) {
                                double priorUnrealizedPNLPriorDay = p.getUnrealizedPNLPriorDay();
                                p.setUnrealizedPNLPriorDay(priorUnrealizedPNLPriorDay + entrySize * (entryPrice - mtmPrice));
                            }
                            if (entrySize > 0) {
                                tempOpenPosition = this.openPositionCount.get(i);
                                this.openPositionCount.add(i, tempOpenPosition + 1);
                                logger.log(Level.INFO, "500, InitialOpenPositionCount,{0}:{1}:{2}:{3}:{4},OpenPositionCount={5}",
                                        new Object[]{orderReference, c.getAccountName(), Trade.getEntrySymbol(db, key), -1, -1, String.valueOf(openPositionCount.get(i))});
                            }
                            break;
                        default:
                            break;
                    }
                    switch (Trade.getExitSide(db, key)) {
                        case COVER:
                            tempPositionPrice = tempPosition + exitSize != 0 ? (tempPosition * tempPositionPrice + exitSize * exitPrice) / (exitSize + tempPosition) : 0D;
                            tempPosition = tempPosition + exitSize;
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(this.pointValue);
                            p.setStrategy(this.orderReference);
                            c.getPositions().put(ind, p);
                            if (Trade.getExitTime(db, key).substring(0, 10).compareTo(todayString) < 0) {
                                double priorUnrealizedPNLPriorDay = p.getUnrealizedPNLPriorDay();
                                p.setUnrealizedPNLPriorDay(priorUnrealizedPNLPriorDay - exitSize * (entryPrice - exitPrice));
                            }
                            break;
                        case SELL:
                            tempPositionPrice = tempPosition - exitSize != 0 ? (tempPosition * tempPositionPrice - exitSize * exitPrice) / (-exitSize + tempPosition) : 0D;
                            tempPosition = tempPosition - exitSize;
                            p.setPosition(tempPosition);
                            p.setPrice(tempPositionPrice);
                            p.setPointValue(this.pointValue);
                            p.setStrategy(this.orderReference);
                            c.getPositions().put(ind, p);
                            if (Trade.getExitTime(db, key).substring(0, 10).compareTo(todayString) < 0) {
                                double priorUnrealizedPNLPriorDay = p.getUnrealizedPNLPriorDay();
                                p.setUnrealizedPNLPriorDay(priorUnrealizedPNLPriorDay - exitSize * (exitPrice - entryPrice));
                            }
                            break;
                        default:
                            break;
                    }
                    if (p.getPosition() == 0) {
                        p.setUnrealizedPNLPriorDay(0);
                    }
//                    p.setUnrealizedPNLPriorDay(p.getPosition() * (mtmPrice - p.getPrice()));
                    //add childPositions and set it to null
                    for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                        p.getChildPosition().add(new BeanChildPosition(entry.getKey().getSerialno() - 1, entry.getValue()));
                    }
                }
            }
        } else if (childid >= 0 && parentid >= 0 && combos.contains(entryorderidint)) { //trade linked to combo. Update child positions
            //add child ids to combo position
            for (BeanConnection c : Parameters.connection) {
                if (c.getAccountName().equals(Trade.getAccountName(db, key))) {
                    Index ind = new Index(orderReference, parentid);
                    BeanPosition p = c.getPositions().get(ind) == null ? new BeanPosition() : c.getPositions().get(ind);
                    for (BeanChildPosition cp : p.getChildPosition()) {
                        if (cp.getSymbolid() == childid) {
                            tempPosition = cp.getPosition();
                            tempPositionPrice = cp.getPrice();
                            switch (Trade.getEntrySide(db, key)) {
                                case BUY:
                                    tempPositionPrice = tempPosition + entrySize != 0 ? (tempPosition * tempPositionPrice + entrySize * entryPrice) / (entrySize + tempPosition) : 0D;
                                    tempPosition = tempPosition + entrySize;
                                    cp.setPosition(tempPosition);
                                    cp.setPrice(tempPositionPrice);
                                    cp.setPointValue(this.pointValue);
                                    p.setStrategy(this.orderReference);
                                    c.getPositions().put(ind, p);
                                    break;
                                case SHORT:
                                    tempPositionPrice = tempPosition - entrySize != 0 ? (tempPosition * tempPositionPrice - entrySize * entryPrice) / (-entrySize + tempPosition) : 0D;
                                    tempPosition = tempPosition - entrySize;
                                    cp.setPosition(tempPosition);
                                    cp.setPrice(tempPositionPrice);
                                    cp.setPointValue(this.pointValue);
                                    p.setStrategy(this.orderReference);
                                    c.getPositions().put(ind, p);
                                    break;
                                default:
                                    break;
                            }
                            switch (Trade.getExitSide(db, key)) {
                                case COVER:
                                    tempPositionPrice = tempPosition + entrySize != 0 ? (tempPosition * tempPositionPrice + entrySize * entryPrice) / (entrySize + tempPosition) : 0D;
                                    tempPosition = tempPosition + entrySize;
                                    cp.setPosition(tempPosition);
                                    cp.setPrice(tempPositionPrice);
                                    cp.setPointValue(this.pointValue);
                                    p.setStrategy(this.orderReference);
                                    c.getPositions().put(ind, p);
                                    break;
                                case SELL:
                                    tempPositionPrice = tempPosition - entrySize != 0 ? (tempPosition * tempPositionPrice - entrySize * entryPrice) / (-entrySize + tempPosition) : 0D;
                                    tempPosition = tempPosition - entrySize;
                                    cp.setPosition(tempPosition);
                                    cp.setPrice(tempPositionPrice);
                                    cp.setPointValue(this.pointValue);
                                    p.setStrategy(this.orderReference);
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
    public void orderReceived(OrderBean event) {
        //for each connection eligible for trading
        // System.out.println(Thread.currentThread().getName());
        try {
            if (event.get("OrderReference").compareToIgnoreCase(orderReference) == 0) {
                //we first handle initial orders given by EnumOrderStage=INIT
                if (EnumOrderStage.valueOf(event.get("OrderStage")) == EnumOrderStage.INIT) {
                    int id = event.getParentSymbolID();
                    for (BeanConnection c : Parameters.connection) {
                        boolean specificAccountSpecified = event.getSpecifiedBrokerAccount() != null && !event.getSpecifiedBrokerAccount().isEmpty();
                        if ("Trading".equals(c.getPurpose()) && (!specificAccountSpecified && accounts.contains(c.getAccountName()) || (specificAccountSpecified && event.getSpecifiedBrokerAccount().equals(c.getAccountName())))) {
                            //check if system is square
                            //logger.log(Level.INFO, "303, OrderDetails,{0}", new Object[]{c.getAccountName()+delimiter+ orderReference+delimiter+ event.getInternalorder()+delimiter+event.getInternalorderentry()+delimiter+event.get Parameters.symbol.get(id).getDisplayname()+delimiter+event.getSide()+delimiter+event.getOrderSize()+delimiter+event.getLimitPrice()+delimiter+event.getTriggerPrice()});
                            Index ind = new Index(event.get("OrderReference"), id);
                            Integer position = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                            position = position != 0 ? 1 : 0;
                            int signedPositions = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
                            Integer openorders = zilchOpenOrders(c, id, event.get("OrderReference")) == Boolean.TRUE ? 0 : 1;
                            Integer entry = (event.getOrderSide() == EnumOrderSide.BUY || event.getOrderSide() == EnumOrderSide.SHORT) ? 1 : 0;
                            String rule = event.getStubs() != null ? "STUB" : Integer.toBinaryString(position) + Integer.toBinaryString(openorders) + Integer.toBinaryString(entry);
                            //POI (Position, Open Order, Initiation)
                            ArrayList<OrderBean> openBuy = getOpenOrdersForSide(c, id, EnumOrderSide.BUY);
                            ArrayList<OrderBean> openSell = getOpenOrdersForSide(c, id, EnumOrderSide.SELL);
                            ArrayList<OrderBean> openShort = getOpenOrdersForSide(c, id, EnumOrderSide.SHORT);
                            ArrayList<OrderBean> openCover = getOpenOrdersForSide(c, id, EnumOrderSide.COVER);
                            logger.log(Level.INFO, "301,OrderReceived.ExecutionFlow,{0}:{1}:{2}:{3}:{4},Case={5}:OpenBuy={6}:OpenSell={7}:OpenShort={8}:OpenCover={9},:OrderSize={10}",
                                    new Object[]{orderReference, c.getAccountName(), event.get("ParentDisplayName"), event.get("ParentInternalOrderID"), -1, rule, openBuy.size(), openSell.size(), openShort.size(), openCover.size(), Integer.toString(event.getCurrentOrderSize())});
                            switch (rule) {
                                case "STUB":
                                    //processStubOrder(id, c, event);
                                    break;
                                case "000"://position=0, no openorder=0, exit order as entry=0
                                    break;
                                case "001": //position=0, no openorder=0, entry order as entry=1
                                    if (signedPositions == 0 && (event.getOrderSide() == EnumOrderSide.BUY || event.getOrderSide() == EnumOrderSide.SHORT)) {
                                        processEntryOrder(id, c, event);
                                    } else {
                                        logger.log(Level.SEVERE, "301,ExecutionFlow,{0}", new Object[]{"UnexpectedCase_001"});
                                    }
                                    break;
                                case "100": //position=1, no open order=0, exit order 
                                    if ((signedPositions > 0 && event.getOrderSide() == EnumOrderSide.SELL) || (signedPositions < 0 && event.getOrderSide() == EnumOrderSide.COVER)) {
                                        processExitOrder(id, c, event);
                                    } else {
                                        //Logically the else condition should never be hit as it requires an entry order. Case 100 is only for exit orders
                                        logger.log(Level.INFO, "201,ExecutionFlow,{0}", new Object[]{"UnexpectedCase_100"});
                                        this.cancelOpenOrders(c, id, event.getOrderReference());
                                        this.squareAllPositions(c, id, event.getOrderReference());
                                    }
                                    break;
                                case "010": //position=0, open order, exit order
                                    if (event.isScale()) {
                                        cleanScaleTrueOrders(c, id, event);
                                    } else {
                                       cleanScaleFalseOrders(c, id, event);
                                    }                    
                                        processExitOrder(id, c, event);
                                    
                                    break;
                                case "011": //no position, open order exists, entry order
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
                                    if (event.isScale()) {
//                                        logger.log(Level.INFO, "{0},{1},Execution Manager,Case:101. Scale In Allowed, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                        processEntryOrder(id, c, event);
                                    } else {
                                        //system is broken for the symbol
                                        logger.log(Level.INFO, "201,ExecutionFlow,{0}", new Object[]{"UnexpectedCase_303"});
//                                        logger.log(Level.INFO, "{0},{1},Execution Manager,Case: 101. Error:Entry order received while positions exist, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                    }
                                    break;

                                case "111"://position, open order, entry order received.
                                    boolean combo = TradingUtil.isSyntheticSymbol(id);
                                    if (event.isScale()) { //scale in order
                                        if (!combo && (openCover.size() > 0 && event.getOrderSide() == EnumOrderSide.SHORT) || (openSell.size() > 0 && event.getOrderSide() == EnumOrderSide.BUY)) {
                                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Reinstate Scale-in, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                            //If an entry order is received, but prior exit for same entry side not completed, cancel prior exit order
                                            ArrayList<Integer> orderids = event.getOrderSide() == EnumOrderSide.SHORT ? getExternalOpenOrdersForSide(c, id, EnumOrderSide.COVER) : getExternalOpenOrdersForSide(c, id, EnumOrderSide.SELL);
                                            this.cancelOpenOrders(c, id, event.getOrderReference());
                                            //there is a problem if the order could not be cancelled as it was already filled. We then need to bring back this event again.
                                            int connectionid = Parameters.connection.indexOf(c);
                                            synchronized (lockLinkedAction) {
                                                for (int orderid : orderids) {
                                                    ArrayList<OrderEvent> e = new ArrayList<>();
                                                    //ArrayList<LinkedAction> fillRequests = getFillRequestsForTracking().get(connectionid);
                                                    //fillRequests.add(new LinkedAction(c, orderid, event, EnumLinkedAction.REVERSEFILL));
                                                    event.createLinkedAction(event.getParentSymbolID(), "REVERSEFILL", "CANCELLEDPARTIALFILL_CANCELLEDNOFILL_COMPLETEFILLED", "0");
//                                                    getFillRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderid, event, EnumLinkedAction.REVERSEFILL,0));
                                                }
                                                lockLinkedAction.notifyAll();
                                            }

                                        } else if ((c.getPositions().get(ind).getPosition() > 0 && openBuy.size() > 0 && openShort.size() == 0 && openSell.size() == 0 && openCover.size() == 0)
                                                || (c.getPositions().get(ind).getPosition() < 0 && openBuy.size() == 0 && openShort.size() > 0 && openSell.size() == 0 && openCover.size() == 0)) {
                                            logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Scale-In allowed, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getDisplayname(), event.getOriginalOrderSize(), event.getOrderSide(), event.getLimitPrice(), event.getTriggerPrice(),event.getEffectiveTill()});
                                            //else process scale-in order
                                            processEntryOrder(id, c, event);
                                        } else {
                                            logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Cleanse Scale-in orders, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getDisplayname(), event.getOriginalOrderSize(), event.getOrderSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getEffectiveTill()});
                                            cleanScaleTrueOrders(c, id, event);
                                        }
                                    } else if (!event.isScale()) {
                                        if (!combo && (openCover.size() > 0 && event.getOrderSide() == EnumOrderSide.SHORT) || (openSell.size() > 0 && event.getOrderSide() == EnumOrderSide.BUY)) {
                                            logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Scale-in not allowed. Reinstate , Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getDisplayname(), event.getOriginalOrderSize(), event.getOrderSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getEffectiveTill()});
                                            ArrayList<Integer> orderids = event.getOrderSide() == EnumOrderSide.SHORT ? getExternalOpenOrdersForSide(c, id, EnumOrderSide.COVER) : getExternalOpenOrdersForSide(c, id, EnumOrderSide.SELL);
                                            this.cancelOpenOrders(c, id, event.getOrderReference());
                                            //there is a problem if the order could not be cancelled as it was already filled. We then need to bring back this event again.
                                            int connectionid = Parameters.connection.indexOf(c);
                                            //ArrayList<LinkedAction> fillRequests = getFillRequestsForTracking().get(connectionid);
                                            //fillRequests.add(new LinkedAction(c, orderid, event, EnumLinkedAction.REVERSEFILL));
                                            event.createLinkedAction(event.getParentInternalOrderID(), "REVERSEFILL", "CANCELLEDPARTIALFILL_CANCELLEDNOFILL_COMPLETEFILLED", "0");
                                            // getFillRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderid, event, EnumLinkedAction.REVERSEFILL,0));

                                        } else {
                                            logger.log(Level.INFO, "{0},{1},Execution Manager,Case:111. Cleanse Orders, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getDisplayname(), event.getOriginalOrderSize(), event.getOrderSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getEffectiveTill()});
                                            cleanScaleFalseOrders(c, id, event);
                                        }
                                    }
                                    break;
                                case "110"://position, open order, exit order received.
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
//                                    logger.log(Level.INFO, "{0},{1},Execution Manager,Case:Default, Symbol:{2}, Size={3}, Side:{4}, Limit:{5}, Trigger:{6}, Expiration Time:{7}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getOrderSize(), event.getSide(), event.getLimitPrice(), event.getTriggerPrice(), event.getExpireTime()});
                                    break;
                            }
                        }
                    }

                } else if (event.getOrderStage() == EnumOrderStage.AMEND) {
                    int id = event.getParentSymbolID();
                    //logger.log(Level.FINE, "{0},{1},Execution Manager,OrderReceived Amend. Symbol:{2}, OrderSide:{3}", new Object[]{"ALL", orderReference, Parameters.symbol.get(id).getSymbol(), event.getSide()});
                    for (BeanConnection c : Parameters.connection) {
                        if ("Trading".equals(c.getPurpose()) && accounts.contains(c.getAccountName())) {
                            processOrderAmend(id, c, event);
                        }

                    }
                } else if (event.getOrderStage() == EnumOrderStage.CANCEL) {
                    int id = event.getParentInternalOrderID();
                    //Do cancel processing for combo orders. To be added.
                    //logger.log(Level.INFO, "All Accounts,{0},Execution Manager,Cancel any open orders, Symbol:{1}, OrderSide:{2}", new Object[]{orderReference, Parameters.symbol.get(id).getSymbol(), event.getSide()});
                    for (BeanConnection c : Parameters.connection) {
                        if ("Trading".equals(c.getPurpose()) && accounts.contains(c.getAccountName())) {
                            //check if system is square && order id is to initiate
                            //logger.log(Level.INFO, "{0},{1},Execution Manager,Cancel any open orders, Symbol:{2}, OrderSide:{3}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(id).getSymbol(), event.getSide()});
                            processOrderCancel(c, event);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    private void cleanScaleTrueOrders(BeanConnection c, int id,OrderBean event) {
       if (!event.isCancelRequested()) {
                synchronized (lockLinkedAction) {
                    //for (int orderid : orderids) {
                    event.createLinkedAction(id, "CLOSEPOSITION,PROPOGATE", "UNDEFINED,CANCELLEDPARTIALFILL_CANCELLEDNOFILL_COMPLETEFILLED", "0,0");
                    //getCancellationRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.CLOSEPOSITION,0));
                    //getCancellationRequestsForTracking().get(connectionid).add(new LinkedAction(c, orderids.get(0), event, EnumLinkedAction.PROPOGATE,0));
                    logger.log(Level.INFO, "500,cleanScaleTrueOrders.LinkedActionsAdded,{0}:{1}:{2}:{3}:{4},LinkedAction=CLOSEPOSITION&PROPOGATE",
                            new Object[]{orderReference, c.getAccountName(), event.getParentDisplayName(), String.valueOf(event.getParentInternalOrderID()), String.valueOf(event.getExternalOrderID())});
                    c.getWrapper().cancelOrder(c, event);
                    //}
                    lockLinkedAction.notifyAll();
                }
            }
        }

    private void cleanScaleFalseOrders(BeanConnection c, int id, OrderBean event) {
        if (!event.isCancelRequested()) {
            synchronized (lockLinkedAction) {
                //if cancellation is a success, close position.
                event.createLinkedAction(id, "CLOSEPOSITION,PROPOGATE", "UNDEFINED,CANCELLEDPARTIALFILL_CANCELLEDNOFILL_COMPLETEFILLED", "0,0");
                c.getWrapper().cancelOrder(c, event);
                lockLinkedAction.notifyAll();
            }
        }
    }
    

    /**
     * Returns a list of external order ids for a given symbol. The symbolid
     * should be a parent symbol id.
     *
     * @param c
     * @param id
     * @param orderSide
     * @return
     */
    ArrayList<OrderBean> getOpenOrdersForSide(BeanConnection c, int id, EnumOrderSide orderSide) {
        ArrayList<OrderBean> out = new ArrayList<>();
        String strategy = getS().getStrategy();
        String[] childDisplayNames = TradingUtil.getChildDisplayNames(id);
        boolean zilchorders = zilchOpenOrders(c, id, strategy);
        if (!zilchorders) {
            for (String childDisplayName : childDisplayNames) {
                String searchString = "OQ:*" + c.getAccountName() + ":" + strategy + ":" + Parameters.symbol.get(id).getDisplayname() + ":" + childDisplayName;
                ArrayList<OrderBean>openOrders=getExternalOpenOrders(c, searchString, orderSide);
                out.addAll(openOrders);
                
            }
        }
        return out;
    }

     ArrayList<Integer> getExternalOpenOrdersForSide(BeanConnection c, int id, EnumOrderSide orderSide) {
        ArrayList<Integer> out = new ArrayList<>();
        String strategy = getS().getStrategy();
        String[] childDisplayNames = TradingUtil.getChildDisplayNames(id);
        boolean zilchorders = zilchOpenOrders(c, id, strategy);
        if (!zilchorders) {
            for (String childDisplayName : childDisplayNames) {
                String searchString = "OQ:*" + c.getAccountName() + ":" + strategy + ":" + Parameters.symbol.get(id).getDisplayname() + ":" + childDisplayName;
                ArrayList<OrderBean>openOrders=getExternalOpenOrders(c, searchString, orderSide);
                for (OrderBean ob:openOrders){
                    out.add(ob.getExternalOrderID());
                }                
            }
        }
        return out;
    }

//    private ArrayList<Integer> getExternalOpenOrders(BeanConnection c, String searchString, EnumOrderSide orderSide) {
//        ArrayList<Integer> out = new ArrayList<>();
//        Set<String> oqks = db.getKeys("", searchString);
//        for (String oqki : oqks) {
//            OrderQueueKey oqk = new OrderQueueKey(oqki);
//            ArrayList<NewOrderBean> oqv = c.getOrdersSymbols().get(oqk);
//            NewOrderBean oqvl = oqv.get(oqv.size() - 1);
//            if (TradingUtil.isLiveOrder(c, oqk) && oqvl.getOrderSide().equals(orderSide)) {
//                out.add(oqvl.getExternalOrderID());
//            }
//        }
//        return out;
//    }

        private ArrayList<OrderBean> getExternalOpenOrders(BeanConnection c, String searchString, EnumOrderSide orderSide) {
        ArrayList<OrderBean> out = new ArrayList<>();
        Set<String> oqks = db.getKeys("", searchString);
        for (String oqki : oqks) {
            OrderQueueKey oqk = new OrderQueueKey(oqki);
            ArrayList<OrderBean> oqv = c.getOrders().get(oqk);
            OrderBean oqvl = oqv.get(oqv.size() - 1);
            if (TradingUtil.isLiveOrder(c, oqk) && oqvl.getOrderSide().equals(orderSide)) {
                out.add(oqvl);
            }
        }
        return out;
    }

    void processEntryOrder(int id, BeanConnection c, OrderBean event) {
        int connectionid = Parameters.connection.indexOf(c);
        int tempOpenPosition = this.getOpenPositionCount().get(connectionid);
        int tempMaxPosition = this.maxOpenPositions.get(connectionid);
        if (tempOpenPosition < tempMaxPosition && !s.getPlmanager().isDayProfitTargetHit() && !s.getPlmanager().isDayStopLossHit()) {//enter loop is sl/tp is not hit
            int referenceid = Utilities.getCashReferenceID(Parameters.symbol, id, null);
            double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, event.getOrderSide(), tickSize, event.getOrderType());
            event.put("LimitPrice", String.valueOf(limitprice));
            HashMap<Integer, Order> orders = c.getWrapper().createOrder(event);
            if (orders.size() > 0) {//trading is not halted 
                this.getOpenPositionCount().set(connectionid, tempOpenPosition + 1);
                logger.log(Level.FINE, "500,EntryOrder,{0}:{1}{2}:{3}:{4},OpenPosition={5}",
                        new Object[]{orderReference, c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), String.valueOf(event.getParentInternalOrderID()), -1, this.getOpenPositionCount().get(connectionid)});
                ArrayList<Integer> orderids = c.getWrapper().placeOrder(c, orders, this, event);
                //update orderid structures
                //activeOrders - done
                //ordersInProgress - done
                //orderSymbols -done
                //orderMapping -done
                //ordersToBeCancelled - done
                //ordersToBeFastTracked - not needed
                //ordersToBeRetried - not needed
                //ordersMissed - not needed
                if (event.getEffectiveFrom() != null) {
                    long tempexpire = DateUtil.parseDate("yyyyMMdd HH:mm:ss", event.getEffectiveFrom()).getTime();
                    String key = c.getAccountName() + ":" + this.orderReference + ":" + event.getParentDisplayName() + ":" + event.getChildDisplayName() + ":" + event.getParentInternalOrderID() + ":" + event.getInternalOrderID();
                    java.util.Timer trigger = new java.util.Timer("Timer: " + key + " RScriptProcessor");
                    trigger.schedule(TimedEventTask, new Date(tempexpire));
                }
                if (!c.getOrdersInProgress().contains(event.getParentInternalOrderID())) {
                    c.getOrdersInProgress().add(event.getParentInternalOrderID());
                }
                for (int orderid : orderids) {
                    switch (event.getOrderType()) {
                        case CUSTOMREL:
                            Thread t = new Thread(new OrderTypeRel(id, orderid, c, event, tickSize, this));
                            t.setName("OrderType: REL " + Parameters.symbol.get(id).getDisplayname());
                            t.start();
                            break;
                        default:
                            break;
                    }
                }

            }
        } else {
            logger.log(Level.INFO, "500,EntryOrderNotSent, {0}:{1}:{2}:{3}:{4},OpenPosition={5}:DayStopHit={6}:DayProfitTargetHit:{7}",
                    new Object[]{orderReference, c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), String.valueOf(event.getParentInternalOrderID()), -1, String.valueOf(tempOpenPosition), getS().getPlmanager().isDayProfitTargetHit(), getS().getPlmanager().isDayStopLossHit()});
        }
    }

    TimerTask TimedEventTask = new TimerTask() {
        @Override
        public void run() {
            //iterate through open orders and process
        }
    };

//    void processStubOrder(int id, BeanConnection c, OrderBean event) {
//        HashMap<Integer, Order> orders = new HashMap<>();
//        orders = c.getWrapper().createOrder(event);
//        logger.log(Level.INFO, "303,StubOrder, {0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getInternalorder()});
//        ArrayList<Integer> orderids = c.getWrapper().placeOrder(c, event, orders, this);
//        //orderid structures impacted
//        //activeOrders - yes
//        //ordersInProgress - yes
//        //orderSymbols - yes
//        //orderMapping - yes
//        //ordersToBeCancelled - no
//        //ordersToBeFastTracked - yes
//        //ordersToBeRetried - no
//        //ordersMissed - no
//        ArrayList<SymbolOrderMap> symbolOrders = new ArrayList<>();
//        ArrayList<Integer> linkedOrderIds = new ArrayList<>();
//        for (int orderid : orderids) {
//            c.getActiveOrders().put(new Index(orderReference, id), new BeanOrderInformation(id, c, orderid, 0, event));
//            symbolOrders.add(new SymbolOrderMap(id, orderid));
//            linkedOrderIds.add(orderid);
//            if (!c.getOrdersInProgress().contains(orderid)) {
//                c.getOrdersInProgress().add(orderid);
//            }
//        }
//        c.getOrdersSymbols().put(new Index(orderReference, id), symbolOrders);
//        synchronized (c.lockOrderMapping) {
//            ArrayList<Integer> tempMapping = c.getOrderMapping().get(new Index(orderReference, event.getInternalorder()));
//            tempMapping.addAll(orderids);
//            c.getOrderMapping().put(new Index(orderReference, event.getInternalorder()), tempMapping);
//        }
//    }
    void processExitOrder(int id, BeanConnection c, OrderBean event) {
        Index ind = new Index(event.getOrderReference(), id);
        int positions = c.getPositions().get(ind) == null ? 0 : Math.abs(c.getPositions().get(ind).getPosition());
        int referenceid = Utilities.getCashReferenceID(Parameters.symbol, id, null);
        double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, id, referenceid, event.getOrderSide(), tickSize, event.getOrderType());
        event.put("LimitPrice", String.valueOf(limitprice));
        HashMap<Integer, Order> orders = new HashMap<>();
        if (!event.isScale()) {
            event.put("OrignalOrderSize", String.valueOf(positions));
            orders = c.getWrapper().createOrder(event);
        } else if (event.isScale()) {
            orders = c.getWrapper().createOrder(event);
        }
        logger.log(Level.FINE, "500,ExitOrder,{0}:{1}:{2}:{3}:{4},OrderSize={5}",
                new Object[]{orderReference, c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), String.valueOf(event.getInternalOrderID()), -1, String.valueOf(event.getOriginalOrderSize())});
        ArrayList<Integer> orderids = c.getWrapper().placeOrder(c, orders, this, event);

        //orderid structures impacted
        //activeOrders - yes
        //ordersInProgress - yes
        //orderSymbols - yes
        //orderMapping - yes
        //ordersToBeCancelled - no
        //ordersToBeFastTracked - yes
        //ordersToBeRetried - no
        //ordersMissed - no
        if (event.getEffectiveFrom() != null) {
            long tempexpire = DateUtil.parseDate("yyyyMMdd HH:mm:ss", event.getEffectiveFrom()).getTime();
            String key = c.getAccountName() + ":" + this.orderReference + ":" + event.getParentDisplayName() + ":" + event.getChildDisplayName() + ":" + event.getParentInternalOrderID() + ":" + event.getInternalOrderID();
            java.util.Timer trigger = new java.util.Timer("Timer: " + key + " RScriptProcessor");
            trigger.schedule(TimedEventTask, new Date(tempexpire));
        }

        ArrayList<SymbolOrderMap> symbolOrders = new ArrayList<>();
        ArrayList<Integer> linkedOrderIds = new ArrayList<>();
        if (!c.getOrdersInProgress().contains(event.getParentInternalOrderID())) {
            c.getOrdersInProgress().add(event.getParentInternalOrderID());
        }
        for (int orderid : orderids) {
            switch (event.getOrderType()) {
                case CUSTOMREL:
                    Thread t = new Thread(new OrderTypeRel(id, orderid, c, event, tickSize, this));
                    t.setName("OrderType: REL " + Parameters.symbol.get(id).getDisplayname());
                    t.start();
                    break;
                default:
                    break;
            }
        }
    }

    void processOrderAmend(int id, BeanConnection c, OrderBean event) {
        //synchronized to ensure that only one combo amendment enters this loop
        //An issue when there are multiple combos linked to this same execution manager
        Index ind = new Index(event.getOrderReference(), id);
        int internalorderid = event.getParentInternalOrderID();
        ArrayList<Integer> orderids;
        orderids = TradingUtil.getLinkedOrdersByParentID(db, c, event);
        HashMap<Integer, Double> limitPrices = new HashMap<>();
//        if (event.isScale()) {
//            size = event.getOrderSize();
//        } else {
//            size = c.getPositions().get(ind) == null ? 0 : c.getPositions().get(ind).getPosition();
//        }
        if (!orderids.isEmpty()) { //orders exist for the internal order id
            //place for amended only if acknowledged by ib and not completely filled
            if (event.getOrderStatus() != EnumOrderStatus.COMPLETEFILLED & event.getOrderStatus() != EnumOrderStatus.SUBMITTED) {
                //amend orders
                //<Symbolid,order>
//                HashMap<Integer, Order> ordersHashMap = c.getWrapper().createOrderFromExisting(c, internalorderid, event.getOrderReference());
                boolean combo = false;
                int parentid = -1;

                if (TradingUtil.isSyntheticSymbol(id)) {
                    combo = true;
                    parentid = event.getParentSymbolID();
                }

                if (combo && parentid > -1) {//amend combo orders

                } else if (!combo) {
                    HashMap<Integer, Order> amendedOrders = new HashMap<>();
                    Order ord = (Order) c.getWrapper().createBrokerOrder(event);
                    if (Math.abs(ord.m_auxPrice - event.getTriggerPrice()) > tickSize
                            || Utilities.round(Math.abs(ord.m_lmtPrice - event.getLimitPrice()), 2) >= tickSize) {
                        ord.m_auxPrice = event.getTriggerPrice() > 0 ? event.getTriggerPrice() : 0;
                        ord.m_lmtPrice = event.getLimitPrice() > 0 ? event.getLimitPrice() : 0;
                        if (event.getLimitPrice() > 0 & event.getTriggerPrice() == 0) {
                            ord.m_orderType = "LMT";
                            ord.m_lmtPrice = event.getLimitPrice();
                        } else if (event.getLimitPrice() == 0 && event.getTriggerPrice() > 0 && (event.getOrderSide() == EnumOrderSide.SELL || event.getOrderSide() == EnumOrderSide.COVER)) {
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
                        if (amendedOrders.size() > 0) {
                            logger.log(Level.INFO, "500,AmendmentOrder,{0}:{1}:{2}:{3}:{4},NewLimitPrice={5}",
                                    new Object[]{orderReference, c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), String.valueOf(event.getParentInternalOrderID()), String.valueOf(ord.m_orderId), String.valueOf(ord.m_lmtPrice)});
                            c.getWrapper().placeOrder(c, amendedOrders, this, event);
                        }
                    }
                }

            }
        }
    }

   void processOrderCancel(BeanConnection c, OrderBean event) {
       
        if (!event.isCancelRequested()) {
            logger.log(Level.INFO, "500,CancellationOrder,{0}:{1}:{2}:{3}:{4}",
                    new Object[]{orderReference, c.getAccountName(), event.getParentDisplayName(), String.valueOf(event.getParentInternalOrderID()), String.valueOf(event.getExternalOrderID())});
            c.getWrapper().cancelOrder(c, event);
        }
    }

    @Override
    public synchronized void orderStatusReceived(OrderStatusEvent event) {
        try {
            int orderid = event.getOrderID();
            //update HashMap orders
            BeanConnection c = event.getC();
            synchronized (event.getC().lockOrders) {
                Set<OrderQueueKey> oqks = TradingUtil.getAllOrderKeys(db, c, "OQ:" + orderid + ":" + c.getAccountName() + ":");
                if (oqks.size() == 1) {
                    for (OrderQueueKey oqk : oqks) {
                        int index = c.getOrders().get(oqk).size() - 1;
                        OrderBean ob = c.getOrders().get(oqk).get(index);
                        if (ob != null && ob.getOrderReference().compareToIgnoreCase(orderReference) == 0) {
                            int parentid = ob.getParentSymbolID();
                            int childid = ob.getChildSymbolID();
                            EnumOrderStatus fillStatus = EnumOrderStatus.SUBMITTED;
                            if (TradingUtil.isSyntheticSymbol(parentid)) {//single leg
                                if (event.getRemaining() == 0) {
                                    fillStatus = EnumOrderStatus.COMPLETEFILLED;
                                } else if (event.getRemaining() > 0 && event.getAvgFillPrice() > 0 && !"Cancelled".equals(event.getStatus())) {
                                    fillStatus = EnumOrderStatus.PARTIALFILLED;
                                } else if (("Cancelled".equals(event.getStatus()) || "Inactive".equals(event.getStatus())) && ob.getCurrentFillSize() == 0) {
                                    fillStatus = EnumOrderStatus.CANCELLEDNOFILL;
                                } else if (("Cancelled".equals(event.getStatus()) || "Inactive".equals(event.getStatus())) && ob.getCurrentFillSize() != 0) {
                                    fillStatus = EnumOrderStatus.CANCELLEDPARTIALFILL;
                                } else if ("Submitted".equals(event.getStatus())) {
                                    fillStatus = EnumOrderStatus.ACKNOWLEDGED;
                                }
                            } else {//combo order
                                ArrayList<OrderBean> obs = TradingUtil.getLinkedOrderBeans(orderid, c);
                                HashMap<Integer, Integer> incompleteFills = new HashMap<>();
                                boolean noFill = true;
                                boolean acknowledged = true;
                                obs.remove(Integer.valueOf(orderid));
                                for (OrderBean obv : obs) {
                                    if (obv != null) {
                                        int incomplete = obv.getCurrentOrderSize() - obv.getCurrentFillSize();
                                        if (obv.getCurrentFillSize()> 0) {
                                            noFill = noFill && false;
                                        }
                                        if (incomplete > 0) {
                                            incompleteFills.put(obv.getExternalOrderID(), incomplete);
                                        }
                                        acknowledged = acknowledged && !obv.getOrderStatus().equals(EnumOrderStatus.SUBMITTED);
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
                                } else if ("Submitted".equals(event.getStatus()) && !ob.getOrderStatus().equals(EnumOrderStatus.ACKNOWLEDGED)) {
                                    fillStatus = EnumOrderStatus.ACKNOWLEDGED;
                                }

                            }
                            //if (orderStatus.get(orderid) == null || orderStatus.get(orderid) != fillStatus) {
                            logger.log(Level.INFO, "302,OrderStatus,{0}:{1}:{2}:{3}:{4},OrderStatus={5}",
                                    new Object[]{orderReference, c.getAccountName(), Parameters.symbol.get(parentid).getDisplayname(), ob.getInternalOrderID(), String.valueOf(orderid), fillStatus});
                            orderStatus.put(orderid, fillStatus);
                            //}
                            switch (fillStatus) {
                                case COMPLETEFILLED:
                                    updateFilledOrders(event.getC(), ob, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                                    break;
                                case PARTIALFILLED:
                                    updatePartialFills(event.getC(), ob, event.getFilled(), event.getAvgFillPrice(), event.getLastFillPrice());
                                    break;
                                case CANCELLEDNOFILL:
                                case CANCELLEDPARTIALFILL:
                                    updateCancelledOrders(event.getC(), parentid, ob);
                                    break;
                                case ACKNOWLEDGED:
                                    updateAcknowledgement(event.getC(), ob);
                                //logger.log(Level.FINE, "{0},{1},Execution Manager,Order Acknowledged by IB, Parent Symbol: {2}, Child Symbol: {3}, Orderid: {4}", new Object[]{event.getC().getAccountName(), orderReference, Parameters.symbol.get(parentid).getSymbol(), Parameters.symbol.get(childid).getSymbol(), orderid});
                                default:
                                    break;

                            }
                        }
                    }
                }

            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void TWSErrorReceived(TWSErrorEvent event) {
        try {
            if (deemedCancellation != null && deemedCancellation.contains(event.getErrorCode()) && (!event.getErrorMessage().contains("Cannot cancel the filled order") || !event.getErrorMessage().contains("modify the filled order"))) {//135 is thrown if there is no specified order id with TWS.
                Set<OrderQueueKey> oqks = TradingUtil.getAllOrderKeys(db, event.getConnection(), "OQ:" + event.getId() + ":" + event.getConnection().getAccountName() + ":");
                if (oqks.size() == 1) {
                    for (OrderQueueKey oqk : oqks) {
                        int index = event.getConnection().getOrders().get(oqk).size() - 1;
                        OrderBean ob = event.getConnection().getOrders().get(oqk).get(index);
                        int id = ob.getParentSymbolID();
                        String ref = ob.getOrderReference();
                        if (orderReference.equals(ref)) {
                            logger.log(Level.INFO, "303,TWSError.DeemedOrderCancelledEvent,{0}:{1}:{2}:{3}:{4},ErrorCode:{5}:ErrorMsg={6}",
                                    new Object[]{orderReference, event.getConnection().getAccountName(), Parameters.symbol.get(id).getDisplayname(), String.valueOf(ob.getInternalOrderID()), String.valueOf(ob.getExternalOrderID()), event.getErrorCode(), event.getErrorMessage()});
                            //this.tes.fireOrderStatus(event.getConnection(), event.getId(), "Cancelled", 0, 0, 0, 0, 0, 0D, 0, "");
                            this.updateCancelledOrders(event.getConnection(), id, ob);
                        }
                    }
                }
            }

            if (event.getErrorCode() == 200) {
                //contract id not found
            } else if (event.getErrorCode() == 202 && event.getErrorMessage().contains("Equity with Loan Value")) { //insufficient margin
                 Set<OrderQueueKey> oqks = TradingUtil.getAllOrderKeys(db, event.getConnection(), "OQ:" + event.getId() + ":" + event.getConnection().getAccountName() + ":");
                if (oqks.size() == 1) {
                    for (OrderQueueKey oqk : oqks) {
                        int index = event.getConnection().getOrders().get(oqk).size() - 1;
                        OrderBean ob = event.getConnection().getOrders().get(oqk).get(index);
                        int id = ob.getParentSymbolID();
                        int orderid = event.getId();
                        logger.log(Level.INFO, "303,TWSError.InsufficientMargin,{0}:{1}:{2}:{3}:{4},ErrorCode:{5}:ErrorMsg={6}",
                        new Object[]{orderReference, event.getConnection().getAccountName(), Parameters.symbol.get(id).getDisplayname(),
                            ob.getInternalOrderID(), ob.getExternalOrderID(), event.getErrorCode(), event.getErrorMessage()});
                    }
                }
                //this.getActiveOrders().remove(id); //commented this as activeorders is a part of OMS and impacts all accounts. Insufficient margin is related to a specific account
            } else if (event.getErrorCode() == 202 && event.getErrorMessage().contains("Order Canceled - reason:The order price is outside of the allowable price limits")) {
                Set<OrderQueueKey> oqks = TradingUtil.getAllOrderKeys(db, event.getConnection(), "OQ:" + event.getId() + ":" + event.getConnection().getAccountName() + ":");
                if (oqks.size() == 1) {
                    for (OrderQueueKey oqk : oqks) {
                        int index = event.getConnection().getOrders().get(oqk).size() - 1;
                        OrderBean ob = event.getConnection().getOrders().get(oqk).get(index);
                        int id = ob.getParentSymbolID();
                        int orderid = event.getId();
                        //send email
                        Thread t = new Thread(new Mail(event.getConnection().getOwnerEmail(), "Order placed by inStrat for symbol " + Parameters.symbol.get(id).getBrokerSymbol() + " over strategy " + getS().getStrategy() + " was outside permissible range. Please check inStrat status", "Algorithm SEVERE ALERT"));
                        t.start();
                        logger.log(Level.INFO, "205,OrderCancelledEvent,{0}", new Object[]{event.getConnection().getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getErrorCode() + delimiter + event.getId() + delimiter + event.getErrorMessage()});
                        this.tes.fireOrderStatus(event.getConnection(), event.getId(), "Cancelled", 0, 0, 0, 0, 0, 0D, 0, "");
                    }
                }               
            } else if (event.getErrorCode() == 202 && event.getErrorMessage().contains("Order Canceled - reason:")) {
                Set<OrderQueueKey> oqks = TradingUtil.getAllOrderKeys(db, event.getConnection(), "OQ:" + event.getId() + ":" + event.getConnection().getAccountName() + ":");
                if (oqks.size() == 1) {
                    for (OrderQueueKey oqk : oqks) {
                        int index = event.getConnection().getOrders().get(oqk).size() - 1;
                        OrderBean ob = event.getConnection().getOrders().get(oqk).get(index);
                        int id = ob.getParentSymbolID();
                        int orderid = event.getId();
                         logger.log(Level.INFO, "205,OrderCancelledEvent,{0}", new Object[]{event.getConnection().getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(id).getDisplayname() + delimiter + event.getErrorCode() + delimiter + event.getId() + delimiter + event.getErrorMessage()});
                    this.tes.fireOrderStatus(event.getConnection(), event.getId(), "Cancelled", 0, 0, 0, 0, 0, 0D, 0, "");
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    private void reduceStub(BeanConnection c, OrderBean ob) {
        HashMap<Integer, Integer> stubs = comboStubSize(c,ob); //holds <childid,stubsize>
        Boolean stubSizeZero = true;
        for (Integer stub : stubs.values()) {
            if (stub != 0) {
                stubSizeZero = stubSizeZero && false;
            }
        }
        int parentid=ob.getParentSymbolID();
        if (!stubSizeZero) {
            OrderBean newob=new OrderBean(ob);
            newob.setSpecifiedBrokerAccount(c.getAccountName());
            int internalorderid = TradingUtil.getInternalOrderID();
            newob.setInternalOrderID(internalorderid);
            newob.setParentInternalOrderID(internalorderid);
            tes.fireOrderEvent(newob);
        }
    }

    private void completeStub(BeanConnection c, OrderBean ob) {
        int baselineSize = ob.getOrderSide() == EnumOrderSide.BUY || ob.getOrderSide() == EnumOrderSide.COVER ? ob.getCurrentOrderSize() : -ob.getCurrentOrderSize();
        HashMap<Integer, Integer> stubs = comboStubSize(c, ob); //holds <childid,stubsize>
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
            OrderBean newob=new OrderBean(ob);
            newob.setSpecifiedBrokerAccount(c.getAccountName());
            int internalorderid=TradingUtil.getInternalOrderID();
            newob.setInternalOrderID(internalorderid);
            newob.setParentInternalOrderID(internalorderid);
            tes.fireOrderEvent(newob);
        }
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

    //fireLinkedActions is triggered on partial/ complete fill and on order cancellation
    private synchronized void fireLinkedActions(BeanConnection c, OrderBean ob) {
        int orderid=ob.getExternalOrderID();
        logger.log(Level.FINE, "500,LinkedActionOrderID,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid});
        //this function only supports linked actions for cancellation. What about linked action for fills?
        if(orderid>=0){
            int parentid = ob.getParentSymbolID();
        int internalorderid = ob.getInternalOrderID();
        //if this was a requested cancellation, fire any event if needed
        int connectionid = Parameters.connection.indexOf(c);
        synchronized (lockLinkedAction) {
            ArrayList<LinkedAction> cancelledOrders = (ArrayList<LinkedAction>) getCancellationRequestsForTracking().get(connectionid).clone();
            ArrayList<LinkedAction> itemsToRemove = new ArrayList<>();
            ArrayList<LinkedAction> actionsToFire = new ArrayList<>();
            int i = 0;
            for (LinkedAction f : cancelledOrders) {
                if (f.orderID == orderid && i == 0 && (ob.getOrderStatus().equals(EnumOrderStatus.CANCELLEDNOFILL) || ob.getOrderStatus().equals(EnumOrderStatus.CANCELLEDPARTIALFILL) || ob.getOrderStatus().equals(EnumOrderStatus.COMPLETEFILLED))) { //only fire one linkedaction at one time.
                    new java.util.Timer().schedule(
                            new java.util.TimerTask() {
                        @Override
                        public void run() {
                            fireLinkedAction(c, ob, f.action, f);
                        }
                    },
                            f.delay * 1000
                    );
                    //fireLinkedAction(c, orderid, f.action, f);
                    i = i + 1;
                    itemsToRemove.add(f);
                }
            }

            List<LinkedAction> filledOrders = Collections.synchronizedList(this.getFillRequestsForTracking().get(connectionid));
            i = 0;
            for (LinkedAction f : filledOrders) {
                if (f.orderID == orderid && i == 0 && (ob.getOrderStatus().equals(EnumOrderStatus.COMPLETEFILLED))) {//only fire one linked action at one time
                    //logger.log(Level.INFO, "{0},{1},Execution Manager,OrderFilled. Linked Order being generated, OrderID Cancelled:{2}, symbol:{3}", new Object[]{c.getAccountName(), orderReference, orderid, Parameters.symbol.get(parentid).getSymbol()});
//                    fireLinkedAction(c, orderid, f.action, f);
                    actionsToFire.add(f);
                    i = i + 1;
                    //iter.remove();

                    itemsToRemove.add(f);
                }
            }

            for (LinkedAction f : actionsToFire) {
                new java.util.Timer().schedule(
                        new java.util.TimerTask() {
                    @Override
                    public void run() {
                        fireLinkedAction(c, ob, f.action, f);
                    }
                },
                        f.delay * 1000
                );

//                fireLinkedAction(c, orderid, f.action, f);
            }

            for (LinkedAction f : itemsToRemove) {
                logger.log(Level.INFO, "204,LinkedActionRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + f.action + delimiter + internalorderid + delimiter + f.orderID});
                getCancellationRequestsForTracking().get(connectionid).remove(f);
                getFillRequestsForTracking().get(connectionid).remove(f);
            }

            lockLinkedAction.notifyAll();
        }
        }
        

    }

    private synchronized void removeLinkedActions(BeanConnection c, OrderBean ob) {
        int orderid=ob.getExternalOrderID();
        if(orderid>=0){
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


    }

    private synchronized void removeLinkedActions(BeanConnection c, OrderBean ob, EnumLinkedAction f) {
        int orderid=ob.getExternalOrderID();
        if(orderid>=0){
            int parentid = ob.getParentSymbolID() - 1;
        int internalorderid = ob.getInternalOrderID();
        //if this was a requested cancellation, fire any event if needed
        int connectionid = Parameters.connection.indexOf(c);
        synchronized (lockLinkedAction) {
            Iterator<LinkedAction> iter;
            ArrayList<LinkedAction> cancelledOrders = (ArrayList<LinkedAction>) getCancellationRequestsForTracking().get(connectionid).clone();
            //iter = cancelledOrders.iterator();
            ArrayList<LinkedAction> itemsToRemove = new ArrayList<>();
            for (LinkedAction f1 : cancelledOrders) {
                if (f1.orderID == orderid && f.equals(f1.action)) {
                    //fireLinkedAction(c, orderid, f.action, f);
                    //iter.remove();
                    itemsToRemove.add(f1);
                }
            }
            ArrayList<LinkedAction> filledOrders = this.getFillRequestsForTracking().get(connectionid);
            iter = filledOrders.iterator();
            for (LinkedAction f1 : filledOrders) {
                if (f1.orderID == orderid && f.equals(f1.action)) {
                    //logger.log(Level.INFO, "{0},{1},Execution Manager,OrderFilled. Linked Order being generated, OrderID Cancelled:{2}, symbol:{3}", new Object[]{c.getAccountName(), orderReference, orderid, Parameters.symbol.get(parentid).getSymbol()});
                    //fireLinkedAction(c, orderid, f.action, f);
                    itemsToRemove.add(f1);
                }
            }
            for (LinkedAction f1 : itemsToRemove) {
                logger.log(Level.FINE, "307,LinkedActionRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + f1.action + delimiter + internalorderid + delimiter + f1.orderID});
                getCancellationRequestsForTracking().get(connectionid).remove(f);
            }
            for (LinkedAction f1 : itemsToRemove) {
                logger.log(Level.FINE, "307,LinkedActionRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + f1.action + delimiter + internalorderid + delimiter + f1.orderID});
                getFillRequestsForTracking().get(connectionid).remove(f1);
            }
            lockLinkedAction.notifyAll();
        }
        }
        

    }

    private void fireLinkedAction(BeanConnection c, OrderBean ob, EnumLinkedAction nextAction, LinkedAction f) {
        int orderid=ob.getExternalOrderID();
        int parentid = ob.getParentSymbolID();
        int size;
        OrderBean newob=new OrderBean(ob);
        
        switch (nextAction) {
            case COMPLETEFILL:
                size = ob.getCurrentOrderSize() - ob.getCurrentFillSize();
                newob.setOrderType(EnumOrderType.MKT);
                newob.setSpecifiedBrokerAccount(c.getAccountName());
                newob.setCurrentOrderSize(size);
                int internalorderid = TradingUtil.getInternalOrderID();
                newob.setInternalOrderID(internalorderid);
                newob.setParentInternalOrderID(internalorderid);
                logger.log(Level.INFO, "204,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + String.valueOf(newob.getInternalOrderID()) + delimiter + String.valueOf(newob.getExternalOrderID())});
                tes.fireOrderEvent(ob);
                break;
            case REVERSEFILL:
                size = ob.getCurrentFillSize();
                int newOrderSize = size;
                logger.log(Level.INFO, "204,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + String.valueOf(ob.getInternalOrderID()) + delimiter + String.valueOf(ob.getExternalOrderID()) + delimiter + newOrderSize});
                if (newOrderSize > 0) {
                    ob.setCurrentOrderSize(newOrderSize);
//                e = OrderEvent.fastClose(Parameters.symbol.get(parentid), reverse(ob.getParentOrderSide()), size, orderReference);
                    ob.setSpecifiedBrokerAccount(c.getAccountName());
                internalorderid = TradingUtil.getInternalOrderID();
                newob.setInternalOrderID(internalorderid);
                newob.setParentInternalOrderID(internalorderid);
                    tes.fireOrderEvent(ob);
                }
                break;
            case CLOSEPOSITION:
                size = c.getPositions().get(new Index(orderReference, parentid)) != null ? c.getPositions().get(new Index(orderReference, parentid)).getPosition() : 0;
                if (size != 0) {
                    EnumOrderSide side = size > 0 ? EnumOrderSide.SELL : EnumOrderSide.COVER;
                     internalorderid = TradingUtil.getInternalOrderID();
                newob.setInternalOrderID(internalorderid);
                newob.setParentInternalOrderID(internalorderid);
                    newob.setSpecifiedBrokerAccount(c.getAccountName());
                    newob.setOrderType(EnumOrderType.MKT);
                    logger.log(Level.INFO, "204,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + String.valueOf(ob.getInternalOrderID()) + delimiter + String.valueOf(ob.getExternalOrderID())});
                    int connectionid = Parameters.connection.indexOf(c);
                    ArrayList<LinkedAction> filledOrders = this.getFillRequestsForTracking().get(connectionid);
                    for (LinkedAction f1 : filledOrders) {
                        if (f1.e.getParentSymbolID() == parentid && f.orderID == orderid) {
                            f.orderID = -1; //reset the orderid for tracking completed fills. 
                            break;//update the first occurrence
                        }
                    }
                    tes.fireOrderEvent(newob);

                }
                break;
            case REVERSESTUB:
                int parentposition = ob.getCurrentFillSize();
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
                    internalorderid = TradingUtil.getInternalOrderID();
                newob.setInternalOrderID(internalorderid);
                newob.setParentInternalOrderID(internalorderid);
                    newob.setSpecifiedBrokerAccount(c.getAccountName());
                    newob.setOrderType(EnumOrderType.MKT);
                    newob.setOrderSide(parentside);                   
                    logger.log(Level.INFO, "204,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + ob.getInternalOrderID()});
                    tes.fireOrderEvent(newob);
                }
                break;
            case PROPOGATE:
                logger.log(Level.INFO, "204,LinkedAction,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + nextAction + delimiter + String.valueOf(ob.getInternalOrderID()) + delimiter + String.valueOf(ob.getExternalOrderID())});
                tes.fireOrderEvent(f.e);
                break;
            default:
                break;
        }
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
                Set<OrderBean>obs=TradingUtil.getLiveOrders(db, c, "*");
                Date now =new Date();
                for(OrderBean ob:obs){
                    if(ob.getEffectiveTillDate().after(now)){
                        c.getWrapper().cancelOrder(c, ob);
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
            this.lastExecutionRequestTime = DateUtil.getFormattedDate("yyyyMMdd-HH:mm:ss", new Date().getTime());
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
        OrderBean event = new OrderBean();
        if (position > 0) {
           OrderBean oqv=new OrderBean();
           int internalorderid=getS().getInternalOrderID();
           String displayName=Parameters.symbol.get(id).getDisplayname();
           oqv.setInternalOrderID(internalorderid);
           oqv.setParentInternalOrderID(internalorderid);
           oqv.setOrderSide(EnumOrderSide.SELL);
           oqv.setLimitPrice(0);
           oqv.setOriginalOrderSize(position);
           oqv.setCurrentOrderSize(position);
           oqv.setTriggerPrice(0);
           oqv.setOrderReason(EnumOrderReason.SQUAREOFF);
           oqv.setOrderStage(EnumOrderStage.INIT);
           oqv.setOrderReference(strategy);
           oqv.setOrderType(EnumOrderType.MKT);
           oqv.setParentDisplayName(displayName);
           oqv.setChildDisplayName(displayName);
           String key="OQ:-1:"+c.getAccountName()+":"+strategy+":"+displayName+":"+displayName+":"+internalorderid+":"+internalorderid;
           db.insertOrder(key, oqv);
        } else if (position < 0) {
           position=Math.abs(position);
           OrderBean oqv=new OrderBean();
           int internalorderid=getS().getInternalOrderID();
           String displayName=Parameters.symbol.get(id).getDisplayname();
           oqv.setInternalOrderID(internalorderid);
           oqv.setParentInternalOrderID(internalorderid);
           oqv.setOrderSide(EnumOrderSide.COVER);
           oqv.setLimitPrice(0);
           oqv.setCurrentOrderSize(position);
           oqv.setOriginalOrderSize(position);
           oqv.setTriggerPrice(0);
           oqv.setOrderReason(EnumOrderReason.SQUAREOFF);
           oqv.setOrderStage(EnumOrderStage.INIT);
           oqv.setOrderReference(strategy);
           oqv.setOrderType(EnumOrderType.MKT);
           oqv.setChildDisplayName(displayName);
           oqv.setParentDisplayName(displayName);
           String key="OQ:-1:"+c.getAccountName()+":"+strategy+":"+displayName+":"+displayName+":"+internalorderid+":"+internalorderid;
           db.insertOrder(key, oqv);
        }
    }

    public Boolean zilchOpenOrders(BeanConnection c, int id, String strategy) {
        String searchString = "OQ:*" + c.getAccountName() + ":" + strategy + ":" + Parameters.symbol.get(id).getDisplayname();
        Set<String> oqks = db.getKeys("", searchString);
        for (String oqki : oqks) {
            OrderQueueKey oqk = new OrderQueueKey(oqki);
            if (TradingUtil.isLiveOrder(c, oqk)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Cancel all open orders for a given id.id can be a parent or child id.
     *
     * @param c
     * @param id
     * @param strategy
     */
    public void cancelOpenOrders(BeanConnection c, int id, String strategy) {
        String[] childDisplayNames = TradingUtil.getChildDisplayNames(id);
        for (String childDisplayName : childDisplayNames) {
            String searchString = "OQ:*" + c.getAccountName() + ":" + strategy + ":" + Parameters.symbol.get(id).getDisplayname() + ":" + childDisplayName;
            cancelOpenOrders(c, searchString);
        }
    }

    /**
     * Helper function that cancels live order(s) linked to an orderqueuekey
     * pattern
     *
     * @param c
     * @param searchString
     */
    private void cancelOpenOrders(BeanConnection c, String searchString) {
        Set<String> oqks = db.getKeys("", searchString);
        for (String oqki : oqks) { //for each orderqueuekey string
            OrderQueueKey oqk = new OrderQueueKey(oqki);
            if (TradingUtil.isLiveOrder(c, oqk)) { //if the order is live
                ArrayList<OrderBean> oqv = c.getOrders().get(oqk);
                OrderBean oqvl = oqv.get(oqv.size() - 1);
                if (oqvl.getExternalOrderID() > 0) { //if there is an external order id refering to the broker
                    //check an earlier cancellation request is not pending and if all ok then cancel     }
                    if (!oqvl.isCancelRequested()) { //if there is no prior cancelation requested
                        logger.log(Level.INFO, "309,CancelOpenOrders,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + oqvl.getExternalOrderID()});
                        c.getWrapper().cancelOrder(c, oqvl);
                    }
                }
            }

        }
    }

    private boolean updateFilledOrders(BeanConnection c, OrderBean ob, int filled, double avgFillPrice, double lastFillPrice) {
        int orderid=ob.getExternalOrderID();
        int internalorderid = ob.getInternalOrderID();
        int parentid = ob.getParentSymbolID() - 1;
        int childid = ob.getChildSymbolID() - 1;
        boolean combo = parentid != childid;
        String strategy = ob.getOrderReference();
        Index ind = new Index(strategy.toLowerCase(), parentid);
        ArrayList newComboFills;
        int cpid = 0;

        Boolean orderProcessed = false;
        //update OrderBean
        int fill = filled - ob.getCurrentFillSize();
        if (lastFillPrice == 0 && fill > 0) { //execution info from execDetails. calculate lastfillprice
            lastFillPrice = (filled * avgFillPrice - ob.getCurrentFillSize() * ob.getCurrentFillPrice()) / fill;
            //logger.log(Level.FINE, "{0},{1},Execution Manager,Calculated incremental fill price, Fill Size: {2}, Fill Price: {3}", new Object[]{c.getAccountName(), orderReference, fill, lastFillPrice});
        }

        ArrayList priorFillDetails = new ArrayList();
        if (fill > 0) {
            //1. Update orderBean
            ob.setCurrentFillSize(filled);
            ob.setCurrentFillPrice(avgFillPrice);
            ob.setTotalFillPrice((fill*lastFillPrice+ob.getTotalFillPrice()*ob.getTotalFillSize())/(ob.getTotalFillSize()+fill));
            ob.setTotalFillSize(fill+ob.getTotalFillSize());
            ob.setOrderStatus(EnumOrderStatus.COMPLETEFILLED);
            if (TradingUtil.isSyntheticSymbol(ob.getParentSymbolID())) {
                OrderBean obp=TradingUtil.getSyntheticOrder(db, c, ob);
                newComboFills = comboFillSize(c, obp);
                if (((int) newComboFills.get(0)) == obp.getCurrentOrderSize()) {
                    obp.setOrderStatus(EnumOrderStatus.COMPLETEFILLED);
                } else {
                    ob.setOrderStatus(EnumOrderStatus.PARTIALFILLED);
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
            p.setStrategy(strategy);
            //3. Update BeanPosition
            if (p.getChildPosition().isEmpty()) {
                //3a. Update Single Leg Position
                int origposition = p.getPosition();
                if (ob.getOrderSide() == EnumOrderSide.SELL || ob.getOrderSide() == EnumOrderSide.SHORT ) {
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
                ob.setCurrentFillSize(filled);
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
                        if (ob.getOrderSide() == EnumOrderSide.SELL || ob.getOrderSide() == EnumOrderSide.SHORT) {
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
                OrderBean pob=TradingUtil.getSyntheticOrder(db, c, ob);
                int parentNewPosition = pob.getOrderSide().equals(EnumOrderSide.BUY) || pob.getOrderSide().equals(EnumOrderSide.SHORT) ? (int) lowerBoundPosition.get(0) : (int) upperBoundPosition.get(0);
                if (parentNewPosition != p.getPosition()) {//there is a combo parent fill
                    int origposition = p.getPosition();
                    int parentfill = parentNewPosition - p.getPosition();
                    double parentfillprice = 0D;
                    double positionPrice = p.getPrice();
                    if (pob.getOrderSide().equals(EnumOrderSide.BUY) || pob.getOrderSide().equals(EnumOrderSide.SHORT)) {
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

            if (combo) {
                logger.log(Level.INFO, "308,inStratCompleteFillParent,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(parentid).getDisplayname() + delimiter + p.getPosition() + delimiter + p.getPrice()});
                logger.log(Level.INFO, "308,inStratCompleteFillChild,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(childid).getDisplayname() + delimiter + p.getChildPosition().get(cpid).getPosition() + delimiter + p.getChildPosition().get(cpid).getPrice() + delimiter + fill + delimiter + lastFillPrice});
            }

            //send email
            if (!combo) {
                Thread t = new Thread(new Mail(c.getOwnerEmail(), "Order Completely Executed. Account: " + c.getAccountName() + ", Strategy: " + strategy + ", Symbol: " + Parameters.symbol.get(parentid).getDisplayname() + ", Fill Size: " + fill + ", Symbol Position: " + p.getPosition() + ", Fill Price: " + avgFillPrice + " ,Reason: " + ob.getOrderReason().toString(), "Algorithm Alert - " + strategy.toUpperCase()));
                t.start();
            } else {
                Thread t = new Thread(new Mail(c.getOwnerEmail(), "Order Completely Executed. Account: " + c.getAccountName() + ", Strategy: " + strategy + ", Combo Symbol: " + Parameters.symbol.get(parentid).getDisplayname() + ",Filled Child: " + Parameters.symbol.get(childid).getBrokerSymbol() + ", Child Fill Size: " + fill + ", Child Position: " + p.getChildPosition().get(cpid).getPosition() + ", Child Fill Price: " + avgFillPrice + ", Combo Position: " + p.getPosition() + ",Combo Price: " + p.getPrice() + " ,Reason:" + ob.getOrderReason().toString(), "Algorithm Alert - " + strategy.toUpperCase()));
                t.start();
            }

            //if (c.getOrdersInProgress().contains(orderid) && ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
            if (c.getOrdersInProgress().contains(orderid)) {
                c.getOrdersInProgress().remove(Integer.valueOf(orderid));
                logger.log(Level.FINE, "307,OrderProgressQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid + delimiter + Parameters.symbol.get(parentid).getDisplayname()});
            }
            if (c.getOrdersMissed().contains(orderid) && ob.getOrderStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersMissed().remove(Integer.valueOf(orderid));
                logger.log(Level.FINE, "307,OrderMissedQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid});
            }
            int tradeFill = 0;
            switch (ob.getOrderSide()) {
                case BUY:
                case SHORT:
                    tradeFill = Math.abs(fill);
                    break;
                case SELL:
                case COVER:
                    tradeFill = Math.abs(fill);
                    break;
                default:
                    break;
            }

            //For exits we send incremental fill = abs(fill) as tradeupdate ** for exits only ** aggregates fills.
            //For entry, there each fill with the same internal order id is updated.
            updateTrades(c, ob, p, tradeFill, avgFillPrice);
            if (ob.getOrderSide() == EnumOrderSide.SELL || ob.getOrderSide() == EnumOrderSide.COVER) {
                //if (fill != 0 && ((ob.getChildFillSize()==ob.getChildOrderSize())||(ob.getParentFillSize()==ob.getParentOrderSize()))) { //do not reduce open position count if duplicate message, in which case fill == 0
                if (fill != 0 && (ob.getCurrentFillSize() == ob.getCurrentOrderSize())) { //do not reduce open position count if duplicate message, in which case fill == 0
                    int connectionid = Parameters.connection.indexOf(c);
                    int tmpOpenPositionCount = this.getOpenPositionCount().get(connectionid);
                    int openpositioncount = tmpOpenPositionCount - 1;
                    this.getOpenPositionCount().set(connectionid, openpositioncount);
                    logger.log(Level.INFO, "206,OpenPosition,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + openpositioncount});
                }
            }
            //if this was a requested cancellation, fire any event if needed
            int connectionid = Parameters.connection.indexOf(c);
            ArrayList<LinkedAction> filledOrders = this.getFillRequestsForTracking().get(connectionid);
            for (LinkedAction f : filledOrders) {
                if (f.e.getChildSymbolID() == childid && f.orderID == -1) {//only fire one linked action at one time
                    f.orderID = orderid;
                    break;//update the first occurrence
                }
            }
            fireLinkedActions(c, ob);
        }
        return orderProcessed;
    }

    private boolean updateCancelledOrders(BeanConnection c, int id, OrderBean ob) {
        int parentid = ob.getParentSymbolID();
        int internalorderid = ob.getInternalOrderID();
        boolean stubOrderPlaced = false;
        //ob.setCancelRequested(false);
        if (ob.getCurrentFillSize()> 0 && ob.getCurrentFillSize() < ob.getCurrentOrderSize()) {
            ob.setOrderStatus(EnumOrderStatus.CANCELLEDPARTIALFILL);
        } else if (ob.getCurrentFillSize()> 0 && ob.getCurrentFillSize() == ob.getCurrentOrderSize()) {
            ob.setOrderStatus(EnumOrderStatus.COMPLETEFILLED);
        } else {
            ob.setOrderStatus(EnumOrderStatus.CANCELLEDNOFILL);
        }

        //ordersToBeRetried - not needed. Orders to be retried is cleansed only if there are linked orders
        //Reduce position count if needed
        if (!ob.isScale() && (ob.getOrderSide() == EnumOrderSide.BUY || ob.getOrderSide() == EnumOrderSide.SHORT) && ob.getParentSymbolID()==ob.getChildSymbolID()) {
            //reduce open position count
            int connectionid = Parameters.connection.indexOf(c);
            ArrayList<Integer> cancelledOrdersForConnection = cancelledOrdersAcknowledgedByIB.get(connectionid);
            if (!cancelledOrdersForConnection.contains(internalorderid)) {
                cancelledOrdersForConnection.add(internalorderid);
                cancelledOrdersAcknowledgedByIB.set(connectionid, cancelledOrdersForConnection);
                int tmpOpenPositionCount = this.getOpenPositionCount().get(connectionid);
                int openpositioncount = tmpOpenPositionCount - 1;
                this.getOpenPositionCount().set(connectionid, openpositioncount);
                logger.log(Level.INFO, "206,OpenPosition,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + openpositioncount});

            }
        }

        //Process combo stubs
        if (TradingUtil.isSyntheticSymbol(ob.getParentSymbolID()) && (ob.getOrderSide().equals(EnumOrderSide.BUY) || ob.getOrderSide().equals(EnumOrderSide.SHORT))) {
            ArrayList<OrderBean>linkedOrders=TradingUtil.getLinkedOrderBeans(ob.getExternalOrderID(), c);
            if(linkedOrders.size()>0){
                for(OrderBean obl:linkedOrders){
                    c.getWrapper().cancelOrder(c, obl);
                }
            }else{
                reduceStub(c, ob);
                stubOrderPlaced = true;
            }
        } else if (TradingUtil.isSyntheticSymbol(ob.getParentSymbolID()) && (ob.getOrderSide().equals(EnumOrderSide.SELL) || ob.getOrderSide().equals(EnumOrderSide.COVER))) {
            ArrayList<OrderBean>linkedOrders=TradingUtil.getLinkedOrderBeans(ob.getExternalOrderID(), c);
            if(linkedOrders.size()>0){
                for(OrderBean obl:linkedOrders){
                    c.getWrapper().cancelOrder(c, obl);
                }
            }else{
                completeStub(c, ob);
                stubOrderPlaced = true;
            }
        }
        if (!stubOrderPlaced) {
            fireLinkedActions(c, ob);
        } else {
            removeLinkedActions(c, ob);
        }
        return true;
    }

    private boolean updatePartialFills(BeanConnection c, OrderBean ob, int filled, double avgFillPrice, double lastFillPrice) {
        int orderid=ob.getExternalOrderID();
        int parentid = ob.getParentSymbolID();
        int internalOrderID = ob.getInternalOrderID();
        int childid = ob.getChildSymbolID();
        String strategy = ob.getOrderReference();
        Index ind = new Index(strategy, parentid);
        boolean combo = parentid != childid;
        int cpid = 0;

        //identify incremental fill
        int fill = filled - ob.getCurrentFillSize();
        if (lastFillPrice == 0 && fill > 0) { //execution info from execDetails. calculate lastfillprice
            lastFillPrice = (filled * avgFillPrice - ob.getCurrentFillSize() * ob.getCurrentFillPrice()) / fill;
        }
        ArrayList priorFillDetails = new ArrayList();
        ob.setOrderStatus(EnumOrderStatus.PARTIALFILLED);
        OrderBean pob;
        if(TradingUtil.isSyntheticSymbol(parentid)){
            pob=TradingUtil.getSyntheticOrder(db, c, ob);
            pob.setOrderStatus(EnumOrderStatus.PARTIALFILLED);
        }
        
        if (c.getOrdersInProgress().contains(orderid) && ob.getOrderStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
            c.getOrdersInProgress().remove(Integer.valueOf(orderid));
            logger.log(Level.FINE, "307,OrderProgressQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid + delimiter + Parameters.symbol.get(parentid).getDisplayname()});
        }

        if (fill > 0) {
            //1. Update orderbean
            ob.setTotalFillPrice((fill*lastFillPrice+ob.getTotalFillSize()*ob.getTotalFillPrice())/(fill+ob.getTotalFillSize()));
            ob.setTotalFillSize(fill+ob.getTotalFillSize());
            ob.setCurrentFillSize(filled);
            ob.setCurrentFillPrice(avgFillPrice);
            
            if (ob.getCurrentOrderSize() - ob.getCurrentFillSize() == 0) {
                ob.setOrderStatus(EnumOrderStatus.COMPLETEFILLED);
                logger.log(Level.INFO, "DEBUG: Child Symbol: {0},ChildStatus:{1}", new Object[]{Parameters.symbol.get(childid).getDisplayname(), ob.getOrderStatus()});
            }
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
                if (ob.getOrderSide() == EnumOrderSide.SELL || ob.getOrderSide() == EnumOrderSide.SHORT) {
                    fill = -fill;
                }
                //logger.log(Level.FINE, "{0},{1},Execution Manager,Updated positions on partial fill,Symbol:{2}, Incremental Fill Reported:{3},Total Fill={4},Position within Program={5}, Side={6}", new Object[]{c.getAccountName(), orderReference, Parameters.symbol.get(childid).getSymbol(), fill, filled, ob.getChildFillSize(), ob.getParentOrderSide()});
                double realizedPL = (origposition + fill) == 0 && origposition != 0 ? -(origposition * p.getPrice() + fill * lastFillPrice) * pointValue + p.getProfit() : p.getProfit();
                double positionPrice = (origposition + fill) == 0 ? 0 : (p.getPosition() * p.getPrice() + fill * lastFillPrice) / (origposition + fill);
                logger.log(Level.FINE, "308,inStratPartialFill,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(parentid).getDisplayname() + delimiter + p.getPosition() + delimiter + p.getPrice() + delimiter + fill + delimiter + lastFillPrice});
                p.setPrice(positionPrice);
                p.setProfit(realizedPL);
                p.setPosition(origposition + fill);
                p.setStrategy(strategy);
                c.getPositions().put(ind, p);
                ob.setCurrentFillSize(filled);
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
                        if (ob.getOrderSide()== EnumOrderSide.SELL || ob.getOrderSide() == EnumOrderSide.SHORT ) {
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
                OrderBean obp=TradingUtil.getSyntheticOrder(db, c, ob);
                ArrayList lowerBoundPosition = lowerBoundParentPosition(p);
                ArrayList upperBoundPosition = upperBoundParentPosition(p);
                int parentNewPosition = obp.getOrderSide().equals(EnumOrderSide.BUY) || obp.getOrderSide().equals(EnumOrderSide.SHORT) ? (int) lowerBoundPosition.get(0) : (int) upperBoundPosition.get(0);
                if (parentNewPosition != p.getPosition()) {//there is a combo parent fill
                    int origposition = p.getPosition();
                    int parentfill = parentNewPosition - p.getPosition();
                    double parentfillprice = 0D;
                    double positionPrice;
                    if (obp.getOrderSide().equals(EnumOrderSide.BUY) || obp.getOrderSide().equals(EnumOrderSide.SHORT)) {
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
                    //we need to update ob.parentfillsize for combo orders. TBD

                }
                if(TradingUtil.getLinkedOrderIds(orderid, c).size()>0) {
                    obp.setOrderStatus(EnumOrderStatus.PARTIALFILLED);
                
            }
            // Moved check for setting childorderstatus = COMPLETEFILLED to later in the code

            
            }

            if (combo) {
                logger.log(Level.INFO, "308,inStratPartialFillParent,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(parentid).getDisplayname() + delimiter + p.getPosition() + delimiter + p.getPrice()});
                logger.log(Level.INFO, "308,inStratPartialFillChild,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Parameters.symbol.get(childid).getDisplayname() + delimiter + p.getChildPosition().get(cpid).getPosition() + delimiter + p.getChildPosition().get(cpid).getPrice() + delimiter + fill + delimiter + lastFillPrice});
            }
            //send email
            if (!combo) {
                Thread t = new Thread(new Mail(c.getOwnerEmail(), "Order Partially Executed. Account: " + c.getAccountName() + ", Strategy: " + strategy + ", Symbol: " + Parameters.symbol.get(parentid).getDisplayname() + ", Fill Size: " + fill + ", Symbol Position: " + p.getPosition() + ", Fill Price: " + avgFillPrice + " , Reason: " + ob.getOrderReason().toString(), "Algorithm Alert - " + strategy.toUpperCase()));
                t.start();
            } else {
                Thread t = new Thread(new Mail(c.getOwnerEmail(), "Order Partially Executed. Account: " + c.getAccountName() + ", Strategy: " + strategy + ", Combo Symbol: " + Parameters.symbol.get(parentid).getDisplayname() + ",Filled Child: " + Parameters.symbol.get(childid).getBrokerSymbol() + ", Child Fill Size: " + fill + ", Child Position: " + p.getChildPosition().get(cpid).getPosition() + ", Child Fill Price: " + avgFillPrice + ", Combo Position: " + p.getPosition() + ",Combo Price: " + p.getPrice() + " ,Reason :" + ob.getOrderReason().toString(), "Algorithm Alert - " + strategy.toUpperCase()));
                t.start();
            }
 
            if (c.getOrdersInProgress().contains(orderid) && ob.getOrderStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersInProgress().remove(Integer.valueOf(orderid));
                logger.log(Level.FINE, "307,OrderProgressQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid + delimiter + Parameters.symbol.get(parentid).getDisplayname()});
            }

            if (c.getOrdersMissed().contains(orderid) && ob.getOrderStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                c.getOrdersMissed().remove(Integer.valueOf(orderid));
                logger.log(Level.FINE, "307,OrderMissedQueueRemoved,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + orderid});
            }
            //For exits we send incremental fill = abs(fill) as tradeupdate ** for exits only ** aggregates fills.
            //For entry, there each fill with the same internal order id is updated.
            updateTrades(c, ob, p, Math.abs(fill), avgFillPrice);
            //if this was a requested cancellation, fire any event if needed
            fireLinkedActions(c, ob);
        }
        return true;
    }

    private boolean updateTrades(BeanConnection c, OrderBean ob, BeanPosition p, int filled, double avgFillPrice) {
        try {
            boolean exitCompleted = false;
            int parentid = ob.getParentSymbolID() - 1;
            int childid = ob.getChildSymbolID() - 1;
            int orderid = ob.getExternalOrderID();
            int parentInternalOrderIDEntry;
            Index ind = new Index(orderReference, parentid);
            String account = c.getAccountName();
            boolean entry = ob.getOrderSide() == EnumOrderSide.BUY || ob.getOrderSide() == EnumOrderSide.SHORT ? true : false;
            //update trades if order is completely filled. For either combo or regular orders, this will be reflected
            if (!entry) {
                parentInternalOrderIDEntry = this.ParentInternalOrderIDForSquareOff(c,ob);
                //childInternalOrderIDsEntry = getFirstInternalOpenOrder(parentid, ob.getOrderSide(), c.getAccountName(), false);
            } else {
                //parentInternalOrderIDsEntry.add(getEntryParentOrderIDInt(ob, account));
                //childInternalOrderIDsEntry.add(ob.getOrderIDForSquareOff());
            }

            //String key =s.getStrategy() + ":" + String.valueOf(childInternalOrderIDEntry) + ":" + account;
            if (p.getChildPosition().isEmpty()) {//single leg order
                     String key = getS().getStrategy() + ":" + String.valueOf(ob.getInternalOrderID()) + ":" + account;
                      if (entry) {
                       new Trade(db, childid, parentid, ob.getOrderReason(), ob.getOrderSide(), avgFillPrice, filled, ob.getInternalOrderID(), orderid, ob.getParentInternalOrderID(), timeZone, c.getAccountName(), getS().getStrategy(), "opentrades", ob.getOrderLog());
                        logger.log(Level.INFO, "207,TradeUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Trade.getParentSymbol(db, key) + delimiter + Trade.getEntrySide(db, key) + delimiter + avgFillPrice + delimiter + filled + delimiter + Trade.getEntryOrderIDInternal(db, key) + delimiter + Trade.getEntryOrderIDExternal(db, key) + delimiter + ob.getExternalOrderID() + delimiter + ob.getInternalOrderID() + delimiter + ob.getExternalOrderID() + delimiter + ob.getInternalOrderID() + delimiter + Trade.getEntrySize(db, key)});
                    } else {
                        if (Trade.getEntrySize(db, key) > 0 && filled > 0) {
                            int entrySize = Trade.getEntrySize(db, key);
                            int exitSize = Trade.getExitSize(db, key);
                            double exitPrice = Trade.getExitPrice(db, key);
                            int adjTradeSize = exitSize + filled > entrySize ? (entrySize - exitSize) : filled;
                            int newexitSize = adjTradeSize + exitSize;
                            filled = filled - adjTradeSize;
                            double newexitPrice = (exitPrice * exitSize + adjTradeSize * avgFillPrice) / (newexitSize);
                            Trade.updateExit(db, parentid, ob.getOrderReason(), ob.getOrderSide(), ob.getCurrentFillPrice(), ob.getCurrentFillSize(), ob.getParentInternalOrderID(), orderid, ob.getParentInternalOrderID(), timeZone, c.getAccountName(), getS().getStrategy(), "opentrades", ob.getOrderLog());
                            if (newexitSize == entrySize) {
                                Trade.closeTrade(db, key);
                                exitCompleted = true;
                            }
                            logger.log(Level.INFO, "207,TradeUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Trade.getParentSymbol(db, key) + delimiter + Trade.getEntrySide(db, key) + delimiter + avgFillPrice + delimiter + filled + delimiter + Trade.getEntryOrderIDInternal(db, key) + delimiter + Trade.getEntryOrderIDExternal(db, key) + delimiter + ob.getExternalOrderID() + delimiter + ob.getInternalOrderID() + delimiter + key + delimiter + adjTradeSize});
                        } else {
                            logger.log(Level.INFO, "207,NoTradeUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Trade.getEntrySize(db, key) + delimiter + ob.getInternalOrderID() + delimiter + ob.getParentInternalOrderID() + delimiter + orderid + delimiter + key});
                        }
                    }
                
                return true;
            } else {//combo order
                OrderBean obp=TradingUtil.getSyntheticOrder(db, c, ob);
                ArrayList in = comboFillSize(c, obp);
                String key;
                //update parent
                    if (entry) {
                        //create/update child order
                        key = getS().getStrategy() + ":" + ob.getInternalOrderID() + ":" + account;
                        new Trade(db, ob.getChildSymbolID(), ob.getParentSymbolID(), ob.getOrderReason(), ob.getOrderSide(), ob.getCurrentFillPrice(), ob.getCurrentFillSize(), ob.getInternalOrderID(), 0, ob.getParentInternalOrderID(), timeZone, c.getAccountName(), getS().getStrategy(), "opentrades", ob.getOrderLog());
                        logger.log(Level.INFO, "311,ChildTradeParentUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Trade.getParentSymbol(db, key) + delimiter + Trade.getEntrySide(db, key) + delimiter + avgFillPrice + delimiter + filled + delimiter + Trade.getEntryOrderIDInternal(db, key) + delimiter + Trade.getEntryOrderIDExternal(db, key) + delimiter + ob.getExternalOrderID() + delimiter + ob.getInternalOrderID()});
                        //create/update parent order
                        key = getS().getStrategy() + ":" + obp.getInternalOrderID() + ":" + account;
                        new Trade(db, obp.getChildSymbolID(), obp.getParentSymbolID(), obp.getOrderReason(), obp.getOrderSide(), (double) in.get(1), Math.abs((int) in.get(0)), obp.getInternalOrderID(), 0, obp.getParentInternalOrderID(), timeZone, c.getAccountName(), getS().getStrategy(), "opentrades", obp.getOrderLog());
                        logger.log(Level.INFO, "311,ParentTradeParentUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Trade.getParentSymbol(db, key) + delimiter + Trade.getEntrySide(db, key) + delimiter + avgFillPrice + delimiter + filled + delimiter + Trade.getEntryOrderIDInternal(db, key) + delimiter + Trade.getEntryOrderIDExternal(db, key) + delimiter + obp.getExternalOrderID() + delimiter + obp.getInternalOrderID()});
                    } else {
                        key = getS().getStrategy() + ":" + ob.getInternalOrderID() + ":" + account;
                        if (Trade.getEntrySize(db, key) > 0) {
                            int exitSize = Trade.getExitSize(db, key);
                            double exitPrice = Trade.getExitPrice(db, key);
                            exitSize = exitSize ;
                            exitPrice = (exitPrice * exitSize + Math.abs((int) in.get(0)) * (double) in.get(1)) / (exitSize);
                            exitSize = exitSize + Math.abs((int) in.get(0));
                            exitPrice = (exitPrice * exitSize + Math.abs((int) in.get(0)) * (double) in.get(1)) / (exitSize);
                            Trade.updateExit(db, ob.getChildSymbolID(), ob.getOrderReason(), ob.getOrderSide(), ob.getCurrentFillPrice(), ob.getCurrentFillSize(), ob.getInternalOrderID(), 0, ob.getParentInternalOrderID(), timeZone, c.getAccountName(), getS().getStrategy(), "opentrades", ob.getOrderLog());
                            //update parent trade
                            Trade.updateExit(db, obp.getChildSymbolID(), obp.getOrderReason(), obp.getOrderSide(), obp.getCurrentFillPrice(), obp.getCurrentFillSize(), obp.getInternalOrderID(), 0, obp.getParentInternalOrderID(), timeZone, c.getAccountName(), getS().getStrategy(), "opentrades", obp.getOrderLog());
                            
                            if (c.getPositions().get(ind).getPosition() == 0) {
                                key = getS().getStrategy() + ":" + ob.getInternalOrderID() + ":" + account;
                                Trade.closeTrade(db, key);
                                key = getS().getStrategy() + ":" + obp.getInternalOrderID() + ":" + account;
                                Trade.closeTrade(db, key);
                                exitCompleted = true;
                            }
                            logger.log(Level.INFO, "311,TradeParentUpdate,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + Trade.getParentSymbol(db, key) + delimiter + Trade.getEntrySide(db, key) + delimiter + avgFillPrice + delimiter + filled + delimiter + Trade.getEntryOrderIDInternal(db, key) + delimiter + Trade.getEntryOrderIDExternal(db, key) + delimiter + ob.getExternalOrderID() + delimiter + ob.getInternalOrderID()});
                        } else {
                            logger.log(Level.INFO, "103,ExitUpdateError,{0}", new Object[]{c.getAccountName() + delimiter + orderReference + delimiter + "NullTradeObject" + delimiter + orderid + delimiter + key});

                        }
                    }
                    //update child
                    //get child id
                
                return exitCompleted;
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
            return false;
        }
    }

    private int getEntryParentOrderIDInt(OrderBean ob, String account) {
        if (ob.getOrderSide().equals(EnumOrderSide.BUY) || ob.getOrderSide().equals(EnumOrderSide.SHORT)) {
            //entry order
            return ob.getParentInternalOrderID();
        } else {
            //exit order
            int entryOrderIDInt = ob.getOrderIDForSquareOff(); //this is the entry id of a child order
            return Trade.getParentEntryOrderIDInternal(db, getS().getStrategy() + ":" + entryOrderIDInt + ":" + account);
        }
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
     * @param parentInternalOrderID internal orderid generated by the strategy
     * or the framework
     * @param parentid id of the parent symbol
     * @return
     *
     */
    private ArrayList comboFillSize(BeanConnection c, OrderBean obp) {
        //logger.log(Level.FINE, "ComboFillSize, Parameters in,BeanConnection:{0},parentInternalOrderID:{1},parentid:{2}", new Object[]{c.getAccountName(), parentInternalOrderID, parentid});
        ArrayList out = new ArrayList();
        int parentid=obp.getParentSymbolID();
        HashMap<Integer, Integer> fill = new HashMap<>();//<symbolid,fillsize>
        HashMap<Integer, Double> fillprice = new HashMap<>();
        int orderSize = 0;
        ArrayList<OrderBean>obs=TradingUtil.getLinkedOrderBeansGivenParentBean(obp,c);
        
        for (OrderBean ob : obs) {
            int ordersize=0;
           if (orderSize == 0) { //run this condition just once
                orderSize = obp.getOrderSide().equals(EnumOrderSide.BUY) || obp.getOrderSide().equals(EnumOrderSide.COVER) ? ob.getCurrentOrderSize() : -ob.getCurrentOrderSize();
            }
            int id = ob.getChildSymbolID() - 1;
            int last = 0;
            int current = 0;
            double lastfillprice = 0D;
            double currentfillprice = 0D;
            last = fill.get(id) == null ? 0 : fill.get(id);
            current = (ob.getOrderSide().equals(EnumOrderSide.BUY) || ob.getOrderSide().equals(EnumOrderSide.COVER)) ? ob.getCurrentFillSize() : -ob.getCurrentFillSize();
            fill.put(id, last + current);
            lastfillprice = fillprice.get(id) == null ? 0D : fillprice.get(id);
            currentfillprice = fill.get(id) != 0 ? (ob.getCurrentFillPrice()* current + lastfillprice * last) / fill.get(id) : 0D;
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
    private HashMap<Integer, Integer> comboStubSize(BeanConnection c, OrderBean ob) {
        HashMap<Integer, Integer> out = new HashMap();
        HashMap<Integer, Integer> comboSpecs = new HashMap();
        int baselineSize = ob.getCurrentOrderSize();
        HashMap<Integer, Integer> comboChildFillSize = new HashMap();
        for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(ob.getParentSymbolID()).getCombo().entrySet()) {
            comboSpecs.put(entry.getKey().getSerialno() - 1, entry.getValue());
            comboChildFillSize.put(entry.getKey().getSerialno() - 1, entry.getValue() * baselineSize);
        }
        synchronized (c.lockOrderMapping) {
            ArrayList<OrderBean>obs=TradingUtil.getLinkedOrderBeansGivenParentBean(ob, c);
            for (OrderBean obi : obs) {
                int childid = ob.getChildSymbolID();
                int childFillSize = (ob.getOrderSide().equals(EnumOrderSide.BUY) || ob.getOrderSide().equals(EnumOrderSide.COVER)) ? ob.getCurrentFillSize() : -ob.getCurrentFillSize();
                int stub = childFillSize - comboChildFillSize.get(childid);
                int earlierValue = out.get(childid) == null ? 0 : out.get(childid);
                out.put(childid, earlierValue + stub);
            }
            return out;
        }
    }

    public int ParentInternalOrderIDForSquareOff(BeanConnection c,OrderBean ob) {
        if(ob.getOrderIDForSquareOff()>0){
            return ob.getOrderIDForSquareOff();
        }else{
            HashSet<Integer> out = new HashSet<>();
        String symbol=ob.getParentDisplayName();
        if(TradingUtil.isSyntheticSymbol(ob.getParentSymbolID())){
            symbol=ob.getParentDisplayName();
        }
        EnumOrderSide entrySide = ob.getOrderSide() == EnumOrderSide.SELL ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
        for (String key : getDb().getKeys("opentrades")) {
            if (key.contains("_" + getS().getStrategy())) {
                if (Trade.getAccountName(db, key).equals(c.getAccountName()) && Trade.getParentSymbol(db, key).equals(symbol) && Trade.getEntrySide(db, key).equals(entrySide) && Trade.getEntrySize(db, key) > Trade.getExitSize(db, key) && Trade.getParentSymbol(db, key).equals(Trade.getEntrySymbol(db, key))) {
                        out.add(Trade.getEntryOrderIDInternal(db, key));
                }
            }
        }        
        for(int o:out){
            return o;
        }
        return -1;
        }
        
    }

    private void updateAcknowledgement(BeanConnection c, OrderBean ob) {
        int parentid=ob.getParentSymbolID();
        int orderid=ob.getExternalOrderID();
        ob.setOrderStatus(EnumOrderStatus.ACKNOWLEDGED);
        ArrayList<OrderBean> obs = TradingUtil.getLinkedOrderBeansGivenParentBean(ob, c);
        boolean parentStatus = true;
        for (OrderBean obi : obs) {
                if (obi.getOrderStatus()== EnumOrderStatus.ACKNOWLEDGED || obi.getOrderStatus() == EnumOrderStatus.COMPLETEFILLED || obi.getOrderStatus() == EnumOrderStatus.PARTIALFILLED) {
                    parentStatus = parentStatus && true;
                } else {
                    parentStatus = parentStatus && false;
                }
            
        }
        if (parentStatus && TradingUtil.isSyntheticSymbol(parentid)) {
            OrderBean obp=TradingUtil.getSyntheticOrder(db, c, ob);
            obp.setOrderStatus(EnumOrderStatus.ACKNOWLEDGED);
        }
        //logger.log(Level.INFO, "{0},{1},Execution Manager,Order State, OrderID: {2}, Parent Order Status:{3}, Child Order Status:{4}", new Object[]{c.getAccountName(), orderReference, orderID, ob.getParentStatus(), ob.getChildStatus()});
    }

    //<editor-fold defaultstate="collapsed" desc="getter-setters">
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
    public List<ArrayList<LinkedAction>> getFillRequestsForTracking() {
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

    /**
     * @return the db
     */
    public Database<String, String> getDb() {
        return db;
    }

    /**
     * @return the s
     */
    public Strategy getS() {
        return s;
    }
}
