/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.framework.fundamental.FundamentalDataListener;
import com.ib.client.*;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.incurrency.framework.rateserver.Rates;
import java.io.PrintStream;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;
import com.ib.client.TickType;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;
import redis.clients.jedis.Jedis;

/**
 *
 * @author admin
 */
public class TWSConnection extends Thread implements EWrapper {

    protected final static int MAX_WAIT_COUNT = 10;
    protected final static int WAIT_TIME = 1;//seconds
    public EClientSocket eClientSocket = new EClientSocket(this);
    private BeanConnection c;
    private int mRequestId;
    public static volatile int mTotalSymbols;
    public static volatile int mTotalATMChecks;
    private ArrayList _fundamentallisteners = new ArrayList();
    private Drop accountIDSync = new Drop();
    private static final Logger logger = Logger.getLogger(TWSConnection.class.getName());
    private boolean initialsnapShotFilled = false; //set to true by getMktData() once the first 100 snapshot requests are out to IB
    private TradingEventSupport tes = new TradingEventSupport();
    private LimitedQueue recentOrders;
    private boolean stopTrading = false;
    AtomicBoolean severeEmailSent = new AtomicBoolean(Boolean.FALSE);
    public ConcurrentHashMap<Integer, Request> requestDetails = new ConcurrentHashMap<>();
    public ConcurrentHashMap<Integer, Request> requestDetailsWithSymbolKey = new ConcurrentHashMap<>();
    public int outstandingSnapshots = 0;
    private final String delimiter = "_";
    static final Object lock_request = new Object();
    private boolean historicalDataFarmConnected = true;
    public static boolean skipsymbol = false;
    //Parameters for dataserver
    public String cassandraIP;
    public int cassandraPort = 4242;
    public Socket cassandraConnection;
    public PrintStream output;
    //public boolean useRTVolume = false;
    public String topic=Algorithm.topic;
    Jedis jedis = Algorithm.marketdatapool.getResource();
    public boolean saveToCassandra;
    public boolean realtime = false;
    public String tickEquityMetric;
    public String tickFutureMetric;
    public String tickOptionMetric;
    public String rtEquityMetric;
    public String rtFutureMetric;
    public String rtOptionMetric;
    public static String[][] marketData;
    public static AtomicBoolean serverInitialized = new AtomicBoolean();
    public RequestIDManager requestIDManager = new RequestIDManager();
    private SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private SynchronousQueue<String> startingOrderID = new SynchronousQueue<>();
    private java.util.Random random=new java.util.Random();
    
    public TWSConnection(BeanConnection c) {
        this.c = c;
        mTotalSymbols = Parameters.symbol.size();
        if (mTotalATMChecks == 0) {
            for (BeanSymbol s : Parameters.symbol) {
                if (s.getType().compareTo("OPT") == 0 && s.getOption() == null) {
                    mTotalATMChecks = mTotalATMChecks + 1;
                }
            }
        }
    }

    public synchronized boolean connectToTWS() {
        try {
            String twsHost = getC().getIp();
            int twsPort = getC().getPort();
            int clientID = getC().getClientID();
            if (!eClientSocket.isConnected()) {
                eClientSocket.eConnect(twsHost, twsPort, clientID);
                if (eClientSocket.isConnected()) {
                    if (this.severeEmailSent.get()) {
                        Thread t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + " reconnected. Trading Resumed on this account", "Algorithm Information ALERT"));
                        t.start();
                        //Resubscribe streaming connections
                        int connectionid = Parameters.connection.indexOf(this.getC());
                        for (BeanSymbol s : Parameters.symbol) {
                            if (s.getConnectionidUsedForMarketData() == connectionid) {
                                this.getMktData(s, false);
                            }
                        }
                    }
                    this.severeEmailSent.set(Boolean.FALSE);
                    String orderid = startingOrderID.poll(2, TimeUnit.SECONDS);
                    //  String orderid="1";
                    getC().getIdmanager().initializeOrderId(Utilities.getInt(orderid, this.requestIDManager.getNextOrderId()));
                    logger.log(Level.INFO, "402, NextOrderIDReceived,{0}:{1}:{2}:{3}:{4},OrderID={5}",
                            new Object[]{"Unknown", getC().getAccountName(), "Unknown", -1, -1, orderid});
                    eClientSocket.reqIds(1);
                    logger.log(Level.INFO, "403,Connected,{0}:{1}:{2}:{3}:{4}", new Object[]{"Unknown", c.getAccountName(), c.getStrategy(), -1, -1});
                    eClientSocket.setServerLogLevel(2);
                    return true;
                } else {
                    logger.log(Level.SEVERE, "402, Could Not Connect,{0}:{1}:{2}:{3}:{4}",
                            new Object[]{"Unknown", c.getAccountName(), "Unknown", -1, -1});
                    return false;
                }
            }

            return false;
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
            return false;
        }
    }

    public void getAccountUpdates() {
        eClientSocket.reqAccountUpdates(true, null);
    }

    public void cancelAccountUpdates() {
        eClientSocket.reqAccountUpdates(false, null);
    }

    public void getContractDetails() {
        Contract con = new Contract();
        for (BeanSymbol s : Parameters.symbol) {
            con.m_symbol = s.getBrokerSymbol();
            con.m_currency = s.getCurrency();
            con.m_exchange = s.getExchange();
            con.m_expiry = s.getExpiry();
            con.m_primaryExch = s.getPrimaryexchange();
            con.m_right = s.getRight();
            con.m_secType = s.getType();
            con.m_strike = s.getOption() == null ? 0 : Double.parseDouble(s.getOption());
            if (getC().getReqHandle().requestContractDetailsHandle()) {
                getC().getReqHandle().setContractDetailsReturned(false);
                if (getC().getReqHandle().getHandle()) {
                    mRequestId = requestIDManager.getNextRequestId();
                    //c.getmReqID().put(mRequestId, s.getSerialno());
                    requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.CONTRACTDETAILS, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                    eClientSocket.reqContractDetails(mRequestId, con);
                } else {
                    System.out.println("### Error getting handle while requesting market data for contract Name: " + s.getBrokerSymbol());
                }
            }
        }
    }

    public void getContractDetails(BeanSymbol s, String overrideType) {
        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_strike = s.getOption() == null ? 0 : Double.parseDouble(s.getOption());
        if ("".compareTo(overrideType) != 0) {
            con.m_secType = overrideType;
        } else {
            con.m_secType = s.getType();
        }
        if (getC().getReqHandle().getHandle()) {
            mRequestId = requestIDManager.getNextRequestId();
            //c.getmReqID().put(mRequestId, s.getSerialno());
            requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.CONTRACTDETAILS, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));

            eClientSocket.reqContractDetails(mRequestId, con);

        } else {
            System.out.println("101,ErrorOnHandle,{0}" + s.getDisplayname());
        }
    }

    public void requestSingleSnapshot(BeanSymbol s) {
        boolean proceed = true;
        for (Map.Entry<Integer, Request> entry : requestDetails.entrySet()) {
            if (s.getSerialno() == entry.getValue().symbol.getSerialno() && entry.getValue().requestType.equals(EnumRequestType.SNAPSHOT)) {
                proceed = false;
                //logger.log(Level.FINER, "101,ErrorSnapshotRequestExists", new Object[]{s.getDisplayname()+delimiter+entry.getKey()});
            }
        }
        if (proceed && getC().getReqHandle().getHandle()) {
            mRequestId = requestIDManager.getNextRequestId();
            requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.SNAPSHOT, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
            logger.log(Level.FINER, "MarketDataRequestSent_Snapshot,{0}", new Object[]{s.getSerialno() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + this.getC().getAccountName()});


            //c.getmSnapShotReqID().put(mRequestId, s.getSerialno());
            Contract con;
            con = createContract(s);
            this.eClientSocket.reqMktData(mRequestId, con, null, true, null);
            logger.log(Level.FINER, "403,OneTimeSnapshotSent, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
        }
    }

    public synchronized void getMktData(BeanSymbol s, Contract contract, boolean isSnap) {
        //for streaming request
        if (!isSnap) { //streaming data request
            if (getC().getReqHandle().getHandle()) {
                mRequestId = requestIDManager.getNextRequestId();
                // Store the request ID for each symbol for later use while updating the symbol table
                s.setReqID(mRequestId);
                //make snapshot/ streaming data request
                requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.STREAMING, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                logger.log(Level.FINER, "401,MarketDataRequestSentStreaming,{0}:{1}:{2}:{3}:{4},RequestID={5}",
                        new Object[]{"Unknown", c.getAccountName(), s.getDisplayname(), -1, -1, mRequestId});


                //c.getmReqID().put(mRequestId, s.getSerialno());
                //requestDetails.putIfAbsentIfAbsent(mRequestId, new Request(mRequestId, s, EnumRequestType.STREAMING, EnumRequestStatus.PENDING, new Date().getTime()));

                //c.getmStreamingSymbolRequestID().put(s.getSerialno(), mRequestId);
                List<TagValue> l = new ArrayList<>();
                //TagValue tv=new TagValue();
                //tv.m_value="XYZ";
                //l.add(tv);
                requestDetailsWithSymbolKey.putIfAbsent(s.getSerialno(), new Request(EnumSource.IB, mRequestId, s, EnumRequestType.SNAPSHOT, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                this.eClientSocket.reqMktData(mRequestId, contract, null, isSnap, null);
                s.setConnectionidUsedForMarketData(Parameters.connection.indexOf(getC()));
                logger.log(Level.FINER, "403,MarketDataRequestSent, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
            } else {
                System.out.println("101,ErrorOnHandle,{0}" + s.getDisplayname());
            }
        } else if (isSnap) {
            boolean requestData = true;
            if (getC().getReqHandle().getHandle()) {
                if (requestDetailsWithSymbolKey.containsKey(s.getSerialno())) {//if the symbol is already being serviced, 
                    if (new Date().getTime() > requestDetailsWithSymbolKey.get(s.getSerialno()).requestTime + 10000) { //and request is over 10 seconds seconds old
                        int origReqID = requestDetailsWithSymbolKey.get(s.getSerialno()).requestID;
                        requestDetailsWithSymbolKey.get(s.getSerialno()).requestStatus = EnumRequestStatus.CANCELLED;
                        if (requestDetailsWithSymbolKey.get(origReqID) != null) {
                            requestDetails.get(origReqID).requestStatus = EnumRequestStatus.CANCELLED;
                            getC().getWrapper().eClientSocket.cancelMktData(origReqID);
                            logger.log(Level.FINEST, "403,SnapshotCancelled, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + origReqID});
                        }
                        requestDetailsWithSymbolKey.remove(s.getSerialno());

                        //we dont reattempt just yet to prevent a loop of attempts when IB is not throwing data for the symbol
                    } else {
                        requestData = false;
                    }
                }
                if (requestData) {//we request data only if there is no outstanding request
                    mRequestId = requestIDManager.getNextRequestId();
                    // Store the request ID for each symbol for later use while updating the symbol table
                    s.setReqID(mRequestId);
                    requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.SNAPSHOT, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                    logger.log(Level.FINER, "401,MarketDataRequestSentSnapShot,{0}:{1}:{2}:{3}:{4},RequestID={5}",
                            new Object[]{"Unknown", c.getAccountName(), s.getDisplayname(), -1, -1, mRequestId});

                    requestDetailsWithSymbolKey.putIfAbsent(s.getSerialno(), new Request(EnumSource.IB, mRequestId, s, EnumRequestType.SNAPSHOT, EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                    eClientSocket.reqMktData(mRequestId, contract, null, isSnap, null);
                    s.setConnectionidUsedForMarketData(-1);
                    logger.log(Level.FINER, "403,ContinuousSnapshotSent, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
                }
            } else {
                System.out.println("### Error getting handle while requesting snapshot data for contract " + contract.m_conId + " Name: " + s.getBrokerSymbol());
            }
        }
    }

    public void getMktData(BeanSymbol s, boolean isSnap) {
        Contract con;
        con = createContract(s);
        getMktData(s, con, isSnap);
    }

    public void getRealTimeBars(BeanSymbol s) {
        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (getC().getReqHistoricalHandle().getHandle()) {
            if (getC().getReqHandle().getHandle()) {
                mRequestId = requestIDManager.getNextRequestId();
                requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.REALTIMEBAR, EnumBarSize.FIVESECOND, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                logger.log(Level.FINER, "MarketDataRequestSent_Realtime,{0}", new Object[]{mRequestId + delimiter + s.getDisplayname()});
                eClientSocket.reqRealTimeBars(mRequestId, con, 5, "TRADES", true, null); //only returns regular trading hours
                logger.log(Level.FINER, "403,RealTimeBarsRequestSent, {0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});

            } else {
                System.out.println("### Error getting handle while requesting market data for contract " + con.m_symbol + " Name: " + s.getBrokerSymbol());
            }
        }
    }

    public HashMap<Integer, Double> getTickValues(int parentid) {

        HashMap<Integer, Double> tickValue = new HashMap<>();
        for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(parentid).getCombo().entrySet()) {
            int childid = entry.getKey().getSerialno() - 1;
            tickValue.put(childid, Math.abs(Parameters.symbol.get(childid).getTickSize() * entry.getValue()));
        }
        return tickValue;
    }

    public HashMap<Integer, Order> createOrder(OrderEvent e) {
        HashMap<Integer, Order> out;
        int id = e.getSymbolBean().getSerialno() - 1;
        int internalOrderID = e.getInternalorder();
        int size = e.getOrderSize();
        EnumOrderSide ordSide = e.getSide();
        EnumOrderReason notify = e.getReason();
        EnumOrderType orderType = e.getOrderType();
        EnumOrderStage stage = e.getOrderStage();
        double limit = e.getLimitPrice();
        double trigger = e.getTriggerPrice();
        String ordValidity = e.getValidity();
        String orderRef = e.getOrdReference();
        String validAfter = e.getEffectiveFrom();
        String link = e.getTag();
        boolean transmit = e.isTransmit();
        String ocaGroup = e.getOrderGroup();
        String effectiveFrom = e.getEffectiveFrom();
        HashMap<Integer, Integer> stubs = e.getStubs();
        out = createOrder(id, internalOrderID, size, ordSide, notify, orderType, stage, limit, trigger, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom, stubs, e.getOrderAttributes());
        return out;
    }

    public HashMap<Integer, Order> createOrder(int id, int internalOrderID, int size, EnumOrderSide ordSide, EnumOrderReason notify, EnumOrderType orderType, EnumOrderStage stage, double limit, double trigger, String ordValidity, String orderRef, String validAfter, String link, boolean transmit, String ocaGroup, String effectiveFrom, HashMap<Integer, Integer> stubs, HashMap<String, Object> orderAttributes) {
        if (recentOrders == null) {
            recentOrders = new LimitedQueue(getC().getOrdersHaltTrading());
        }
        HashMap<Integer, Order> orders = new HashMap<>();
        if (!tradeIntegrityOK(ordSide, stage, orders, true)) {
            return orders;
        }
        if (!Parameters.symbol.get(id).getType().equals("COMBO")) {
            Order order = createChildOrder(size, ordSide, notify, orderType, limit, trigger, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom, orderAttributes);
            orders.put(id, order);
            return orders;
        } else if (Parameters.symbol.get(id).getType().equals("COMBO") && stubs == null) {//regular combo order
            HashMap<Integer, Double> tickValue = getTickValues(id);
            HashMap<Integer, Double> limitPrices = initializeLimitPricesUsingAggression(id, limit, ordSide, tickValue);
            int i = 0;

            for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(id).getCombo().entrySet()) {
                int subSize = entry.getValue();
                EnumOrderSide subSide = EnumOrderSide.UNDEFINED;
                switch (ordSide) {
                    case BUY:
                        if (subSize > 0) {
                            subSide = EnumOrderSide.BUY;
                        } else {
                            subSide = EnumOrderSide.SHORT;
                        }
                        break;
                    case SELL:
                        if (subSize > 0) {
                            subSide = EnumOrderSide.SELL;
                        } else {
                            subSide = EnumOrderSide.COVER;
                        }
                        break;
                    case SHORT:
                        if (subSize > 0) {
                            subSide = EnumOrderSide.SHORT;
                        } else {
                            subSide = EnumOrderSide.BUY;
                        }
                        break;
                    case COVER:
                        if (subSize > 0) {
                            subSide = EnumOrderSide.COVER;
                        } else {
                            subSide = EnumOrderSide.SELL;
                        }
                    default:
                        break;
                }
                Order order = createChildOrder(Math.abs(entry.getValue()) * size, subSide, notify, orderType, limitPrices.get(entry.getKey().getSerialno() - 1), trigger, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom, orderAttributes);
                orders.put(entry.getKey().getSerialno() - 1, order);
                i = i + 1;
            }
        } else if (stubs != null) {//stub order
            EnumOrderSide subSide = EnumOrderSide.UNDEFINED;
            int childid;
            int childsize;
            for (Map.Entry<Integer, Integer> entry : stubs.entrySet()) {
                childid = entry.getKey();
                childsize = entry.getValue();

                if (childsize > 0 && link.equals("STUBREDUCE")) { //entry being reversed
                    subSide = EnumOrderSide.SHORT;
                } else if (childsize < 0 && link.equals("STUBREDUCE")) {
                    subSide = EnumOrderSide.BUY;
                } else if (childsize > 0 && link.equals("STUBCOMPLETE")) {
                    subSide = EnumOrderSide.COVER;
                } else if (childsize < 0 && link.equals("STUBCOMPLETE")) {
                    subSide = EnumOrderSide.SELL;
                }
                if (childsize != 0) {
                    Order order = createChildOrder(Math.abs(childsize), subSide, notify, orderType, 0D, 0D, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom, orderAttributes);
                    orders.put(childid, order);
                }
            }
        }
        return orders;
    }

    private Order createChildOrder(int size, EnumOrderSide ordSide, EnumOrderReason notify, EnumOrderType orderType, double limit, double trigger, String ordValidity, String orderRef, String validAfter, String link, boolean transmit, String ocaGroup, String effectiveFrom, HashMap<String, Object> orderAttributes) {
        Order order = new Order();
        order.m_action = (ordSide == EnumOrderSide.BUY || ordSide == EnumOrderSide.COVER || ordSide == EnumOrderSide.TRAILBUY) ? "BUY" : "SELL";
        order.m_auxPrice = trigger > 0 ? trigger : 0;
        order.m_lmtPrice = limit > 0 ? limit : 0;
        order.m_tif = ordValidity;
        order.m_goodAfterTime = effectiveFrom;

        order.m_displaySize = Utilities.getInt(orderAttributes.get("displaysize"), 0);

        switch (orderType) {
            case MKT:
                order.m_orderType = "MKT";
                break;
            case LMT:
            case CUSTOMREL:
                if (limit > 0) {
                    order.m_orderType = "LMT";
                    order.m_lmtPrice = limit;
                } else {
                    order.m_orderType = "MKT";
                }
                break;
            case STPLMT:
                order.m_orderType = "STP LMT";
                order.m_lmtPrice = limit;
                order.m_auxPrice = trigger;
                break;
            case STP:
                order.m_orderType = "STP LMT";
                order.m_auxPrice = trigger;
                break;
            case REL:
                order.m_orderType = "REL";
                order.m_lmtPrice = limit;
                order.m_auxPrice = trigger;
                break;
            default:
                break;
        }

        order.m_orderRef = orderRef;
        order.m_totalQuantity = size;
        order.m_account = getC().getAccountName().toUpperCase();
        if ("".compareTo(link) != 0) {
            order.m_ocaGroup = ocaGroup;
            order.m_ocaType = 2;
        }
        order.m_transmit = transmit;
        //order.m_tif = validity; //All orders go as DAY orders after expireminutes logic was removed
        if (validAfter != null) {
            order.m_goodAfterTime = validAfter;
        }
        switch (notify) {
            case OCOSL:
            case OCOTP:
                order.m_ocaGroup = ocaGroup;
                order.m_ocaType = 2;
                break;
            default:
                break;
        }
        logger.log(Level.FINE, "307,OrderDetails,{0}", new Object[]{getC().getAccountName() + delimiter + order.m_orderRef + delimiter + order.m_action + delimiter + order.m_totalQuantity + delimiter + order.m_orderType + delimiter + order.m_lmtPrice + delimiter + order.m_auxPrice + delimiter + order.m_tif + delimiter + order.m_goodTillDate});
        return order;

    }

    private double getAggression(int id) {
        double aggr = 0;
        int uptick = 0;
        int downtick = 0;
        LimitedQueue<Double> e = Parameters.symbol.get(id).getTradedPrices();
        if (e.size() > 1) {
            for (int i = 1; i < e.size(); i++) {
                if (e.get(i) > e.get(i - 1)) {
                    uptick++;
                } else if (e.get(i) < e.get(i - 1)) {
                    downtick++;
                }
            }
        }
        if (uptick + downtick > 0) {
            aggr = (double) uptick / (uptick + downtick);
        }
        return aggr;
    }

    public double getLimitPriceUsingAggression(int id, double originalLimitPrice, EnumOrderSide side) {//for a single id
        double aggr = getAggression(id);
        double bidprice;
        double askprice;
        double tickSize;
        bidprice = Parameters.symbol.get(id).getBidPrice();
        askprice = Parameters.symbol.get(id).getAskPrice();
        tickSize = Parameters.symbol.get(id).getTickSize();
        double newlimitprice = 0;
        switch (side) {
            case BUY:
            case COVER:
                if (originalLimitPrice > 0 && bidprice == originalLimitPrice) {
                    newlimitprice = originalLimitPrice;
                } else {
                    newlimitprice = ((int) ((bidprice + ((askprice - bidprice) * aggr)) / tickSize)) * tickSize;
                }
                break;
            case SHORT:
            case SELL:
                if (originalLimitPrice > 0 && askprice == originalLimitPrice) {
                    newlimitprice = originalLimitPrice;
                } else {
                    newlimitprice = ((int) ((askprice - (askprice - bidprice) * (1 - aggr)) / tickSize)) * tickSize;
                }
                break;
        }

        logger.log(Level.FINE, "307,RecalculatedLimitPrice,{0}", new Object[]{aggr + delimiter + bidprice + delimiter + askprice + delimiter + side + delimiter + originalLimitPrice + delimiter + newlimitprice + delimiter + tickSize});
        return newlimitprice;
    }

    public double calculatePairPrice(int pairID, HashMap<Integer, Double> limitPrices) {
        HashMap<BeanSymbol, Integer> combo = Parameters.symbol.get(pairID).getCombo();
        double pairPrice = 0;
        int i = 0;
        for (Map.Entry<BeanSymbol, Integer> entry : combo.entrySet()) {
            pairPrice = pairPrice + limitPrices.get(entry.getKey().getSerialno() - 1) * entry.getValue();
            i = i + 1;
        }
        return pairPrice;
    }

    private HashMap<Integer, Double> initializeLimitPricesUsingAggression(int pairID, double limit, EnumOrderSide orderSide, HashMap<Integer, Double> tickValue) {
        HashMap<Integer, Double> limitPrices = new HashMap<>();
        HashMap<Integer, Integer> buysell = new HashMap<>();
        HashMap<BeanSymbol, Integer> combo = Parameters.symbol.get(pairID).getCombo();
        double tickSize = 0;
        for (Map.Entry<BeanSymbol, Integer> entry : combo.entrySet()) { //ordering of orders and combo should be the same. This appears to be a correct assumption
            limitPrices.put(entry.getKey().getSerialno() - 1, getLimitPriceUsingAggression(entry.getKey().getSerialno() - 1, 0D, orderSide));
            tickSize = entry.getKey().getTickSize();
            if (entry.getValue() > 0) {
                buysell.put(entry.getKey().getSerialno() - 1, 1);
            } else {
                buysell.put(entry.getKey().getSerialno() - 1, -1);
            }
        }
        double pairLimitPrice = calculatePairPrice(pairID, limitPrices);
        if (limit == 0) {
            for (Map.Entry<Integer, Double> limitPrice : limitPrices.entrySet()) {
                limitPrice.setValue(0D);//set limit =0 for market orders
            }
        } else {
            int loops = 0;
            double maxTickValue = 0;
            double minTickValue = Double.MAX_VALUE;
            switch (orderSide) {
                case BUY:
                case COVER:
                    for (Double d : tickValue.values()) {
                        maxTickValue = Math.max(maxTickValue, d);
                        minTickValue = Math.min(minTickValue, d);
                    }
                    while (pairLimitPrice > limit) {
                        loops = loops + 1;
                        for (Map.Entry<Integer, Double> limitPrice : limitPrices.entrySet()) {
                            int symbolid = limitPrice.getKey();
                            if (Math.round((minTickValue * loops) / tickValue.get(symbolid)) > Math.round((minTickValue * (loops - 1)) / tickValue.get(symbolid))) {
                                limitPrices.put(symbolid, limitPrices.get(symbolid) - tickSize * buysell.get(symbolid));
                            }
                        }
                        pairLimitPrice = this.calculatePairPrice(pairID, limitPrices);
                    }
                    break;
                case SHORT:
                case SELL:
                    for (double d : tickValue.values()) {
                        maxTickValue = Math.max(maxTickValue, d);
                        minTickValue = Math.min(minTickValue, d);
                    }
                    while (pairLimitPrice < limit || loops == 0) {
                        loops = loops + 1;
                        for (Map.Entry<Integer, Double> limitPrice : limitPrices.entrySet()) {
                            int symbolid = limitPrice.getKey();
                            if (Math.round((minTickValue * loops) / tickValue.get(symbolid)) > Math.round((minTickValue * (loops - 1)) / tickValue.get(symbolid))) {
                                limitPrices.put(symbolid, limitPrices.get(symbolid) + tickSize * buysell.get(symbolid));
                            }
                        }
                        pairLimitPrice = this.calculatePairPrice(pairID, limitPrices);
                    }
                    break;
                default:
                    break;
            }
        }
        return limitPrices;
    }

    public HashMap<Integer, Double> amendLimitPricesUsingAggression(int parentid, double limit, ArrayList<Integer> orders, EnumOrderSide orderSide) {
        HashMap<Integer, Double> tickValue = getTickValues(parentid);
        HashMap<Integer, Double> out = new HashMap<>();
        HashMap<Integer, Double> limitPrices = new HashMap<>();//Integer = symbol id, double = limit price
        HashMap<Integer, Integer> buysell = new HashMap<>();
        double tickSize = 0;
        ArrayList<Integer> symbolids = new ArrayList<>();

        HashMap<BeanSymbol, Integer> combo = Parameters.symbol.get(parentid).getCombo();
        for (Map.Entry<BeanSymbol, Integer> entry : combo.entrySet()) { //ordering of orders and combo should be the same. This appears to be a correct assumption
            int childsymbolid = entry.getKey().getSerialno() - 1;
            EnumOrderSide childSide = EnumOrderSide.UNDEFINED;
            switch (orderSide) {
                case BUY:
                    childSide = entry.getValue() > 0 ? EnumOrderSide.BUY : EnumOrderSide.SHORT;
                    break;
                case SHORT:
                    childSide = entry.getValue() > 0 ? EnumOrderSide.SHORT : EnumOrderSide.BUY;
                    break;
                case SELL:
                    childSide = entry.getValue() > 0 ? EnumOrderSide.SELL : EnumOrderSide.COVER;
                    break;
                case COVER:
                    childSide = entry.getValue() > 0 ? EnumOrderSide.COVER : EnumOrderSide.SELL;
                    break;
                default:
                    break;

            }
            limitPrices.put(childsymbolid, getLimitPriceUsingAggression(entry.getKey().getSerialno() - 1, 0D, childSide));
            tickSize = entry.getKey().getTickSize();
            symbolids.add(childsymbolid);
            if (entry.getValue() > 0) {
                buysell.put(childsymbolid, 1);
            } else {
                buysell.put(childsymbolid, -1);
            }
        }
        double pairLimitPrice = calculatePairPrice(parentid, limitPrices);
        int loops = 0;
        double maxTickValue = 0;
        double minTickValue = Double.MAX_VALUE;
        switch (orderSide) {
            case BUY:
            case COVER:
                for (double d : tickValue.values()) {
                    maxTickValue = Math.max(maxTickValue, d);
                    minTickValue = Math.min(minTickValue, d);
                }
                while (pairLimitPrice > limit) {
                    loops = loops + 1;
                    for (int i = 0; i < orders.size(); i++) {
                        OrderBean ob = getC().getOrders().get(orders.get(i));
                        int childid = ob.getChildSymbolID() - 1;
                        if (ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                            limitPrices.put(childid, getC().getOrders().get(orders.get(i)).getFillPrice());
                        } else if (Math.round((minTickValue * loops) / tickValue.get(childid)) > Math.round((minTickValue * (loops - 1)) / tickValue.get(childid))) {
                            limitPrices.put(childid, limitPrices.get(childid) - Parameters.symbol.get(childid).getTickSize() * buysell.get(childid));
                        }
                    }
                    pairLimitPrice = this.calculatePairPrice(parentid, limitPrices);
                }
                break;
            case SHORT:
            case SELL:
                for (double d : tickValue.values()) {
                    maxTickValue = Math.max(maxTickValue, d);
                    minTickValue = Math.min(minTickValue, d);
                }
                while (pairLimitPrice < limit) {
                    loops = loops + 1;
                    for (int j = 0; j < orders.size(); j++) {
                        OrderBean ob = getC().getOrders().get(orders.get(j));
                        int childid = ob.getChildSymbolID() - 1;
                        if (ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                            limitPrices.put(childid, getC().getOrders().get(orders.get(j)).getFillPrice());
                        } else if (Math.round((minTickValue * loops) / tickValue.get(childid)) > Math.round((minTickValue * (loops - 1)) / tickValue.get(childid))) {
                            limitPrices.put(childid, limitPrices.get(childid) + tickSize * buysell.get(childid));
                        }
                    }
                    pairLimitPrice = this.calculatePairPrice(parentid, limitPrices);
                }
                break;
            default:
                break;

        }
        int i = 0;
        for (int symbolid : symbolids) {
            out.put(symbolid, limitPrices.get(symbolid));
            i = i + 1;
        }
        return out;
    }

    public HashMap<Integer, Order> createOrderFromExisting(BeanConnection c, int internalorderid, String strategy) {
        HashMap<Integer, Order> out = new HashMap<>();
        ArrayList<Integer> orderids;
        orderids = c.getOrderMapping().get(new Index(strategy, internalorderid));
        for (int orderid : orderids) {
            OrderBean ordExisting = c.getOrders().get(orderid);
            Order ordNew = new Order();
            ordNew.m_action = (ordExisting.getChildOrderSide() == EnumOrderSide.BUY || ordExisting.getChildOrderSide() == EnumOrderSide.COVER || ordExisting.getParentOrderSide() == EnumOrderSide.TRAILBUY) ? "BUY" : "SELL";
            ordNew.m_auxPrice = ordExisting.getTriggerPrice() > 0 ? ordExisting.getTriggerPrice() : 0;
            ordNew.m_lmtPrice = ordExisting.getChildLimitPrice() > 0 ? ordExisting.getChildLimitPrice() : 0;
            ordNew.m_tif = ordExisting.getOrderValidity();
            ordNew.m_account = c.getAccountName();
//        ordNew.m_totalQuantity=ordExisting.getOrderSize();
            switch (ordExisting.getOrderType()) {
                case MKT:
                    ordNew.m_orderType = "MKT";
                    break;
                case LMT:
                    ordNew.m_orderType = "LMT";
                    break;
                case STPLMT:
                    ordNew.m_orderType = "STP LMT";
                    break;
                case STP:
                    ordNew.m_orderType = "STP";
                    break;
                default:
                    break;
            }
            ordNew.m_ocaGroup = ordExisting.getOcaGroup();
            ordNew.m_ocaType = ordExisting.getOcaExecutionLogic();
            ordNew.m_orderRef = ordExisting.getOrderReference();
            ordNew.m_totalQuantity = ordExisting.getChildOrderSize();
            ordNew.m_orderId = ordExisting.getOrderID();
            ordNew.m_displaySize = ordExisting.getDisplaySize();
            //ordNew.m_goodTillDate = ordExisting.getExpireTime();            
            logger.log(Level.FINE, "307,OrderDetails,{0}", new Object[]{c.getAccountName() + delimiter + ordNew.m_orderRef + delimiter + ordNew.m_action + delimiter + ordNew.m_totalQuantity + delimiter + ordNew.m_orderType + delimiter + ordNew.m_lmtPrice + delimiter + ordNew.m_auxPrice + delimiter + ordNew.m_tif + delimiter + ordNew.m_goodTillDate});
            out.put(ordExisting.getChildSymbolID() - 1, ordNew);
        }
        return out;

    }

    public Order createOrderFromExisting(BeanConnection c, int orderid) {

        OrderBean ordExisting = c.getOrders().get(orderid);
        Order ordNew = new Order();
        ordNew.m_action = (ordExisting.getChildOrderSide() == EnumOrderSide.BUY || ordExisting.getChildOrderSide() == EnumOrderSide.COVER || ordExisting.getParentOrderSide() == EnumOrderSide.TRAILBUY) ? "BUY" : "SELL";
        ordNew.m_auxPrice = ordExisting.getTriggerPrice() > 0 ? ordExisting.getTriggerPrice() : 0;
        ordNew.m_lmtPrice = ordExisting.getChildLimitPrice() > 0 ? ordExisting.getChildLimitPrice() : 0;
        ordNew.m_tif = ordExisting.getOrderValidity();
//        ordNew.m_totalQuantity=ordExisting.getOrderSize();
        switch (ordExisting.getOrderType()) {
            case MKT:
                ordNew.m_orderType = "MKT";
                break;
            case LMT:
                ordNew.m_orderType = "LMT";
                break;
            case STPLMT:
                ordNew.m_orderType = "STP LMT";
                break;
            case STP:
                ordNew.m_orderType = "STP";
                break;
            default:
                break;
        }
        ordNew.m_ocaGroup = ordExisting.getOcaGroup();
        ordNew.m_ocaType = ordExisting.getOcaExecutionLogic();
        ordNew.m_orderRef = ordExisting.getOrderReference();
        ordNew.m_totalQuantity = ordExisting.getChildOrderSize();
        ordNew.m_orderId = ordExisting.getOrderID();
        //ordNew.m_goodTillDate = ordExisting.getExpireTime();
        logger.log(Level.FINE, "307,OrderDetails,{0}", new Object[]{c.getAccountName() + delimiter + ordNew.m_orderRef + delimiter + ordNew.m_action + delimiter + ordNew.m_totalQuantity + delimiter + ordNew.m_orderType + delimiter + ordNew.m_lmtPrice + delimiter + ordNew.m_auxPrice + delimiter + ordNew.m_tif + delimiter + ordNew.m_goodTillDate});
        return ordNew;

    }

    public ArrayList<Contract> createContract(int id) {
        ArrayList<Contract> out = new ArrayList<>();
        if (!Parameters.symbol.get(id).getType().equals("COMBO")) {
            Contract contract = new Contract();
            if (Parameters.symbol.get(id).getContractID() > 0) {
                contract.m_conId = Parameters.symbol.get(id).getContractID();
            }
            contract.m_currency = Parameters.symbol.get(id).getCurrency();
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            contract.m_primaryExch = Parameters.symbol.get(id).getPrimaryexchange() == null ? null : Parameters.symbol.get(id).getPrimaryexchange();

            contract.m_symbol = Parameters.symbol.get(id).getBrokerSymbol();
            if (Parameters.symbol.get(id).getBrokerSymbol() != null) {
                contract.m_symbol = Parameters.symbol.get(id).getBrokerSymbol();
            }
            if (Parameters.symbol.get(id).getExchangeSymbol() != null && Parameters.symbol.get(id).getType().equals("STK")) {
                contract.m_localSymbol = Parameters.symbol.get(id).getExchangeSymbol();
            }
            contract.m_expiry = Parameters.symbol.get(id).getExpiry() == null ? null : Parameters.symbol.get(id).getExpiry();
            contract.m_right = Parameters.symbol.get(id).getRight() == null ? null : Parameters.symbol.get(id).getRight();
            contract.m_strike = Utilities.getDouble(Parameters.symbol.get(id).getOption(), 0);
            contract.m_secType = Parameters.symbol.get(id).getType();
            out.add(contract);
        } else {
            for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(id).getCombo().entrySet()) { //ordering of orders and combo should be the same. This appears to be a correct assumption
                Contract contract = new Contract();
                if (Parameters.symbol.get(entry.getKey().getSerialno() - 1).getContractID() > 0) {
                    contract.m_conId = Parameters.symbol.get(entry.getKey().getSerialno() - 1).getContractID();
                }
                contract.m_currency = Parameters.symbol.get(entry.getKey().getSerialno() - 1).getCurrency();
                contract.m_exchange = Parameters.symbol.get(entry.getKey().getSerialno() - 1).getExchange();
                if (Parameters.symbol.get(id).getBrokerSymbol() != null) {
                    contract.m_symbol = Parameters.symbol.get(id).getBrokerSymbol();
                }
                if (Parameters.symbol.get(id).getExchangeSymbol() != null && Parameters.symbol.get(id).getType().equals("STK")) {
                    contract.m_localSymbol = Parameters.symbol.get(id).getExchangeSymbol();
                }
                out.add(contract);

            }
        }
        return out;
    }

    public Contract createContract(BeanSymbol s) {
        Contract contract = new Contract();
        int id = Utilities.getIDFromBrokerSymbol(Parameters.symbol, s.getBrokerSymbol(), s.getType(), s.getExpiry() == null ? "" : s.getExpiry(), s.getRight() == null ? "" : s.getRight(), s.getOption() == null ? "" : s.getOption());
        if (id >= 0) {
            if (s.getContractID() > 0) {
                contract.m_conId = s.getContractID();
            }
            contract.m_conId = Parameters.symbol.get(id).getContractID();
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            contract.m_symbol = Parameters.symbol.get(id).getBrokerSymbol();
            if (s.getExchangeSymbol() != null && Parameters.symbol.get(id).getType().equals("STK")) {
                contract.m_localSymbol = s.getExchangeSymbol();
            }
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            contract.m_primaryExch = Parameters.symbol.get(id).getPrimaryexchange();
            contract.m_currency = Parameters.symbol.get(id).getCurrency();
            contract.m_strike = Utilities.getDouble(Parameters.symbol.get(id).getOption(), 0);
            contract.m_right = Parameters.symbol.get(id).getRight();
            contract.m_secType = Parameters.symbol.get(id).getType();
            contract.m_expiry = Parameters.symbol.get(id).getExpiry();
        } else {
            logger.log(Level.INFO, "101,ErrorSymbolIDNotFound,{0}", new Object[]{s.getDisplayname()});
        }
        return contract;
    }

    public ArrayList<Integer> placeOrder(BeanConnection c, OrderEvent e, HashMap<Integer, Order> orders, ExecutionManager oms) {
        ArrayList<Integer> orderids = new ArrayList<>();
        int symbolID = e.getSymbolBean().getSerialno();
        EnumOrderSide side = e.getSide();
        EnumOrderReason notify = e.getReason();
        EnumOrderStage stage = e.getOrderStage();
        int internalOrderID = e.getInternalorder();
        int internalOrderIDEntry = e.getInternalorderentry();
        EnumOrderType ordType = e.getOrderType();
        if (!orders.isEmpty()) {
            orderids = placeOrder1(c, symbolID, side, notify, stage, ordType, orders, internalOrderID, internalOrderIDEntry, e.getTag(), e.isScale(), e.getLog(), oms, e);
        }
        return orderids;
    }

    private synchronized ArrayList<Integer> placeOrder1(BeanConnection c, int symbolID, EnumOrderSide side, EnumOrderReason reason, EnumOrderStage stage, EnumOrderType ordType, HashMap<Integer, Order> orders, int internalOrderID, int internalOrderIDEntry, String tag, boolean scale, String log, ExecutionManager oms, OrderEvent event) {
        ArrayList<Integer> orderids = new ArrayList<>();
        if (!tradeIntegrityOK(side, stage, orders, true)) {//reset trading flag set during createorder
            return orderids;
        }
        int i = -1;
        boolean comboOrderMapsUpdated = false;
        for (Map.Entry<Integer, Order> entry1 : orders.entrySet()) {//loop for each order and place order
            i = i + 1;
            Order order = entry1.getValue();
            int symbolid = entry1.getKey();
            if (c.getReqHandle().getHandle()) {
                OrderBean ob;
                int mOrderID = order.m_orderId == 0 ? c.getIdmanager().getNextOrderId() : order.m_orderId;
                if (order.m_orderId == 0) {
                    c.getOrders().put(mOrderID, new OrderBean());
                }
                int parentid = symbolID - 1;
                ob = c.getOrders().get(mOrderID);
                //save orderIDs at two places
                //1st location
                ob.setOrderDate(new Date().getTime());
                ob.setOrderID(mOrderID);
                ob.setChildOrderSize(order.m_totalQuantity);
                ob.setIntent(stage);
                ob.setReason(reason);
                ob.setOcaGroup(order.m_ocaGroup);
                ob.setOcaExecutionLogic(order.m_ocaType);
                ob.setOrderType(ordType);
                int displaySize=0;
                int value = Utilities.getInt(event.getOrderAttributes().get("value"), 0);
                double bidPrice = Parameters.symbol.get(parentid).getBidPrice();
                double askPrice = Parameters.symbol.get(parentid).getAskPrice();
                double lastPrice=Parameters.symbol.get(parentid).getLastPrice();
                if(value>0){
                    if(lastPrice<=0){
                        lastPrice=Math.max(bidPrice,askPrice);
                    }
                    if(lastPrice>0){
                        displaySize=(int)(value/lastPrice);
                    }
                }else{
                    displaySize = Utilities.getInt(event.getOrderAttributes().get("displaysize"), 0) * Parameters.symbol.get(parentid).getMinsize();
                }
                double impactCost = Math.abs((askPrice - bidPrice) * 2 / (askPrice + bidPrice));
                double impactCostThreshold=Utilities.getDouble(event.getOrderAttributes().get("thresholdimpactcost" ), 0.05);
                if (displaySize>0 && impactCost < impactCostThreshold) {
                    double rand = random.nextGaussian();
                    displaySize = (int) (displaySize * rand);
                    displaySize = (int) Math.round(Utilities.roundTo(displaySize, rand));
                    displaySize=Math.max(Parameters.symbol.get(parentid).getMinsize(),displaySize);
                }
                logger.log(Level.INFO,"500,DisplaySizeSet,{0}",new Object[]{displaySize});
                order.m_displaySize = displaySize;
                ob.setDisplaySize(order.m_displaySize);
                ob.setScale(scale);
                ob.setLog(log);
                boolean singlelegorder = Parameters.symbol.get(parentid).getCombo().isEmpty() & orders.size() == 1;
                if (singlelegorder) {
                    ob.setParentSymbolID(symbolID); //symbolID = the only order    
                    if (ob.getChildSymbolID() == 0) {
                        ob.setChildSymbolID(symbolID);
                    }
                    ob.setParentOrderSide(side);
                    if (ob.getChildOrderSide() == null) {
                        ob.setChildOrderSide(side);
                    }
                    ob.setParentOrderSize(order.m_totalQuantity);
                    if (ob.getChildOrderSize() == 0) {
                        ob.setChildOrderSize(order.m_totalQuantity);
                    }
                    ob.setChildLimitPrice(order.m_lmtPrice);
                    ob.setParentLimitPrice(order.m_lmtPrice);
                    ob.setTriggerPrice(order.m_auxPrice);
                    ob.setChildStatus(EnumOrderStatus.SUBMITTED);
                    ob.setParentStatus(EnumOrderStatus.SUBMITTED);
                    ob.setOrderValidity(order.m_tif);
                    ob.setOrderReference(order.m_orderRef);
                    ob.setParentInternalOrderID(internalOrderID);
                    ob.setInternalOrderID(internalOrderID);
                    ob.setInternalOrderIDEntry(internalOrderIDEntry);
                    ArrayList<Contract> contracts = c.getWrapper().createContract(ob.getChildSymbolID() - 1);

                    if (order.m_displaySize < order.m_totalQuantity && order.m_displaySize > 0 && !event.getOrderStage().equals(EnumOrderStage.AMEND)) {
                        OrderEvent subEvent = event.clone(event);
                        subEvent.setOrderStage(EnumOrderStage.INIT);
                        subEvent.setOrderSize(event.getOrderSize() - order.m_displaySize);
                        order.m_totalQuantity = order.m_displaySize;
                        ob.setDisplaySize(order.m_displaySize);
                        ob.setChildOrderSize(order.m_totalQuantity);
                        int connectionid = Parameters.connection.indexOf(this.getC());
                        logger.log(Level.INFO, "500,Placing Hidden Order. Current OrderSize: {0}, Residual:{1}", new Object[]{String.valueOf(order.m_totalQuantity), String.valueOf(subEvent.getOrderSize())});
                        if (Parameters.symbol.get(parentid).getType().equals("OPT") && event.getOrderType().equals(EnumOrderType.CUSTOMREL)) {
                            double limitprice = Utilities.getLimitPriceForOrder(Parameters.symbol, parentid, Parameters.symbol.get(parentid).getUnderlyingID(), side, oms.tickSize,event.getOrderType());
                            if (limitprice > 0) {
                                order.m_lmtPrice = limitprice;
                            }
                        }
                        int delay=Utilities.getInt(event.getOrderAttributes().get("delay"), 0);
                        oms.getFillRequestsForTracking().get(connectionid).add(new LinkedAction(c, mOrderID, subEvent, EnumLinkedAction.PROPOGATE,delay));
                    }
                    order.m_displaySize = 0; //reset display size to zero as we do not use IB's displaysize feature
                    eClientSocket.placeOrder(mOrderID, contracts.get(0), order);
                    if (stage != EnumOrderStage.AMEND) {
                        getRecentOrders().add(new Date().getTime());
                    }
                    logger.log(Level.INFO, "401,OrderPlacedWithBroker,{0}:{1}:{2}:{3}:{4},OrderSide={5}:Size={6}:OrderType:{7}:LimitPrice:{8}:AuxPrice:{9}",
                            new Object[]{order.m_orderRef, c.getAccountName(), Parameters.symbol.get(ob.getParentSymbolID() - 1).getDisplayname(), Integer.toString(ob.getInternalOrderID()),
                                String.valueOf(mOrderID), ob.getParentOrderSide(), String.valueOf(order.m_totalQuantity), order.m_orderType, String.valueOf(order.m_lmtPrice),
                                String.valueOf(order.m_auxPrice)});
                    orderids.add(mOrderID);
                } else {//combo order
                    if (order.m_orderId > 0 && ob.getIntent() == EnumOrderStage.AMEND) {//combo amendment
                        //do nothing
                    } else if (orders.size() > 1 && tag.equals("")) {//combo new order
                        int j = -1;
                        for (Map.Entry<BeanSymbol, Integer> entry2 : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                            j = j + 1;
                            if (symbolid == entry2.getKey().getSerialno() - 1) {//order is for the childsymbol
                                ob.setChildSymbolID(entry2.getKey().getSerialno());
                                ob.setParentSymbolID(symbolID);
                                ob.setParentOrderSide(side);
                                ob.setScale(scale);
                                ob.setInternalOrderID(Algorithm.orderidint.addAndGet(1));
                                ob.setParentOrderSize(Math.abs(order.m_totalQuantity / entry2.getValue()));
                                if (entry2.getValue() > 0) {
                                    ob.setChildOrderSide(side);
                                } else {
                                    switch (side) {
                                        case BUY:
                                            ob.setChildOrderSide(EnumOrderSide.SHORT);
                                            break;
                                        case SELL:
                                            ob.setChildOrderSide(EnumOrderSide.COVER);
                                            break;
                                        case SHORT:
                                            ob.setChildOrderSide(EnumOrderSide.BUY);
                                            break;
                                        case COVER:
                                            ob.setChildOrderSide(EnumOrderSide.SELL);
                                            break;
                                        default:
                                            ob.setChildOrderSide(EnumOrderSide.UNDEFINED);
                                            break;
                                    }
                                }
                            }
                        }
                    } else if (tag.contains("STUB")) {//if order size==1 and its a combo, then it is a tag
                        int j = -1;
                        for (Map.Entry<BeanSymbol, Integer> entry2 : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                            j = j + 1;
                            if (symbolid == entry2.getKey().getSerialno() - 1) {//order is for the childsymbol
                                ob.setParentSymbolID(symbolID);
                                ob.setChildSymbolID(entry2.getKey().getSerialno());
                                ob.setParentOrderSide(side);
                                switch (tag) {
                                    case "STUBCOMPLETE":
                                        if (entry2.getValue() > 0) {
                                            ob.setChildOrderSide(side);
                                        } else {
                                            ob.setChildOrderSide(switchSide(side));
                                        }
                                        break;
                                    case "STUBREDUCE":
                                        if (entry2.getValue() < 0) {
                                            ob.setChildOrderSide(side);
                                        } else {
                                            ob.setChildOrderSide(switchSide(side));
                                        }
                                        break;
                                    default:
                                        break;
                                }
                                ob.setParentOrderSize(order.m_totalQuantity);
                                ob.setChildOrderSize(order.m_totalQuantity);
                            }
                        }
                    }
                    ob.setChildStatus(EnumOrderStatus.SUBMITTED);
                    ob.setParentStatus(EnumOrderStatus.SUBMITTED);
                    ob.setOrderValidity(order.m_tif);
                    ob.setTriggerPrice(order.m_auxPrice);
                    ob.setChildLimitPrice(order.m_lmtPrice);
                    ob.setOrderReference(order.m_orderRef);
                    ob.setInternalOrderID(internalOrderID);
                    ob.setInternalOrderIDEntry(internalOrderIDEntry);
                    ob.setLog(log);

                    if (orders.size() > 1 && !comboOrderMapsUpdated) {
                        ArrayList<Integer> temp = new ArrayList<>();
                        for (i = 0; i < orders.size(); i++) {
                            temp.add(mOrderID + i);
                        }
                        comboOrderMapsUpdated = true;
                        c.getOrderMapping().put(new Index(order.m_orderRef, internalOrderID), temp);

                    }
                    ArrayList<Contract> contracts = c.getWrapper().createContract(ob.getChildSymbolID() - 1);
                    eClientSocket.placeOrder(mOrderID, contracts.get(0), order);
                    if (stage != EnumOrderStage.AMEND) {
                        getRecentOrders().add(new Date().getTime());
                    }
                    logger.log(Level.INFO, "101,OrderPlacedWithBroker,{0}", new Object[]{c.getAccountName() + delimiter + order.m_orderRef + delimiter + Parameters.symbol.get(ob.getParentSymbolID() - 1).getDisplayname() + delimiter + Parameters.symbol.get(ob.getChildSymbolID() - 1).getDisplayname() + delimiter + mOrderID + delimiter + ob.getParentOrderSide() + delimiter + order.m_totalQuantity + delimiter + order.m_orderType + delimiter + order.m_lmtPrice + delimiter + order.m_auxPrice + delimiter + order.m_tif + delimiter + order.m_goodTillDate + delimiter});
                    orderids.add(mOrderID);
                }
            }
        }

        return orderids;
    }

    synchronized boolean tradeIntegrityOK(EnumOrderSide side, EnumOrderStage stage, HashMap<Integer, Order> orders, boolean reset) {
        if ((side == EnumOrderSide.BUY || side == EnumOrderSide.SHORT) && stage != EnumOrderStage.AMEND && (isStopTrading() || (getRecentOrders().size() == c.getOrdersHaltTrading() && (new Date().getTime() - (Long) getRecentOrders().get(0)) < 120000))) {
            setStopTrading(!reset);
            Thread t = new Thread(new Mail(c.getOwnerEmail(), "Account: " + c.getAccountName() + ", Connection: " + c.getIp() + ", Port: " + c.getPort() + ", ClientID: " + c.getClientID() + " has sent " + c.getOrdersHaltTrading() + " orders in the last two minutes. Trading halted", "Algorithm SEVERE ALERT - " + orders.get(0).m_orderRef.toUpperCase()));
            t.start();
            return false;
        }
        return true;
    }

    private EnumOrderSide switchSide(EnumOrderSide side) {
        EnumOrderSide out;
        switch (side) {
            case BUY:
                out = EnumOrderSide.SHORT;
                break;
            case SELL:
                out = EnumOrderSide.COVER;
                break;
            case SHORT:
                out = EnumOrderSide.BUY;
                break;
            case COVER:
                out = EnumOrderSide.SELL;
                break;
            default:
                out = EnumOrderSide.UNDEFINED;
                break;
        }

        return out;
    }

    public void cancelMarketData(BeanSymbol s) {

        int reqid = -1;
        for (Request r : requestDetails.values()) {
            if (r.requestType.equals(EnumRequestType.STREAMING) && r.symbol.getSerialno() == s.getSerialno()) {
                reqid = r.requestID;
            }
        }

        if (reqid >= 0 && requestDetails.get(reqid).requestStatus != EnumRequestStatus.CANCELLED) {//original: streaming market data
            requestDetailsWithSymbolKey.get(s.getSerialno()).requestStatus = EnumRequestStatus.CANCELLED;
            requestDetails.get(reqid).requestStatus = EnumRequestStatus.CANCELLED;
            eClientSocket.cancelMktData(reqid);
            requestDetails.remove(reqid);
            //c.getmReqID().remove(reqid);
        }
        if (reqid == -1) {
            //logger.log(Level.SEVERE, "Unable to cancel data request for symbol: {0}", new Object[]{s.getSymbol() + "-" + s.getType() + "-" + s.getExchange()});
        }
    }

    public void cancelOrder(BeanConnection c, int orderID, boolean force) {
        ArrayList<Integer> linkedorderids = TradingUtil.getLinkedOrderIds(orderID, c);
        for (int orderid : linkedorderids) {
            if (!force) {//regular cancellation
                switch (c.getOrders().get(orderid).getChildStatus()) {
                    case SUBMITTED:
                    case ACKNOWLEDGED:
                        c.getOrders().get(orderid).setCancelRequested(true);
                        this.eClientSocket.cancelOrder(orderid);
                        //handle cancellations that are not successful
                        logger.log(Level.INFO, "401,CancellationPlacedWithBroker,{0}:{1}:{2}:{3}:{4},Force={5}",
                                new Object[]{c.getOrders().get(orderID).getOrderReference(), c.getAccountName(), Parameters.symbol.get(c.getOrders().get(orderID).getParentSymbolID() - 1).getDisplayname(), c.getOrders().get(orderID).getInternalOrderID(), orderID, force});
                        break;
                }
            } else {
                c.getOrders().get(orderid).setCancelRequested(true);
                this.eClientSocket.cancelOrder(orderid);
                //handle cancellations that are not successful
                logger.log(Level.INFO, "401,CancellationPlacedWithBroker,{0}:{1}:{2}:{3}:{4},Force={5}",
                        new Object[]{c.getOrders().get(orderID).getOrderReference(), c.getAccountName(), Parameters.symbol.get(c.getOrders().get(orderID).getParentSymbolID() - 1).getDisplayname(), c.getOrders().get(orderID).getInternalOrderID(), orderID, force});

            }
        }
    }

    public void requestFundamentalData(BeanSymbol s, String reportType) {

        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        logger.log(Level.FINE, "Waiting for handle for Historical Data for symbol:{0}, Account: {1}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName()});
        if (getC().getReqHistoricalHandle().getHandle()) {
            logger.log(Level.FINE, "Waiting for requestid for Historical Data for symbol:{0}, Account: {1}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName()});
            mRequestId = requestIDManager.getNextRequestId();
            logger.log(Level.FINE, "Waiting for lock for Historical Data for symbol:{0}, Account: {1}, RequestID:{2}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName(), mRequestId});
            requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.valueOf(reportType.toUpperCase()), EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
            s.getFundamental().setSnapshotRequestID(mRequestId);
            logger.log(Level.FINE, "Requested Historical Data for symbol:{0}, Account: {1}, RequestID:{2}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName(), mRequestId});
            eClientSocket.reqFundamentalData(mRequestId, con, reportType.toLowerCase());
            logger.log(Level.FINE, "Finished placing request to eclientsocket for symbol:{0}, Account: {1}, RequestID:{2}", new Object[]{s.getDisplayname() + "_" + reportType, c.getAccountName(), mRequestId});

        }
    }

    public void cancelFundamentalData(int reqId) {
        eClientSocket.cancelFundamentalData(reqId);
    }

    public void requestDailyBar(BeanSymbol s, String duration) {
        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        if (s.getExchangeSymbol() != null) {
            con.m_localSymbol = s.getExchangeSymbol();
        }
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (getC().getReqHistoricalHandle().getHandle()) {
            if (getC().getReqHandle().getHandle()) {
                mRequestId = requestIDManager.getNextRequestId();
                requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.HISTORICAL, EnumBarSize.DAILY, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                logger.log(Level.FINER, "HistoricalDataRequestSent_Historical,{0}", new Object[]{mRequestId + delimiter + s.getDisplayname()});
                String currDateStr = DateUtil.getFormattedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
                String endDateStr = currDateStr + " " + "23:30:00";
                //System.out.println(s.getDisplayname()+":"+mRequestId+":"+"DailyBars");
                eClientSocket.reqHistoricalData(mRequestId, con, endDateStr, duration, "1 day", "TRADES", 1, 2, null);
                logger.log(Level.INFO, "403,HistoricalDataRequestSent,{0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + duration + delimiter + "1 day"});
            } else {
                System.out.println("### Error getting handle while requesting market data for contract " + con.m_symbol + " Name: " + s.getBrokerSymbol());
            }
        }
    }

    public void requestOpenOrders() {
        eClientSocket.reqOpenOrders();
    }

    public void requestExecutionDetails(ExecutionFilter filter) {
        mRequestId = requestIDManager.getNextRequestId();
        eClientSocket.reqExecutions(mRequestId, filter);
    }

    public void requestNewsBulletin(boolean allMessages) {
        eClientSocket.reqNewsBulletins(allMessages);
    }

    public void cancelNewsBulletin() {
        eClientSocket.cancelNewsBulletins();
    }

    public void requestHistoricalData(BeanSymbol s, String endDate, String duration, String barSize) {
        Contract con = new Contract();
        con.m_symbol = s.getBrokerSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (s.getExchangeSymbol() != null && s.getType().equals("STK")) {
            con.m_localSymbol = s.getExchangeSymbol();
        }
        if (s.getType().equals("FUT") || s.getType().equals("OPT")) {
            con.m_includeExpired = true;
        }

        if (getC().getReqHistoricalHandle().getHandle()) {
            if (getC().getReqHandle().getHandle()) {
                mRequestId = requestIDManager.getNextRequestId();
                switch (barSize) {
                    case "1 day":
                        requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.HISTORICAL, EnumBarSize.DAILY, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));

                        break;
                    case "1 min":
                        requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.HISTORICAL, EnumBarSize.ONEMINUTE, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));

                        break;
                    case "1 secs":
                        requestDetails.putIfAbsent(mRequestId, new Request(EnumSource.IB, mRequestId, s, EnumRequestType.HISTORICAL, EnumBarSize.ONESECOND, EnumRequestStatus.PENDING, new Date().getTime(), c.getAccountName()));
                        break;
                    default:
                        break;
                }
                //System.out.println(s.getDisplayname()+":"+mRequestId+":"+barSize);
                eClientSocket.reqHistoricalData(mRequestId, con, endDate, duration, barSize, "TRADES", 1, 2, null);
                logger.log(Level.INFO, "403,HistoricalDataRequestSent,{0}", new Object[]{getC().getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + duration + delimiter + barSize + delimiter + endDate});
                //System.out.println("HistoricalDataRequestSent"+c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + duration + delimiter + barSize+delimiter+endDate);


            } else {
                System.out.println("### Error getting handle while requesting market data for contract " + con.m_symbol + " Name: " + s.getBrokerSymbol());
            }
        }
    }

    public void cancelHistoricalData(int reqid) {
        eClientSocket.cancelHistoricalData(reqid);
    }

    //<editor-fold defaultstate="collapsed" desc="Listeners">
    public void addOrderStatusListener(OrderStatusListener l) {
        tes.addOrderStatusListener(l);
    }

    public void removeOrderStatusListener(OrderStatusListener l) {
        tes.removeOrderStatusListener(l);
    }

    public void addTWSErrorListener(TWSErrorListener l) {
        tes.addTWSErrorListener(l);
    }

    public void removeTWSErrorListener(TWSErrorListener l) {
        tes.removeTWSErrorListener(l);
    }

    public void addBidAskListener(BidAskListener l) {
        tes.addBidAskListener(l);
    }

    public void removeBidAskListener(BidAskListener l) {
        tes.removeBidAskListener(l);
    }

    public void addFundamentalListener(FundamentalDataListener l) {
        this._fundamentallisteners.add(l);
    }

    public void removeFundamentalListener(FundamentalDataListener l) {
        this._fundamentallisteners.remove(l);
    }

    public void addTradeListener(TradeListener l) {
        tes.addTradeListener(l);
    }

    public void removeTradeListener(TradeListener l) {
        tes.removeTradeListener(l);
    }

    //</editor-fold>
    //<editor-fold defaultstate="collapsed" desc="EWrapper Overrides">
    @Override
    public void tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        try {
            if (realtime && serverInitialized.get()) {
                realtime_tickPrice(tickerId, field, price, canAutoExecute);
            } else {

                boolean snapshot = false;
                boolean proceed = true;
                int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
                int id = serialno - 1;
                if (requestDetails.get(tickerId) != null) {
                    snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
                } else {
                    logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId + delimiter + this.getC().getAccountName()});
                    proceed = false;
                }
                if (proceed) {
                    Request r;
                    r = requestDetails.get(tickerId);

                    if (r != null) {
                        r.requestStatus = EnumRequestStatus.SERVICED;
                    }
                    //logger.log(Level.INFO,"request id:{0},id: {1},price:{2}",new Object[]{tickerId,id,price});
                    //Parameters.updateSymbol(id, field, price);
                    if (id >= 0) {
                        if (field == TickType.BID) {
                            Parameters.symbol.get(id).setBidPrice(price);
                            if (MainAlgorithm.getCollectTicks()) {
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Bid," + price);
                            }
                            tes.fireBidAskChange(id);
                        } else if (field == TickType.ASK) {
                            Parameters.symbol.get(id).setAskPrice(price);
                            if (MainAlgorithm.getCollectTicks()) {
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Ask," + price);
                            }
                            tes.fireBidAskChange(id);
                        } else if ((field == TickType.LAST) && (MainAlgorithm.rtvolume && snapshot || !MainAlgorithm.rtvolume)) {
                            double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice() == 0 ? price : Parameters.symbol.get(id).getPrevLastPrice();
                            Parameters.symbol.get(id).setPrevLastPrice(prevLastPrice);
                            Parameters.symbol.get(id).setLastPrice(price);
                            Parameters.symbol.get(id).setLastPriceTime(System.currentTimeMillis());
                            Parameters.symbol.get(id).getTradedPrices().add(price);
                            Parameters.symbol.get(id).getTradedDateTime().add(System.currentTimeMillis());
                            if (MainAlgorithm.getCollectTicks()) {
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Trade," + price);
                            }
                            tes.fireTradeEvent(id, com.ib.client.TickType.LAST);
                            if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(new Date().getTime(), com.ib.client.TickType.LAST, String.valueOf(price));
                            }
                        } else if (field == TickType.HIGH) {
                            Parameters.symbol.get(id).setHighPrice(price, false);
                        } else if (field == TickType.LOW) {
                            Parameters.symbol.get(id).setLowPrice(price, false);
                        } else if (field == TickType.CLOSE) {
                            Parameters.symbol.get(id).setClosePrice(price);
                            tes.fireTradeEvent(id, com.ib.client.TickType.CLOSE);
                        } else if (field == TickType.OPEN) {
                            Parameters.symbol.get(id).setOpenPrice(price);
                        }
                        if (Parameters.symbol.get(id).isAddedToSymbols()) {
                            jedis.publish(topic,field + "," + new Date().getTime() + "," + price + "," + Parameters.symbol.get(id).getDisplayname());

                        }
                    }
                }

            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    public void realtime_tickPrice(int tickerId, int field, double price, int canAutoExecute) {
        try {
            boolean proceed = true;
            int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
            int id = serialno - 1;
            boolean snapshot = false;
            if (requestDetails.get(tickerId) != null) {
                snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
            } else {
                logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId + delimiter + this.getC().getAccountName()});
                proceed = false;
            }
            if (proceed) {
                Request r;
                r = requestDetails.get(tickerId + delimiter + c.getAccountName());

                if (r != null) {
                    r.requestStatus = EnumRequestStatus.SERVICED;
                }

                String type = Parameters.symbol.get(id).getType();
                String header = topic;
                String symbol = Parameters.symbol.get(id).getDisplayname();

                if (field == com.ib.client.TickType.CLOSE) {
                    Parameters.symbol.get(id).setClosePrice(price);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Close," + price);
                    }
                } else if (field == com.ib.client.TickType.OPEN) {
                    Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + price + "," + symbol);
                    Parameters.symbol.get(id).setOpenPrice(price);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Open," + price);
                    }
                } else if (field == com.ib.client.TickType.HIGH) {
                    Parameters.symbol.get(id).setHighPrice(price, false);
                    Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + price + "," + symbol);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "High," + price);
                    }
                } else if (field == com.ib.client.TickType.LOW) {
                    Parameters.symbol.get(id).setLowPrice(price, false);
                    Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + price + "," + symbol);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Low," + price);
                    }
                } else if (field == com.ib.client.TickType.BID || field == com.ib.client.TickType.ASK) {
                    Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + price + "," + symbol);
                }
                if (field == com.ib.client.TickType.LAST) {
                    if (MainAlgorithm.rtvolume && snapshot || !MainAlgorithm.rtvolume) {
                        double lastPrice = Parameters.symbol.get(id).getLastPrice();
                        Parameters.symbol.get(id).setPrevLastPrice(lastPrice);
                        if (Parameters.symbol.get(id).getLastPrice() > 0 && Parameters.symbol.get(id).getOpenPrice() == 0) {
                            requestSingleSnapshot(Parameters.symbol.get(id));
                        }
                        Parameters.symbol.get(id).setLastPrice(price);

                        Rates.rateServer.send(header, com.ib.client.TickType.LAST + "," + new Date().getTime() + "," + price + "," + symbol);
                        Rates.rateServer.send(header, com.ib.client.TickType.CLOSE + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getClosePrice() + "," + symbol);
                        Rates.rateServer.send(header, com.ib.client.TickType.OPEN + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getOpenPrice() + "," + symbol);
                        Rates.rateServer.send(header, com.ib.client.TickType.HIGH + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getHighPrice() + "," + symbol);
                        Rates.rateServer.send(header, com.ib.client.TickType.LOW + "," + new Date().getTime() + "," + Parameters.symbol.get(id).getLowPrice() + "," + symbol);
                        if (MainAlgorithm.getCollectTicks()) {
                            TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Last," + price);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        try {
            if (realtime && serverInitialized.get()) {
                realtime_tickSize(tickerId, field, size);
            } else {

                int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
                boolean proceed = true;
                boolean snapshot = false;
                if (requestDetails.get(tickerId) != null) {
                    snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
                } else {
                    logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId + delimiter + this.getC().getAccountName()});
                    proceed = false;
                }
                if (proceed) {

                    Request r;
                    r = requestDetails.get(tickerId);

                    if (r != null) {
                        r.requestStatus = EnumRequestStatus.SERVICED;
                    }
                    int id = serialno - 1;
                    if (id >= 0) {
                        if (field == TickType.BID_SIZE) {
                            Parameters.symbol.get(id).setBidSize(size);
                            if (MainAlgorithm.getCollectTicks()) {
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "BidSize," + size);
                            }
                        } else if (field == TickType.ASK_SIZE) {
                            Parameters.symbol.get(id).setAskSize(size);
                            if (MainAlgorithm.getCollectTicks()) {
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "AskSize," + size);
                            }
                        } else if (field == TickType.LAST_SIZE) {
                            //Parameters.symbol.get(id).setLastSize(size);
                            if (MainAlgorithm.getCollectTicks()) {
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastSizeTick," + size);
                            }

                        } else if (field == TickType.VOLUME) {
                            if (MainAlgorithm.rtvolume && snapshot || !MainAlgorithm.rtvolume) {
                                Parameters.symbol.get(id).getTradedVolumes().add(size - Parameters.symbol.get(id).getVolume());
                                double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice();
                                double lastPrice = Parameters.symbol.get(id).getLastPrice();
                                int incrementalSize = Parameters.symbol.get(id).getVolume() > 0 ? size - Parameters.symbol.get(id).getVolume() : 0;
                                int calculatedLastSize;
                                if (prevLastPrice != lastPrice) {
                                    Parameters.symbol.get(id).setPrevLastPrice(lastPrice);
                                    calculatedLastSize = incrementalSize;
                                } else {
                                    calculatedLastSize = incrementalSize + Parameters.symbol.get(id).getLastSize();
                                }
                                //int calculatedLastSize=prevLastPrice==Parameters.symbol.get(id).getLastPrice()?Parameters.symbol.get(id).getLastSize()+incrementalSize:incrementalSize;
                                Parameters.symbol.get(id).setLastSize(calculatedLastSize);
                                Parameters.symbol.get(id).setVolume(size, false);
                                tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                                tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                                if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                    Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(new Date().getTime(), com.ib.client.TickType.VOLUME, String.valueOf(calculatedLastSize));
                                }
                                if (MainAlgorithm.getCollectTicks()) {
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Volume," + size);
                                    TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Calculated LastSize," + calculatedLastSize);
                                }
                            }
                        }
                        if (Parameters.symbol.get(id).isAddedToSymbols()) {
                            jedis.publish(topic, field + "," + new Date().getTime() + "," + size + "," + Parameters.symbol.get(id).getDisplayname());

                        }
                    }
                }

            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    public void realtime_tickSize(int tickerId, int field, int size) {
        try {
            boolean proceed = true;
            int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
            int id = serialno - 1;

            boolean snapshot = false;
            if (requestDetails.get(tickerId) != null) {
                snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
            } else {
                logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId});
                proceed = false;
            }
            if (proceed) {
                Request r;
                r = requestDetails.get(tickerId);

                if (r != null) {
                    r.requestStatus = EnumRequestStatus.SERVICED;
                }
                String type = Parameters.symbol.get(id).getType();
                String header = topic;
                String symbol = Parameters.symbol.get(id).getDisplayname();
                if (field == com.ib.client.TickType.BID_SIZE || field == com.ib.client.TickType.ASK_SIZE) {
                    Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + size + "," + symbol);
                }
                if (field == com.ib.client.TickType.VOLUME) {
                    long localTime = new Date().getTime();
                    int lastSize = size - Parameters.symbol.get(id).getVolume();
                    if ((MainAlgorithm.rtvolume && snapshot) || !MainAlgorithm.rtvolume) {
                        //Rates.rateServer.send(header, com.ib.client.TickType.LAST_SIZE + "," + new Date().getTime() + "," + lastSize + "," + symbol);
                        Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + size + "," + symbol);
                        Parameters.symbol.get(id).setVolume(size, false);
                        if (MainAlgorithm.getCollectTicks()) {
                            TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Volume," + size);
                        }
                    }
                } else if (field == com.ib.client.TickType.LAST_SIZE) {
                    long localTime = new Date().getTime();
                    if ((MainAlgorithm.rtvolume && snapshot) || !MainAlgorithm.rtvolume) {
                        Rates.rateServer.send(header, field + "," + new Date().getTime() + "," + size + "," + symbol);
                        if (MainAlgorithm.getCollectTicks()) {
                            TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Lastsize," + size);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
        }
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        try {
            int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
            Request r;
            r = requestDetails.get(tickerId);

            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno - 1;
            if (id >= 0) {
                switch (field) {
                    case 10:
                        Parameters.symbol.get(id).setBidVol(impliedVol);
                        break;
                    case 11:
                        Parameters.symbol.get(id).setAskVol(impliedVol);
                        break;
                    case 12:
                        Parameters.symbol.get(id).setLastVol(impliedVol);
                        break;
                    default:
                        break;
                }
                //System.out.println("tickerid:"+tickerId+"field:"+field+"impliedVol"+impliedVol+"delta:"+delta+"optPrice:"+optPrice+"underlying price:"+undPrice);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        try {
            if (tickType == 48) {
                if (realtime && serverInitialized.get()) {
                    realtime_rtVolume(tickerId, value);
                } else {
                    boolean proceed = true;
                    int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
                    int id = serialno - 1;
                    boolean snapshot = false;
                    if (requestDetails.get(tickerId) != null) {
                        snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
                    } else {
                        logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId});
                        proceed = false;
                    }
                    if (proceed) {
                        String[] values = value.split(";");
                        if (values.length == 6) {
                            double last = Utilities.getDouble(values[0], 0);
                            double last_size = Utilities.getInt(values[1], 0);
                            long time = Utilities.getLong(values[2], 0);
                            int volume = Utilities.getInt(values[3], 0);

                            Parameters.symbol.get(id).setLastPrice(last);
                            Parameters.symbol.get(id).getTradedVolumes().add(volume - Parameters.symbol.get(id).getVolume());
                            double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice();
                            double lastPrice = Parameters.symbol.get(id).getLastPrice();
                            int incrementalSize = Parameters.symbol.get(id).getVolume() > 0 ? volume - Parameters.symbol.get(id).getVolume() : 0;
                            int calculatedLastSize;
                            if (prevLastPrice != lastPrice) {
                                Parameters.symbol.get(id).setPrevLastPrice(lastPrice);
                                calculatedLastSize = incrementalSize;
                            } else {
                                calculatedLastSize = incrementalSize + Parameters.symbol.get(id).getLastSize();
                            }
                            //int calculatedLastSize=prevLastPrice==Parameters.symbol.get(id).getLastPrice()?Parameters.symbol.get(id).getLastSize()+incrementalSize:incrementalSize;
                            Parameters.symbol.get(id).setLastSize(calculatedLastSize);
                            Parameters.symbol.get(id).setVolume(volume, false);
                            tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                            tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                            if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(new Date().getTime(), com.ib.client.TickType.VOLUME, String.valueOf(calculatedLastSize));
                            }
                            if (MainAlgorithm.getCollectTicks()) {
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "ExchangeTimeStamp," + sdfTime.format(new Date(time)));
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Volume," + volume);
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastSize_RT," + last_size);
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Last," + last);
                                TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Calculated LastSize," + calculatedLastSize);
                            }

                        }
                    }
                }

            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    void realtime_rtVolume(int tickerId, String value) {
        try {
            boolean proceed = true;
            int serialno = requestDetails.get(tickerId) != null ? (int) requestDetails.get(tickerId).symbol.getSerialno() : 0;
            int id = serialno - 1;
            boolean snapshot = false;
            if (requestDetails.get(tickerId) != null) {
                snapshot = requestDetails.get(tickerId).requestType == EnumRequestType.SNAPSHOT ? true : false;
            } else {
                logger.log(Level.INFO, "RequestID: {0} was not found", new Object[]{tickerId});
                proceed = false;
            }
            if (proceed) {
                String[] values = value.split(";");
                if (values.length == 6) {
                    double last = Utilities.getDouble(values[0], 0);
                    double last_size = Utilities.getInt(values[1], 0);
                    long time = Utilities.getLong(values[2], 0);
                    int volume = Utilities.getInt(values[3], 0);
                    Request r;
                    r = requestDetails.get(tickerId);

                    if (r != null) {
                        r.requestStatus = EnumRequestStatus.SERVICED;
                    }
                    String type = Parameters.symbol.get(id).getType();
                    String header = topic;
                    String symbol = Parameters.symbol.get(id).getDisplayname();
                    int lastSize = volume - Parameters.symbol.get(id).getVolume();
                    Rates.rateServer.send(header, TickType.VOLUME + "," + time + "," + volume + "," + symbol);
                    Parameters.symbol.get(id).setVolume(volume, false);
                    Rates.rateServer.send(header, TickType.LAST_SIZE + "," + time + "," + last_size + "," + symbol);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "ExchangeTimeStamp," + sdfTime.format(new Date(time)));
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Volume," + volume);
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "LastSize_RT," + last_size);
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getDisplayname() + ".csv", "Last," + last);
                    }

                }

            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void tickEFP(int tickerId, int tickType, double basisPoints, String formattedBasisPoints, double impliedFuture, int holdDays, String futureExpiry, double dividendImpact, double dividendsToExpiry) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void orderStatus(int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {
        try {
            int id = -1;
            String strategy = "Unknown";
            if (getC().getOrders().get(orderId) != null) {
                id = getC().getOrders().get(orderId).getParentSymbolID() - 1;
                strategy = c.getOrders().get(orderId).getOrderReference();
            }

            if (id >= 0) {
                //String orderRef = getC().getOrders() == null ? getC().getOrders().get(orderId).getOrderReference() : "NA";
                logger.log(Level.INFO, "402,orderStatus,{0}:{1}:{2}:{3}:{4},Status={5}:Filled={6}:Remaining={7}",
                        new Object[]{strategy, c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), String.valueOf(getC().getOrders().get(orderId).getInternalOrderID()), String.valueOf(orderId), status, filled, remaining});
                //logger.log(Level.INFO, "{0},TWSReceive,orderStatus, OrderID:{1},Status:{2}.Filled:{3},Remaining:{4},AvgFillPrice:{5},LastFillPrice:{6}", new Object[]{c.getAccountName(), orderId, status, filled, remaining, avgFillPrice, lastFillPrice});
                tes.fireOrderStatus(getC(), orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
            } else {
                logger.log(Level.INFO, "402,orderStatus,{0}:{1}:{2}:{3}:{4},Status={5}:Filled={6}:Remaining={7}",
                        new Object[]{strategy, c.getAccountName(), "Unknown", String.valueOf(orderId), -1, status, filled, remaining});
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void openOrder(int orderId, Contract contract, Order order, OrderState orderState) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //tes.fireOrderStatus(c, orderId, orderState.m_status, 0, 0, 0, 0, 0, 0, 0, "openorder");
        //logger.log(Level.INFO,"orderid:{1}, Status:{2}",new Object[]{orderId,orderState.m_status});
        //System.out.println(methodName);
    }

    @Override
    public void openOrderEnd() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //    System.out.println(methodName);
    }

    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
        if (key.compareTo("AccountCode") == 0) {
            this.getAccountIDSync().put(accountName);
        }

    }

    @Override
    public void updatePortfolio(Contract contract, int position, double marketPrice, double marketValue, double averageCost, double unrealizedPNL, double realizedPNL, String accountName) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void updateAccountTime(String timeStamp) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void accountDownloadEnd(String accountName) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void nextValidId(int orderId) {
        try {
            //System.out.println("orderid:"+orderId);
            //  c.getIdmanager().initializeOrderId(orderId);
            startingOrderID.offer(String.valueOf(orderId));
            Thread.yield();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        try {
            int serialno = requestDetails.get(reqId) != null ? (int) requestDetails.get(reqId).symbol.getSerialno() : 0;

            Request r;
            r = requestDetails.get(reqId);

            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno - 1;
            logger.log(Level.INFO, "402,ContractDetailsReceived,{0}:{1}:{2}:{3}:{4},ContractID={5}:MinTick:{6}",
                    new Object[]{"Unknown", c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), -1, -1, String.valueOf(contractDetails.m_summary.m_conId), contractDetails.m_minTick});
            Parameters.symbol.get(id).setTickSize(contractDetails.m_minTick);
            Parameters.symbol.get(id).setContractID(contractDetails.m_summary.m_conId);
            try (Jedis jedis = Algorithm.marketdatapool.getResource()) {
                jedis.set(Parameters.symbol.get(id).getDisplayname(), contractDetails.m_summary.m_conId + ":" + contractDetails.m_minTick);
            }
            Parameters.symbol.get(id).setStatus(true);
            TWSConnection.mTotalSymbols = TWSConnection.mTotalSymbols - 1;

        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void bondContractDetails(int reqId, ContractDetails contractDetails) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void contractDetailsEnd(int reqId) {
        if (TWSConnection.mTotalATMChecks > 0) {
            TWSConnection.mTotalATMChecks = TWSConnection.mTotalATMChecks - 1;
        }

    }

    @Override
    public void execDetails(int reqId, Contract contract, Execution execution) {
        try {
            int id = -1;
            if (getC().getOrders().get(execution.m_orderId) != null) {
                id = getC().getOrders().get(execution.m_orderId).getParentSymbolID() - 1;
            }
            if (id >= 0) {
                logger.log(Level.INFO, "402,execDetails,{0}:{1}:{2}:{3}:{4},CumExecution={5}:AveragePrice={6}",
                        new Object[]{getC().getOrders().get(execution.m_orderId).getOrderReference(), c.getAccountName(), Parameters.symbol.get(id).getDisplayname(), String.valueOf(getC().getOrders().get(execution.m_orderId).getInternalOrderID()),String.valueOf(execution.m_orderId), String.valueOf(execution.m_cumQty), String.valueOf(execution.m_avgPrice)});

                if (getC().getOrders().get(execution.m_orderId) != null) {
                    if (getC().getOrders().get(execution.m_orderId).getParentOrderSize() - execution.m_cumQty == 0) {
                        tes.fireOrderStatus(getC(), execution.m_orderId, "Filled", execution.m_cumQty, getC().getOrders().get(execution.m_orderId).getParentOrderSize() - execution.m_cumQty, execution.m_avgPrice, execution.m_permId, reqId, 0, execution.m_clientId, "execDetails");
                    } else {
                        tes.fireOrderStatus(getC(), execution.m_orderId, "Submitted", execution.m_cumQty, getC().getOrders().get(execution.m_orderId).getParentOrderSize() - execution.m_cumQty, execution.m_avgPrice, execution.m_permId, reqId, 0, execution.m_clientId, "execDetails");
                    }
                }
            } else {
                logger.log(Level.INFO, "402,execDetails,{0}:{1}:{2}:{3}:{4},CumExecution={5}:AveragePrice={6}",
                        new Object[]{"Unknown", c.getAccountName(), "Unknown", -1, String.valueOf(execution.m_orderId),String.valueOf(execution.m_cumQty), String.valueOf(execution.m_avgPrice)});

            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    @Override
    public void execDetailsEnd(int reqId) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
//        System.out.println(methodName);
    }

    @Override
    public void updateMktDepth(int tickerId, int position, int operation, int side, double price, int size) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void updateMktDepthL2(int tickerId, int position, String marketMaker, int operation, int side, double price, int size) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void updateNewsBulletin(int msgId, int msgType, String message, String origExchange) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
        //System.out.println("Exchange:" + origExchange + " , Message: " + message);
    }

    @Override
    public void managedAccounts(String accountsList) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void receiveFA(int faDataType, String xml) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void historicalData(int reqId, String date, double open, double high, double low, double close, int volume, int count, double WAP, boolean hasGaps) {
        try {
            //System.out.println(c.getAccountName()+":"+reqId);
            int serialno;
            serialno = requestDetails.get(reqId) != null ? (int) requestDetails.get(reqId).symbol.getSerialno() : 0;

            Request r;
            r = requestDetails.get(reqId);
            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            } else {
                logger.log(Level.INFO, "Request ID not found, requestID:{0},serialno:{1}", new Object[]{reqId, serialno});
            }
            int id = serialno - 1;
            if (requestDetails.get(reqId) == null) {
                //System.out.println("NULL");
            }

            if (Parameters.symbol.size() > id) {//only proceed if the id is within the list of symbols being elibile for trading
                boolean realtimebars = false;
                //add check to confirm that getRequestDetails().get(reqId+delimiter+c.getAccountName()) is not null
                //System.out.println(reqId);
                if (date.toLowerCase().contains("finished".toLowerCase())) {
                    switch (requestDetails.get(reqId).barSize) {
                        case FIVESECOND:
                            break;
                        case DAILY:
                            if (Parameters.symbol.get(id).getDailyBar() != null) {
                                Parameters.symbol.get(id).getDailyBar().setFinished(true);
                            }
                            return;
                        case ONEMINUTE:
                            if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                                Parameters.symbol.get(id).getIntraDayBarsFromTick().setFinished(true);
                            }
                            return;
                        default:
                            return;
                    }
                }
                /*
                 realtimebars = getRequestDetails().get(reqId).requestType.equals(EnumRequestType.REALTIMEBAR) ? true : false;
                 if (date.toLowerCase().contains("finished".toLowerCase())) {
                 if (!realtimebars) {
                 Parameters.symbol.get(id).getDailyBar().setFinished(true);
                 }
                 return;
                 }
                 */
//        System.out.println("Symbol:"+ Parameters.symbol.get(id).getSymbol()+":"+DateUtil.getFormattedDate("yyyyMMdd HH:mm:ss", Long.parseLong(date)*1000));
                switch (requestDetails.get(reqId).barSize) {
                    case FIVESECOND:
                        if (Parameters.symbol.get(id).getOneMinuteBarFromRealTimeBars() != null) {
                            Parameters.symbol.get(id).getOneMinuteBarFromRealTimeBars().setOneMinOHLCFromRealTimeBars(Long.valueOf(date), open, high, low, close, Long.valueOf(volume));
                        }
                        break;
                    case DAILY:
                        if (Parameters.symbol.get(id).getDailyBar() != null) {
                            Parameters.symbol.get(id).getDailyBar().setDailyOHLC(DateUtil.parseDate("yyyyMMdd", date).getTime(), open, high, low, close, volume);
                        }
                        break;
                    case ONEMINUTE:
                        if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                            Parameters.symbol.get(id).getIntraDayBarsFromTick().setOneMinBars(Long.valueOf(date), open, high, low, close, Long.valueOf(volume));
                        }
                        break;
                    default:
                        break;


                }
            }

            //code for historical data collection in database
            if (!MainAlgorithm.isUseForTrading()) {
                BeanOHLC ohlc = new BeanOHLC();
                switch (requestDetails.get(reqId).barSize) {
                    case DAILY:
                        ohlc = new BeanOHLC(DateUtil.parseDate("yyyyMMdd", date).getTime(), open, high, low, close, volume, EnumBarSize.DAILY);
                        break;
                    case ONEMINUTE:
                        ohlc = new BeanOHLC(Long.valueOf(date) * 1000, open, high, low, close, volume, EnumBarSize.ONEMINUTE);
                        break;
                    case ONESECOND:
                        ohlc = new BeanOHLC(Long.valueOf(date) * 1000, open, high, low, close, volume, EnumBarSize.ONESECOND);
                        //System.out.println("Main:"+DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime())+":"+ohlc.getClose());
                        break;
                    default:
                        break;
                }
                MainAlgorithm.tes.fireHistoricalBars(0, null, Parameters.symbol.get(id), ohlc);
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101," + "ReqID:" + reqId, e);
        }
    }

    @Override
    public void scannerParameters(String xml) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void scannerData(int reqId, int rank, ContractDetails contractDetails, String distance, String benchmark, String projection, String legsStr) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void scannerDataEnd(int reqId) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void realtimeBar(int reqId, long time, double open, double high, double low, double close, long volume, double wap, int count) {
        try {
            int serialno = requestDetails.get(reqId) != null ? (int) requestDetails.get(reqId).symbol.getSerialno() : 0;
            Request r;
            r = requestDetails.get(reqId);

            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno - 1;
            //System.out.println("RealTime Bar: Symbol:"+Parameters.symbol.get(id).getSymbol() +"timeMS: "+time*1000+ "time: "+DateUtil.getFormattedDate("yyyyMMdd HH:mm:ss", time*1000) +" volume:"+volume);
            if (id >= 0) {
                //Parameters.symbol.get(id).getFiveSecondBars().setFiveSecOHLC(time, open, high, low, close, volume);
                Parameters.symbol.get(id).getOneMinuteBarFromRealTimeBars().setOneMinOHLCFromRealTimeBars(time, open, high, low, close, volume);
            }

        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void currentTime(long time) {
        getC().setConnectionTime(time * 1000);
        getC().setTimeDiff(System.currentTimeMillis() - time * 1000);
        //System.out.println("Time Diff for:" + c.getIp() + ":" + c.getTimeDiff());
    }

    @Override
    public void fundamentalData(int reqId, String data) {
        PrintWriter out = null;
        logger.log(Level.FINE, "Received Fundamental Data for reqid:{0}, connection:{1}", new Object[]{reqId, c.getAccountName()});
        try {
            Request r;
            r = requestDetails.get(reqId);

            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }

            String symbol = r.symbol.getDisplayname();
            String reportType = r.requestType.toString();
            System.out.println("Received report : " + reportType + " for : " + symbol);
            out = new PrintWriter("logs" + "//" + symbol + "_" + reportType + ".xml");
            out.println(data);
            out.close();
        } catch (Exception ex) {
            logger.log(Level.SEVERE, "101", ex);
        } finally {
            if (out != null) {
                out.close();
            }
        }
    }

    @Override
    public void deltaNeutralValidation(int reqId, UnderComp underComp) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void tickSnapshotEnd(int reqId) {
        try {
            if (requestDetails.containsKey(reqId)) {
                int symbolID = requestDetails.get(reqId).symbol.getSerialno();
                requestDetails.remove(reqId);
                requestDetailsWithSymbolKey.remove(symbolID);
            } else {
                //logger.log(Level.SEVERE, "Snapshot Request cannot be removed. IP:{0}, Port:{1},ClientID:{2},ReqID:{3}", new Object[]{c.getIp(), c.getPort(), c.getClientID(), reqId});
            }
            if (this.isInitialsnapShotFilled()) {
                //c.getReqHandle().getSnapShotDataSync().put(Integer.toString(c.getmSnapShotSymbolID().size()));
            }
            //System.out.println(methodName);
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void marketDataType(int reqId, int marketDataType) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void commissionReport(CommissionReport commissionReport) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        //System.out.println(methodName);
    }

    @Override
    public void error(Exception e) {
//        System.out.println(" [API.msg3] IP: " + c.getIp() + ":" + c.getPort() + ":" + c.getClientID() + "Message: " + e.toString());
        //       logger.log(Level.SEVERE, "API.msg3. IP:{0}, Port:{1},ClientID:{2},Message:{3}", new Object[]{c.getIp(), c.getPort(), c.getClientID(), e.toString()});
        tes.fireErrors(0, 0, e.toString(), getC());
    }

    @Override
    public void error(String str) {
        //     System.out.println(" [API.msg1] Connection: " + c.getIp() + ":" + c.getPort() + ":" + c.getClientID() + " Message: " + str);
        //      logger.log(Level.SEVERE, "API.msg1. IP:{0}, Port:{1},ClientID:{2},Message:{3}", new Object[]{c.getIp(), c.getPort(), c.getClientID(), str});
        tes.fireErrors(0, 0, "API.msg1: " + str, getC());
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        try {
            switch (errorCode) {
                case 300:
                    Request rd = requestDetails.get(id);
                    if (rd != null) {
                        logger.log(Level.INFO, "402,RequestID is not available for processing with IB Servers,{0}:{1}:{2}:{3}:{4},RequestTYpe={5}:RequestTime={6}:RequestID={7}:ErrorCode={8},ErrorMsg={9}",
                                new Object[]{"Unknown", rd.accountName, rd.symbol.getDisplayname(), -1, -1,
                            rd.requestType, DateUtil.getFormatedDate("HH:mm:ss", rd.requestTime, TimeZone.getTimeZone(MainAlgorithm.timeZone)), rd.requestID, errorCode, errorMsg});
                    }
                    break;

                case 321:
                    rd = requestDetails.get(id);
                    logger.log(Level.INFO, "402,Could Not Retrieve Data,{0}:{1}:{2}:{3}:{4},RequestType={5}:RequestTime={6}:RequestID={7}:ErrorCode={8},ErrorMsg={9}",
                            new Object[]{"Unknown", rd.accountName, rd.symbol.getDisplayname(), -1, -1,
                        rd.requestType, DateUtil.getFormatedDate("HH:mm:ss", rd.requestTime, TimeZone.getTimeZone(MainAlgorithm.timeZone)), rd.requestID, errorCode, errorMsg});
                    break;

                case 1102: //Reconnected
                    //MainAlgorithm.connectToTWS(c);
                    if (eClientSocket.isConnected()) {
                        setHistoricalDataFarmConnected(true);
                        logger.log(Level.INFO, "402,Reconnected with Account,{0}:{1}:{2}:{3}:{4},ErrorCode={5},ErrorMsg={6}",
                                new Object[]{"Unknown", c.getAccountName(), "Unknown", -1, -1, errorCode, errorMsg});
                    }
                    break;
                case 430://We are sorry, but fundamentals data for the security specified is not available.failed to fetch

                    String symbol = requestDetails.get(id) != null ? requestDetails.get(id).symbol.getDisplayname() : "";
                    logger.log(Level.INFO, "103,FundamentalDataNotReceived,{0}", new Object[]{symbol + delimiter + requestDetails.get(id).requestType});
                    BeanSymbol s = requestDetails.get(id).symbol;
                    //s.getFundamental().putSummary(s.getBrokerSymbol() + "," + errorMsg);
                    break;
                case 200: //No security definition has been found for the request
                    symbol = requestDetails.get(id) != null ? requestDetails.get(id).symbol.getBrokerSymbol() : "";
                    if (requestDetails.get(id) != null) {
                        requestDetails.get(id).symbol.setStatus(false);
                        TWSConnection.skipsymbol = true;
                    }
                    if (symbol.compareTo("") != 0) {
                        logger.log(Level.INFO, "103,ContractDetailsNotReceived,{0}", new Object[]{symbol});
                        requestDetails.get(id).requestStatus = EnumRequestStatus.CANCELLED;
                        TWSConnection.mTotalSymbols = TWSConnection.mTotalSymbols - 1;

                    }
                    break;
                case 1100://Connectivity between IB and TWS has been lost.
                    //this.eClientSocket.eDisconnect();
                    setHistoricalDataFarmConnected(false);
                    logger.log(Level.INFO, "103,ConnectivityLost,{0}", new Object[]{getC().getAccountName() + delimiter + errorCode + delimiter + errorMsg});
                    break;
                case 2105://A historical data farm is disconnected
                    setHistoricalDataFarmConnected(false);
                    logger.log(Level.INFO, "103,HistoricalDataFarmDisconnected,{0}", new Object[]{getC().getAccountName() + delimiter + errorCode + delimiter + errorMsg});
                    break;
                case 1101://Connectivity between IB and TWS has been restoreddata lost.*
                case 2106://A historical data farm is connected.
                    setHistoricalDataFarmConnected(true);
                    logger.log(Level.INFO, "103,HistoricalDataFarmConnected,{0}", new Object[]{getC().getAccountName() + delimiter + errorCode + delimiter + errorMsg});
                    break;
                case 502: //could not connect . Check port
                    this.eClientSocket.eDisconnect();
                    setHistoricalDataFarmConnected(false);
                    logger.log(Level.INFO, "103,CouldNotConnect,{0}", new Object[]{getC().getAccountName() + delimiter + errorCode + delimiter + errorMsg});
                    if (!this.severeEmailSent.get()) {
                        Thread t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + "could not connect. Check that TWSSend is accessible and API connections are enabled in TWSSend. ", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent.set(Boolean.TRUE);
                    }
                    break;
                case 504: //disconnected
                    //this.eClientSocket.eDisconnect();
                    setHistoricalDataFarmConnected(false);
                    logger.log(Level.INFO, "103,Disconnected,{0}", new Object[]{getC().getAccountName() + delimiter + errorCode + delimiter + errorMsg});
                    if (!this.severeEmailSent.get()) {
                        Thread t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + " disconnected. Trading Stopped on this account", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent.set(Boolean.TRUE);
                    }
                    break;
                case 326://client id is in use
                    this.eClientSocket.eDisconnect();
                    if (!this.severeEmailSent.get()) {
                        Thread t = new Thread(new Mail(getC().getOwnerEmail(), "Connection: " + getC().getIp() + ", Port: " + getC().getPort() + ", ClientID: " + getC().getClientID() + " could not connect. Client ID was already in use", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent.set(Boolean.TRUE);
                    }
                    break;
                default:
                    logger.log(Level.INFO, "103,IB Message,{0}", new Object[]{getC().getAccountName() + delimiter + id + delimiter + errorCode + delimiter + errorMsg});
                    tes.fireErrors(id, errorCode, "API.msg2: " + errorMsg, getC());
                    break;

            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void connectionClosed() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        logger.log(Level.SEVERE, "100,IBConnectionClosed", new Object[]{getC().getAccountName()});
        logger.log(Level.SEVERE, "100,IBConnectionClosed", new Object[]{stacktrace[0]});
        //System.out.println(methodName);
        this.eClientSocket.eDisconnect();
        MainAlgorithm.connectToTWS(c);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="getter-setters">
    /**
     * @return the accountIDSync
     */
    public Drop getAccountIDSync() {
        return accountIDSync;
    }

    /**
     * @param accountIDSync the accountIDSync to set
     */
    public void setAccountIDSync(Drop accountIDSync) {
        this.accountIDSync = accountIDSync;
    }

    /**
     * @return the initialsnapShotFilled
     */
    public boolean isInitialsnapShotFilled() {
        return initialsnapShotFilled;
    }

    /**
     * @param initialsnapShotFilled the initialsnapShotFilled to set
     */
    public void setInitialsnapShotFilled(boolean initialsnapShotFilled) {
        this.initialsnapShotFilled = initialsnapShotFilled;
    }

    //</editor-fold>
    /**
     * @return the recentOrders
     */
    public LimitedQueue getRecentOrders() {
        return recentOrders;
    }

    /**
     * @param recentOrders the recentOrders to set
     */
    public void setRecentOrders(LimitedQueue recentOrders) {
        this.recentOrders = recentOrders;
    }

    /**
     * @return the stopTrading
     */
    public synchronized boolean isStopTrading() {
        return stopTrading;
    }

    /**
     * @param stopTrading the stopTrading to set
     */
    public synchronized void setStopTrading(boolean stopTrading) {
        this.stopTrading = stopTrading;
    }

    /**
     * @return the historicalDataFarmConnected
     */
    public boolean isHistoricalDataFarmConnected() {
        return historicalDataFarmConnected;
    }

    /**
     * @param historicalDataFarmConnected the historicalDataFarmConnected to set
     */
    public void setHistoricalDataFarmConnected(boolean historicalDataFarmConnected) {
        this.historicalDataFarmConnected = historicalDataFarmConnected;
    }

    /**
     * @return the c
     */
    public BeanConnection getC() {
        return c;
    }

    /**
     * @param c the c to set
     */
    public void setC(BeanConnection c) {
        this.c = c;
    }

    @Override
    public void position(String account, Contract contract, int pos, double avgCost) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void positionEnd() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountSummary(int reqId, String account, String tag, String value, String currency) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void accountSummaryEnd(int reqId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void verifyMessageAPI(String apiData) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void verifyCompleted(boolean isSuccessful, String errorText) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void displayGroupList(int reqId, String groups) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void displayGroupUpdated(int reqId, String contractInfo) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
