/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.incurrency.framework.fundamental.FundamentalDataListener;
import com.ib.client.*;
import com.incurrency.RatesClient.RequestClient;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import static com.incurrency.framework.Algorithm.globalProperties;
/**
 *
 * @author admin
 */
public class TWSConnection extends Thread implements EWrapper {

    protected final static int MAX_WAIT_COUNT = 10;
    protected final static int WAIT_TIME = 1;//seconds
    public EClientSocket eClientSocket = new EClientSocket(this);
    private BeanConnection c;
    private static int mRequestId;
    public static volatile int mTotalSymbols;
    public static volatile int mTotalATMChecks;
    private ArrayList _fundamentallisteners = new ArrayList();
    private Drop accountIDSync = new Drop();
    private Drop orderIDSync=new Drop();
    private static final Logger logger = Logger.getLogger(TWSConnection.class.getName());
    private boolean initialsnapShotFilled = false; //set to true by getMktData() once the first 100 snapshot requests are out to IB
    private TradingEventSupport tes = new TradingEventSupport();
    private LimitedQueue recentOrders;
    private boolean stopTrading = false;
    boolean severeEmailSent = false;
    private static ConcurrentHashMap<Integer, Request> requestDetails = new ConcurrentHashMap<>();
    private HashMap<Integer, Request> requestDetailsWithSymbolKey = new HashMap<>();
    public int outstandingSnapshots = 0;
    private final String delimiter = "_";
    final static Object lock_request =new Object();
    private boolean historicalDataFarmConnected=true;
    public static boolean skipsymbol=false;

    
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

    public boolean connectToTWS() {
        String twsHost = c.getIp();
        int twsPort = c.getPort();
        int clientID = c.getClientID();
        if (!eClientSocket.isConnected()) {
            eClientSocket.eConnect(twsHost, twsPort, clientID);
            int waitCount = 0;
            String orderid=this.getOrderIDSync().take();
            c.getIdmanager().initializeOrderId(Integer.valueOf(orderid));
            logger.log(Level.INFO,"Next OrderID Received,{0}_{1}_{2}_{3}",new Object[]{c.getIp(),c.getPort(),c.getClientID(),orderid});
            /*
            while (!c.getIdmanager().singleton().isOrderIdInitialized() && (waitCount < MAX_WAIT_COUNT)) {
                try {
                    Thread.sleep(WAIT_TIME * 1000); // Pause for 1 second
                } catch (InterruptedException ex) {
                    logger.log(Level.INFO, "101" + "," + c.getAccountName() + "_", ex);
                }
                System.out.println("Connection waitcount:"+c.getAccountName()+":"+waitCount);
                waitCount++;
            }
           */
            if (eClientSocket.isConnected()) {
                eClientSocket.reqIds(1);
                System.out.println(">>> Connected to TWSSend with client id: " + clientID);
                //logger.log(Level.INFO, "{0},{1},TWSReceive,Connected to TWSSend,Server Version: {2}", new Object[]{"", c.getStrategy(), eClientSocket.serverVersion()});
                eClientSocket.setServerLogLevel(2);
                return true;
            } else {
                System.out.println(">>> Could not connect to TWSSend with client id: " + clientID);
                return false;
            }
        }
        return false;
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
            con.m_symbol = s.getSymbol();
            con.m_currency = s.getCurrency();
            con.m_exchange = s.getExchange();
            con.m_expiry = s.getExpiry();
            con.m_primaryExch = s.getPrimaryexchange();
            con.m_right = s.getRight();
            con.m_secType = s.getType();
            con.m_strike = s.getOption() == null ? 0 : Double.parseDouble(s.getOption());
            if (c.getReqHandle().requestContractDetailsHandle()) {
                c.getReqHandle().setContractDetailsReturned(false);
                if (c.getReqHandle().getHandle()) {
                    mRequestId = RequestIDManager.singleton().getNextRequestId();
                    //c.getmReqID().put(mRequestId, s.getSerialno());
                    synchronized(lock_request){
                    getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.CONTRACTDETAILS,EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime()));
                    }
                    eClientSocket.reqContractDetails(mRequestId, con);
                } else {
                    System.out.println("### Error getting handle while requesting market data for contract Name: " + s.getSymbol());
                }
            }
        }
    }

    public void getContractDetails(BeanSymbol s, String overrideType) {
        Contract con = new Contract();
        con.m_symbol = s.getSymbol();
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
        if (c.getReqHandle().getHandle()) {
            mRequestId = RequestIDManager.singleton().getNextRequestId();
            //c.getmReqID().put(mRequestId, s.getSerialno());
            synchronized(lock_request){
                getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.CONTRACTDETAILS,EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime()));
            }
            eClientSocket.reqContractDetails(mRequestId, con);

        } else {
            System.out.println("101,ErrorOnHandle,{0}" + s.getDisplayname());
        }
    }

    public void requestSingleSnapshot(BeanSymbol s) {
        boolean proceed = true;
        synchronized(lock_request){
        for (Map.Entry<Integer, Request> entry : getRequestDetails().entrySet()) {
            if (s.getSerialno() == entry.getValue().symbol.getSerialno() && entry.getValue().requestType.equals(EnumRequestType.SNAPSHOT)) {
                proceed = false;
                logger.log(Level.INFO, "101,ErrorSnapshotRequestExists", new Object[]{s.getDisplayname()});
            }
        }
        }
        if (proceed && c.getReqHandle().getHandle()) {
            int mRequestId = RequestIDManager.singleton().getNextRequestId();
            synchronized(lock_request){
                getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.SNAPSHOT,EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime()));
            }

            //c.getmSnapShotReqID().put(mRequestId, s.getSerialno());
            Contract con = new Contract();
            con.m_symbol = s.getSymbol();
            con.m_secType = s.getType();
            con.m_exchange = s.getExchange();
            con.m_currency = s.getCurrency();
            this.eClientSocket.reqMktData(mRequestId, con, null, true);
            logger.log(Level.FINER, "403,OneTimeSnapshotSent, {0}", new Object[]{c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
        }
    }

    public void getMktData(BeanSymbol s, Contract contract, boolean isSnap) throws Exception {
        if (!eClientSocket.isConnected()) {
            connectToTWS();
        }
        //for streaming request
        if (!isSnap) { //streaming data request
            if (c.getReqHandle().getHandle()) {
                mRequestId = RequestIDManager.singleton().getNextRequestId();
                // Store the request ID for each symbol for later use while updating the symbol table
                s.setReqID(mRequestId);
                //make snapshot/ streaming data request
                synchronized(lock_request){
                    getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.STREAMING,EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime()));
                }
                //c.getmReqID().put(mRequestId, s.getSerialno());
                //getRequestDetails().put(mRequestId, new Request(mRequestId, s, EnumRequestType.STREAMING, EnumRequestStatus.PENDING, new Date().getTime()));

                //c.getmStreamingSymbolRequestID().put(s.getSerialno(), mRequestId);
                eClientSocket.reqMktData(mRequestId, contract, null, isSnap);
                s.setDataConnectionID(Parameters.connection.indexOf(c));
                logger.log(Level.FINER, "403,MarketDataRequestSent, {0}", new Object[]{c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
            } else {
                System.out.println("101,ErrorOnHandle,{0}" + s.getDisplayname());
            }
        } else if (isSnap) {
            if (c.getReqHandle().getHandle()) {
                if (this.getRequestDetailsWithSymbolKey().containsKey(s.getSerialno())) {//if the symbol is already being serviced, 
                    if (new Date().getTime() > getRequestDetailsWithSymbolKey().get(s.getSerialno()).requestTime + 10000) { //and request is over 10 seconds seconds old
                        int origReqID = getRequestDetailsWithSymbolKey().get(s.getSerialno()).requestID;
                        c.getWrapper().eClientSocket.cancelMktData(origReqID);
                        logger.log(Level.FINEST, "403,SnapshotCancelled, {0}", new Object[]{c.getAccountName() + delimiter + s.getDisplayname() + delimiter + origReqID});
                        //there is no callback to confirm that IB processed the market data cancellation, so we will just remove from queue
                        getRequestDetailsWithSymbolKey().remove(s.getSerialno());
                        synchronized(lock_request){
                        getRequestDetails().remove(origReqID);
                        }
                        //we dont reattempt just yet to prevent a loop of attempts when IB is not throwing data for the symbol
                    }
                } else {
                    mRequestId = RequestIDManager.singleton().getNextRequestId();
                    // Store the request ID for each symbol for later use while updating the symbol table
                    s.setReqID(mRequestId);
                    synchronized(lock_request){
                        getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.SNAPSHOT,EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime()));
                    }
                    getRequestDetailsWithSymbolKey().put(s.getSerialno(), new Request(EnumSource.IB,mRequestId, s, EnumRequestType.SNAPSHOT,EnumBarSize.UNDEFINED, EnumRequestStatus.PENDING, new Date().getTime()));
                    eClientSocket.reqMktData(mRequestId, contract, null, isSnap);
                    s.setDataConnectionID(Parameters.connection.indexOf(c));
                    logger.log(Level.FINEST, "403,ContinuousSnapshotSent, {0}", new Object[]{c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});
                }
            } else {
                System.out.println("### Error getting handle while requesting snapshot data for contract " + contract.m_conId + " Name: " + s.getSymbol());
            }
        }
    }

    public void getRealTimeBars(BeanSymbol s) {
        Contract con = new Contract();
        con.m_symbol = s.getSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (!eClientSocket.isConnected()) {
            connectToTWS();
        }
        if (c.getReqHistoricalHandle().getHandle()) {
            if (c.getReqHandle().getHandle()) {
                mRequestId = RequestIDManager.singleton().getNextRequestId();

                synchronized(lock_request){
                    getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.REALTIMEBAR,EnumBarSize.FIVESECOND, EnumRequestStatus.PENDING, new Date().getTime()));
                }
                eClientSocket.reqRealTimeBars(mRequestId, con, 5, "TRADES", true); //only returns regular trading hours
                logger.log(Level.FINER, "403,RealTimeBarsRequestSent, {0}", new Object[]{c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId});

            } else {
                System.out.println("### Error getting handle while requesting market data for contract " + con.m_symbol + " Name: " + s.getSymbol());
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

        out = createOrder(id, internalOrderID, size, ordSide, notify, orderType, stage, limit, trigger, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom, stubs);

        return out;
    }

    public HashMap<Integer, Order> createOrder(int id, int internalOrderID, int size, EnumOrderSide ordSide, EnumOrderReason notify, EnumOrderType orderType, EnumOrderStage stage, double limit, double trigger, String ordValidity, String orderRef, String validAfter, String link, boolean transmit, String ocaGroup, String effectiveFrom, HashMap<Integer, Integer> stubs) {
        if (recentOrders == null) {
            recentOrders = new LimitedQueue(c.getOrdersHaltTrading());
        }

        HashMap<Integer, Order> orders = new HashMap<>();
        if ((ordSide == EnumOrderSide.BUY || ordSide == EnumOrderSide.SHORT) && stage != EnumOrderStage.AMEND && (isStopTrading() || (getRecentOrders().size() == c.getOrdersHaltTrading() && (new Date().getTime() - (Long) getRecentOrders().get(0)) < 120000))) {
            Thread t = new Thread(new Mail(c.getOwnerEmail(), "Account: " + c.getAccountName() + ", Connection: " + c.getIp() + ", Port: " + c.getPort() + ", ClientID: " + c.getClientID() + " has sent " + c.getOrdersHaltTrading() + " orders in the last two minutes. Trading halted", "Algorithm SEVERE ALERT - " + orderRef.toUpperCase()));
            t.start();
            setStopTrading(true);
            logger.log(Level.INFO, "303,StopTrading,{0}", new Object[]{c.getAccountName() + delimiter + this.getRecentOrders().size() + delimiter + DateUtil.getFormatedDate("yyyMMdd HH:mm:ss", (long) this.getRecentOrders().get(0))});
            return orders;
        } else {
            if (!Parameters.symbol.get(id).getType().equals("COMBO")) {
                Order order = createChildOrder(size, ordSide, notify, orderType, limit, trigger, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom);
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
                    Order order = createChildOrder(Math.abs(entry.getValue()) * size, subSide, notify, orderType, limitPrices.get(entry.getKey().getSerialno() - 1), trigger, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom);
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
                        Order order = createChildOrder(Math.abs(childsize), subSide, notify, orderType, 0D, 0D, ordValidity, orderRef, validAfter, link, transmit, ocaGroup, effectiveFrom);
                        orders.put(childid, order);
                    }
                }
            }
            return orders;

        }
    }

    private Order createChildOrder(int size, EnumOrderSide ordSide, EnumOrderReason notify, EnumOrderType orderType, double limit, double trigger, String ordValidity, String orderRef, String validAfter, String link, boolean transmit, String ocaGroup, String effectiveFrom) {
        Order order = new Order();
        order.m_action = (ordSide == EnumOrderSide.BUY || ordSide == EnumOrderSide.COVER || ordSide == EnumOrderSide.TRAILBUY) ? "BUY" : "SELL";
        order.m_auxPrice = trigger > 0 ? trigger : 0;
        order.m_lmtPrice = limit > 0 ? limit : 0;
        order.m_tif = ordValidity;
        order.m_goodAfterTime = effectiveFrom;

        switch (orderType) {
            case MKT:
                order.m_orderType = "MKT";
                break;
            case LMT:
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
        order.m_account = c.getAccountName().toUpperCase();
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
        logger.log(Level.FINE, "307,OrderDetails,{0}", new Object[]{c.getAccountName() + delimiter + order.m_orderRef + delimiter + order.m_action + delimiter + order.m_totalQuantity + delimiter + order.m_orderType + delimiter + order.m_lmtPrice + delimiter + order.m_auxPrice + delimiter + order.m_tif + delimiter + order.m_goodTillDate});
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
                        OrderBean ob = c.getOrders().get(orders.get(i));
                        int childid = ob.getChildSymbolID() - 1;
                        if (ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                            limitPrices.put(childid, c.getOrders().get(orders.get(i)).getFillPrice());
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
                        OrderBean ob = c.getOrders().get(orders.get(j));
                        int childid = ob.getChildSymbolID() - 1;
                        if (ob.getChildStatus().equals(EnumOrderStatus.COMPLETEFILLED)) {
                            limitPrices.put(childid, c.getOrders().get(orders.get(j)).getFillPrice());
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
        synchronized (c.lockOrderMapping) {
            orderids = c.getOrderMapping().get(new Index(strategy, internalorderid));
        }
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
            contract.m_conId = Parameters.symbol.get(id).getContractID();
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            out.add(contract);
        } else {
            for (Map.Entry<BeanSymbol, Integer> entry : Parameters.symbol.get(id).getCombo().entrySet()) { //ordering of orders and combo should be the same. This appears to be a correct assumption
                Contract contract = new Contract();
                contract.m_conId = Parameters.symbol.get(entry.getKey().getSerialno() - 1).getContractID();
                contract.m_exchange = Parameters.symbol.get(entry.getKey().getSerialno() - 1).getExchange();
                out.add(contract);

            }
        }
        return out;
    }

    public Contract createContract(BeanSymbol s) {
        Contract contract = new Contract();
        int id = TradingUtil.getIDFromSymbol(s.getSymbol(), s.getType(), s.getExpiry() == null ? "" : s.getExpiry(), s.getRight() == null ? "" : s.getRight(), s.getOption() == null ? "" : s.getOption());
        if (id >= 0) {
            contract.m_conId = Parameters.symbol.get(id).getContractID();
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            contract.m_symbol = Parameters.symbol.get(id).getSymbol();
            contract.m_exchange = Parameters.symbol.get(id).getExchange();
            contract.m_primaryExch = Parameters.symbol.get(id).getPrimaryexchange();
            contract.m_currency = Parameters.symbol.get(id).getCurrency();
        } else {
            logger.log(Level.INFO, "101,ErrorSymbolIDNotFound,{0}", new Object[]{s.getDisplayname()});
        }
        return contract;
    }

    public ArrayList<Integer> placeOrder(BeanConnection c, OrderEvent e, HashMap<Integer, Order> orders) {
        ArrayList<Integer> orderids = new ArrayList<>();
        int symbolID = e.getSymbolBean().getSerialno();
        EnumOrderSide side = e.getSide();
        EnumOrderReason notify = e.getReason();
        EnumOrderStage stage = e.getOrderStage();
        int internalOrderID = e.getInternalorder();
        int internalOrderIDEntry = e.getInternalorderentry();
        if (!orders.isEmpty()) {
            orderids = placeOrder(c, symbolID, side, notify, stage, orders, internalOrderID, internalOrderIDEntry, e.getTag());
        }
        return orderids;
    }

    public synchronized ArrayList<Integer> placeOrder(BeanConnection c, int symbolID, EnumOrderSide side, EnumOrderReason reason, EnumOrderStage stage, HashMap<Integer, Order> orders, int internalOrderID, int internalOrderIDEntry, String tag) {
        ArrayList<Integer> orderids = new ArrayList<>();
        if ((side == EnumOrderSide.BUY || side == EnumOrderSide.SHORT) && stage != EnumOrderStage.AMEND && (isStopTrading() || (getRecentOrders().size() == c.getOrdersHaltTrading() && (new Date().getTime() - (Long) getRecentOrders().get(0)) < 120000))) {
            setStopTrading(false);
            Thread t = new Thread(new Mail(c.getOwnerEmail(), "Account: " + c.getAccountName() + ", Connection: " + c.getIp() + ", Port: " + c.getPort() + ", ClientID: " + c.getClientID() + " has sent " + c.getOrdersHaltTrading() + " orders in the last two minutes. Trading halted", "Algorithm SEVERE ALERT - " + orders.get(0).m_orderRef.toUpperCase()));
            t.start();
            return orderids;
        } else {
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
                    ob.setOrderType(order.m_orderType);
                    if (Parameters.symbol.get(parentid).getCombo().isEmpty()) {//single leg order
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
                        ob.setInternalOrderID(internalOrderID);
                        ob.setInternalOrderIDEntry(internalOrderIDEntry);

                        ArrayList<Contract> contracts = c.getWrapper().createContract(ob.getChildSymbolID() - 1);
                        eClientSocket.placeOrder(mOrderID, contracts.get(0), order);
                        if (stage != EnumOrderStage.AMEND) {
                            getRecentOrders().add(new Date().getTime());
                        }
                        logger.log(Level.INFO, "304,OrderPlacedWithBroker,{0}", new Object[]{c.getAccountName() + delimiter + order.m_orderRef + delimiter + Parameters.symbol.get(ob.getParentSymbolID() - 1).getDisplayname() + delimiter + Parameters.symbol.get(ob.getChildSymbolID() - 1).getDisplayname() + delimiter + mOrderID + delimiter + ob.getParentOrderSide() + delimiter + order.m_totalQuantity + delimiter + order.m_orderType + delimiter + order.m_lmtPrice + delimiter + order.m_auxPrice + delimiter + order.m_tif + delimiter + order.m_goodTillDate});
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
                                    if (tag.equals("STUBCOMPLETE")) {
                                        if (entry2.getValue() > 0) {
                                            ob.setChildOrderSide(side);
                                        } else {
                                            ob.setChildOrderSide(switchSide(side));
                                        }
                                    } else if (tag.equals("STUBREDUCE")) {
                                        if (entry2.getValue() < 0) {
                                            ob.setChildOrderSide(side);
                                        } else {
                                            ob.setChildOrderSide(switchSide(side));
                                        }
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

                        if (orders.size() > 1 && !comboOrderMapsUpdated) {
                            ArrayList<Integer> temp = new ArrayList<>();
                            for (i = 0; i < orders.size(); i++) {
                                temp.add(mOrderID + i);
                            }
                            synchronized (c.lockOrderMapping) {//update ordermapping for first time combo order
                                comboOrderMapsUpdated = true;
                                c.getOrderMapping().put(new Index(order.m_orderRef, internalOrderID), temp);
                            }
                        }
                        ArrayList<Contract> contracts = c.getWrapper().createContract(ob.getChildSymbolID() - 1);
                        eClientSocket.placeOrder(mOrderID, contracts.get(0), order);
                        if (stage != EnumOrderStage.AMEND) {
                            getRecentOrders().add(new Date().getTime());
                        }
                        logger.log(Level.INFO, "304,OrderPlacedWithBroker,{0}", new Object[]{c.getAccountName() + delimiter + order.m_orderRef + delimiter + Parameters.symbol.get(ob.getParentSymbolID() - 1).getDisplayname() + delimiter + Parameters.symbol.get(ob.getChildSymbolID() - 1).getDisplayname() + delimiter + mOrderID + delimiter + ob.getParentOrderSide() + delimiter + order.m_totalQuantity + delimiter + order.m_orderType + delimiter + order.m_lmtPrice + delimiter + order.m_auxPrice + delimiter + order.m_tif + delimiter + order.m_goodTillDate + delimiter});
                        orderids.add(mOrderID);
                    }
                }
            }
        }
        return orderids;
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
        synchronized(lock_request){
        for (Request r : getRequestDetails().values()) {
            if (r.requestType.equals(EnumRequestType.STREAMING) && r.symbol.getSerialno() == s.getSerialno()) {
                reqid = r.requestID;
            }
        }
        }
        if (reqid >= 0) {//original: streaming market data
            eClientSocket.cancelMktData(reqid);
            synchronized(lock_request){
                getRequestDetails().remove(reqid);
            }
            //c.getmReqID().remove(reqid);
        }
        if (reqid == -1) {
            //logger.log(Level.SEVERE, "Unable to cancel data request for symbol: {0}", new Object[]{s.getSymbol() + "-" + s.getType() + "-" + s.getExchange()});
        }
    }

    public void cancelOrder(BeanConnection c, int orderID) {
        ArrayList<Integer> linkedorderids = TradingUtil.getLinkedOrderIds(orderID, c);
        for (int orderid : linkedorderids) {
            switch (c.getOrders().get(orderid).getChildStatus()) {
                case SUBMITTED:
                case ACKNOWLEDGED:
                    c.getOrders().get(orderid).setCancelRequested(true);
                    this.eClientSocket.cancelOrder(orderid);
                    //handle cancellations that are not successful
                    logger.log(Level.INFO, "304,CancellationPlacedWithBroker,{0}", new Object[]{c.getAccountName() + delimiter + c.getOrders().get(orderID).getOrderReference() + delimiter + Parameters.symbol.get(c.getOrders().get(orderID).getParentSymbolID() - 1).getDisplayname() + delimiter + Parameters.symbol.get(c.getOrders().get(orderID).getChildSymbolID() - 1).getDisplayname() + delimiter + orderID});
                    break;
            }
        }
    }

    public void requestFundamentalData(BeanSymbol s, String reportType) {
        Contract con = new Contract();
        con.m_symbol = s.getSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (c.getReqHandle().getHandle()) {
            mRequestId = RequestIDManager.singleton().getNextRequestId();
            synchronized(lock_request){
                getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.valueOf(reportType.toUpperCase()), EnumBarSize.UNDEFINED,EnumRequestStatus.PENDING, new Date().getTime()));
            }
            s.getFundamental().setSnapshotRequestID(mRequestId);
            eClientSocket.reqFundamentalData(mRequestId, con, reportType);
        }
    }

    public void cancelFundamentalData(int reqId) {
        eClientSocket.cancelFundamentalData(reqId);
    }
    
    public static void requestCassandraBars(BeanSymbol s, EnumBarSize periodicity) {
        String symbol = null;
        symbol = s.getDisplayname().toLowerCase() + "_" + s.getType();
        String[] parameters = null;
        if (s.getType().equals("FUT")) {
            symbol = symbol + "_" + s.getExpiry();
        } else if (s.getType().equals("OPT")) {
            symbol = symbol + "_" + s.getExpiry() + "_" + s.getRight() + "_" + s.getOption();
        }
        String barSize = periodicity.toString().toLowerCase();
/* TBD - this commented section needs to be reworked
        String[] metricDetails = MainAlgorithm.globalVariables.getProperty(barSize).toString().toLowerCase().split(",");
        String metric[] = new String[metricDetails.length - 1];
        String baseMetric = metricDetails[0];
        for (int i = 0; i < metric.length; i++) {
            metric[i] = baseMetric + "." + metricDetails[i + 1];
        }
        String concatMetrics = null;
        for (String m : metric) {
            if (concatMetrics == null) {
                concatMetrics = m;
            } else {
                concatMetrics = concatMetrics + "," + m;
            }
        }
        String path = MainAlgorithm.globalVariables.getProperty("path").toString().toLowerCase();
        RequestClient rc=RequestClient.singleton(path);
        while(RequestClient.isAvailableForNewRequest()){
        rc.sendRequest("backfill", s, new String[]{barSize.toString()}, concatMetrics,null,false);
        }
     */
    }

    public void requestDailyBar(BeanSymbol s, String duration) {
        Contract con = new Contract();
        con.m_symbol = s.getSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (!eClientSocket.isConnected()) {
            connectToTWS();
        }
        if (c.getReqHistoricalHandle().getHandle()) {
            if (c.getReqHandle().getHandle()) {
                mRequestId = RequestIDManager.singleton().getNextRequestId();
                //c.getmReqID().put(mRequestId, s.getSerialno());
                synchronized(lock_request){
                    getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.HISTORICAL,EnumBarSize.DAILY, EnumRequestStatus.PENDING, new Date().getTime()));
                }
                String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", Parameters.connection.get(0).getConnectionTime());
                String endDateStr = currDateStr + " " + "23:30:00";
                //System.out.println(s.getDisplayname()+":"+mRequestId+":"+"DailyBars");
                eClientSocket.reqHistoricalData(mRequestId, con, endDateStr, duration, "1 day", "TRADES", 1, 2);
                logger.log(Level.INFO, "403,HistoricalDataRequestSent,{0}", new Object[]{c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + duration + delimiter + "1 day"});
            } else {
                System.out.println("### Error getting handle while requesting market data for contract " + con.m_symbol + " Name: " + s.getSymbol());
            }
        }
    }

    public void requestOpenOrders() {
        eClientSocket.reqOpenOrders();
    }

    public void requestExecutionDetails(ExecutionFilter filter) {
        mRequestId = RequestIDManager.singleton().getNextRequestId();
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
        con.m_symbol = s.getSymbol();
        con.m_currency = s.getCurrency();
        con.m_exchange = s.getExchange();
        con.m_expiry = s.getExpiry();
        con.m_primaryExch = s.getPrimaryexchange();
        con.m_right = s.getRight();
        con.m_secType = s.getType();
        if (s.getType().equals("FUT") || s.getType().equals("OPT")) {
            con.m_includeExpired = true;
        }
        if (!eClientSocket.isConnected()) {
            connectToTWS();
        }
        if (c.getReqHistoricalHandle().getHandle()) {
            if (c.getReqHandle().getHandle()) {
                mRequestId = RequestIDManager.singleton().getNextRequestId();
                switch (barSize) {
                    case "1 day":
                        synchronized(lock_request){
                            getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.HISTORICAL, EnumBarSize.DAILY,EnumRequestStatus.PENDING, new Date().getTime()));
                        }
                        break;
                    case "1 min":
                        synchronized(lock_request){
                            getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.HISTORICAL,EnumBarSize.ONEMINUTE, EnumRequestStatus.PENDING, new Date().getTime()));
                        }
                        break;
                    case "1 secs":
                        synchronized(lock_request){
                            getRequestDetails().put(mRequestId, new Request(EnumSource.IB,mRequestId, s, EnumRequestType.HISTORICAL,EnumBarSize.ONESECOND, EnumRequestStatus.PENDING, new Date().getTime()));
                        }
                        break;
                    default:
                        break;
                }
                //System.out.println(s.getDisplayname()+":"+mRequestId+":"+barSize);
                eClientSocket.reqHistoricalData(mRequestId, con, endDate, duration, barSize, "TRADES", 1, 2);
                logger.log(Level.INFO, "403,HistoricalDataRequestSent,{0}", new Object[]{c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + duration + delimiter + barSize+delimiter+endDate});
                //System.out.println("HistoricalDataRequestSent"+c.getAccountName() + delimiter + s.getDisplayname() + delimiter + mRequestId + delimiter + duration + delimiter + barSize+delimiter+endDate);

                
            } else {
                System.out.println("### Error getting handle while requesting market data for contract " + con.m_symbol + " Name: " + s.getSymbol());
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
            int serialno = getRequestDetails().get(tickerId) != null ? (int) getRequestDetails().get(tickerId).symbol.getSerialno() : 0;
            int id = serialno - 1;
            Request r;
            synchronized(lock_request){
                r=getRequestDetails().get(tickerId);
            }
            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            //logger.log(Level.INFO,"request id:{0},id: {1},price:{2}",new Object[]{tickerId,id,price});
            //Parameters.updateSymbol(id, field, price);
            if (id >= 0) {
                if (field == 1) {
                    Parameters.symbol.get(id).setBidPrice(price);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Bid," + price);
                    }
                    tes.fireBidAskChange(id);
                } else if (field == 2) {
                    Parameters.symbol.get(id).setAskPrice(price);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Ask," + price);
                    }
                    tes.fireBidAskChange(id);
                } else if (field == 4) {
                    double prevLastPrice = Parameters.symbol.get(id).getPrevLastPrice() == 0 ? price : Parameters.symbol.get(id).getPrevLastPrice();
                    Parameters.symbol.get(id).setPrevLastPrice(prevLastPrice);
                    Parameters.symbol.get(id).setLastPrice(price);
                    Parameters.symbol.get(id).setLastPriceTime(System.currentTimeMillis());
                    Parameters.symbol.get(id).getTradedPrices().add(price);
                    Parameters.symbol.get(id).getTradedDateTime().add(System.currentTimeMillis());
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Trade," + price);
                    }
                    tes.fireTradeEvent(id, com.ib.client.TickType.LAST);
                    if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                        Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(new Date().getTime(), com.ib.client.TickType.LAST, String.valueOf(price));
                    }
                } else if (field == 6) {
                    Parameters.symbol.get(id).setHighPrice(price);
                } else if (field == 7) {
                    Parameters.symbol.get(id).setLowPrice(price);
                } else if (field == 9) {
                    Parameters.symbol.get(id).setClosePrice(price);
                    tes.fireTradeEvent(id, com.ib.client.TickType.CLOSE);
                } else if (field == 14) {
                    Parameters.symbol.get(id).setOpenPrice(price);
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void tickSize(int tickerId, int field, int size) {
        try {
            int serialno = getRequestDetails().get(tickerId) != null ? (int) getRequestDetails().get(tickerId).symbol.getSerialno() : 0;
            Request r; 
                    synchronized(lock_request){
                        r= getRequestDetails().get(tickerId);
                    }
            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno - 1;
            if (id >= 0) {
                if (field == 0) {
                    Parameters.symbol.get(id).setBidSize(size);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "BidSize," + size);
                    }
                } else if (field == 3) {
                    Parameters.symbol.get(id).setAskSize(size);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "AskSize," + size);
                    }
                } else if (field == 5) {
                    //Parameters.symbol.get(id).setLastSize(size);
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "LastSize," + size);
                    }

                } else if (field == 8) {
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
                    Parameters.symbol.get(id).setVolume(size);
                    tes.fireTradeEvent(id, com.ib.client.TickType.LAST_SIZE);
                    tes.fireTradeEvent(id, com.ib.client.TickType.VOLUME);
                    if (Parameters.symbol.get(id).getIntraDayBarsFromTick() != null) {
                        Parameters.symbol.get(id).getIntraDayBarsFromTick().setOHLCFromTick(new Date().getTime(), com.ib.client.TickType.VOLUME, String.valueOf(calculatedLastSize));
                    }
                    if (MainAlgorithm.getCollectTicks()) {
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Volume," + size);
                        TradingUtil.writeToFile("tick_" + Parameters.symbol.get(id).getSymbol() + ".csv", "Calculated LastSize," + calculatedLastSize);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void tickOptionComputation(int tickerId, int field, double impliedVol, double delta, double optPrice, double pvDividend, double gamma, double vega, double theta, double undPrice) {
        int serialno = getRequestDetails().get(tickerId) != null ? (int) getRequestDetails().get(tickerId).symbol.getSerialno() : 0;
        Request r;
        synchronized(lock_request){
            r= getRequestDetails().get(tickerId);
        }
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
    }

    @Override
    public void tickGeneric(int tickerId, int tickType, double value) {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
    }

    @Override
    public void tickString(int tickerId, int tickType, String value) {
        /*
         StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
         StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
         String methodName = e.getMethodName();
         //System.out.println(methodName);
         */
        //System.out.println("TickType: "+tickType+"Value: "+value );
        //By default, tickstring returns value for ticktype=45 = last timestamp.
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
        String orderRef = c.getOrders() == null ? c.getOrders().get(orderId).getOrderReference() : "NA";
        //logger.log(Level.FINE, "{0},{1},TWSReceive,orderStatus, OrderID:{2},Status:{3}.Filled:{4},Remaining:{5},AvgFillPrice:{6},LastFillPrice:{7}", new Object[]{c.getAccountName(), orderRef, orderId, status, filled, remaining, avgFillPrice, lastFillPrice});
        tes.fireOrderStatus(c, orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
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
        //System.out.println("orderid:"+orderId);
      //  c.getIdmanager().initializeOrderId(orderId);
        this.getOrderIDSync().put(String.valueOf(orderId));
    }

    @Override
    public void contractDetails(int reqId, ContractDetails contractDetails) {
        try {
            int serialno = getRequestDetails().get(reqId) != null ? (int) getRequestDetails().get(reqId).symbol.getSerialno() : 0;

            Request r;
            synchronized(lock_request){
                r= getRequestDetails().get(reqId);
            }
            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno - 1;
            logger.log(Level.INFO, "103,ContractDetailsReceived,{0}", new Object[]{Parameters.symbol.get(id).getDisplayname() + delimiter + String.valueOf(contractDetails.m_summary.m_conId) + delimiter + contractDetails.m_minTick});
            Parameters.symbol.get(id).setTickSize(contractDetails.m_minTick);
            if (Parameters.symbol.get(id).getType().compareTo("OPT") == 0 && Parameters.symbol.get(id).getOption() == null) {
                //this request is checking for ATM strike
                //get underlying id
                int underlyingid = TradingUtil.getIDFromSymbol(Parameters.symbol.get(id).getSymbol(), "STK", "", "", "") >= 0 ? TradingUtil.getIDFromSymbol(Parameters.symbol.get(id).getSymbol(), "STK", "", "", "") : TradingUtil.getIDFromSymbol(Parameters.symbol.get(id).getSymbol(), "IND", "", "", "");
                if (underlyingid >= 0) {
                    BeanSymbol underlyingSymbol = Parameters.symbol.get(underlyingid);
                    double oldATM = underlyingSymbol.getAtmStrike();
                    double closePrice = underlyingSymbol.getClosePrice();
                    double newATM = oldATM == 0 ? contractDetails.m_summary.m_strike : Math.abs(closePrice - oldATM) < Math.abs(closePrice - contractDetails.m_summary.m_strike) ? oldATM : contractDetails.m_summary.m_strike;
                    underlyingSymbol.setAtmStrike(newATM);
                    //logger.log(Level.INFO, "{0},{1},TWSReceive,new ATM: {2}, ClosePrice:{3}, NIFTY id: {4}", new Object[]{c.getAccountName(), "", newATM, closePrice, underlyingid});
                    //logger.log(Level.INFO, "{0},{1},TWSReceive,Request ID: {2}, Strike:{3}", new Object[]{c.getAccountName(), "", reqId, Parameters.symbol.get(underlyingid).getAtmStrike()});
                } else {
                    //logger.log(Level.SEVERE, "Unable to get underlying of option");
                }
            } else {
                Parameters.symbol.get(id).setContractID(contractDetails.m_summary.m_conId);
                Parameters.symbol.get(id).setStatus(true);
                TWSConnection.mTotalSymbols = TWSConnection.mTotalSymbols - 1;
            }
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
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        if (c.getOrders().get(execution.m_orderId) != null) {
            if (c.getOrders().get(execution.m_orderId).getParentOrderSize() - execution.m_cumQty == 0) {
                tes.fireOrderStatus(c, execution.m_orderId, "Filled", execution.m_cumQty, c.getOrders().get(execution.m_orderId).getParentOrderSize() - execution.m_cumQty, execution.m_avgPrice, execution.m_permId, reqId, 0, execution.m_clientId, "execDetails");
            } else {
                tes.fireOrderStatus(c, execution.m_orderId, "Submitted", execution.m_cumQty, c.getOrders().get(execution.m_orderId).getParentOrderSize() - execution.m_cumQty, execution.m_avgPrice, execution.m_permId, reqId, 0, execution.m_clientId, "execDetails");
            }
            //public void fireOrderStatus(BeanConnection c, int orderId, String status, int filled, int remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld) {

//        tes.fireOrderStatus(c, execution.m_orderId, "SUBMITTED", reqId, reqId, reqId, reqId, reqId, mTotalSymbols, reqId, methodName);
            //logger.log(Level.FINE, "{0},{1},TWSReceive,executionDetails,orderid:{2}, m_shares:{3}, cumQuantity: {4}, avgFillPrice: {5}, permid: {6}, parentId: {7}, lastfillprice: {8}, clientid: {9}, whyheld:{10}", new Object[]{c.getAccountName(), execution.m_orderId, execution.m_orderId, execution.m_shares, execution.m_cumQty, execution.m_avgPrice, execution.m_permId, execution.m_execId, execution.m_price, execution.m_clientId, "TBD"});

        }
        //     tes.fireOrderStatus(c, orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld);
        //      tes.fireOrderStatus(c, reqId, "", , reqId, reqId, reqId, reqId, mTotalSymbols, reqId, methodName);
        //System.out.println(methodName);
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
            int serialno=-1;
            synchronized(lock_request){
                serialno=getRequestDetails().get(reqId) != null ? (int) getRequestDetails().get(reqId).symbol.getSerialno() : 0;
            }
            Request r;
            synchronized(lock_request){
                r=getRequestDetails().get(reqId);
            }
            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            } else {
                //logger.log(Level.INFO, "Request ID not found, requestID:{0},serialno:{1}", new Object[]{reqId, serialno});
            }
            int id = serialno - 1;
            if(getRequestDetails().get(reqId)==null){
                //System.out.println("NULL");
                for(Integer r1:getRequestDetails().keySet()){
                    //System.out.print(r1+",");
                }
            }

            if (Parameters.symbol.size() > id) {//only proceed if the id is within the list of symbols being elibile for trading
                boolean realtimebars = false;
                //add check to confirm that getRequestDetails().get(reqId) is not null
                //System.out.println(reqId);
                if (date.toLowerCase().contains("finished".toLowerCase())) {
                    switch (getRequestDetails().get(reqId).barSize) {
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
//        System.out.println("Symbol:"+ Parameters.symbol.get(id).getSymbol()+":"+DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", Long.parseLong(date)*1000));
                switch (getRequestDetails().get(reqId).barSize) {
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
                BeanOHLC ohlc=new BeanOHLC();
                switch (getRequestDetails().get(reqId).barSize) {
                    case DAILY:
                        ohlc = new BeanOHLC(DateUtil.parseDate("yyyyMMdd", date).getTime(), open, high, low, close, volume, EnumBarSize.DAILY);
                        break;
                    case ONEMINUTE:
                        ohlc = new BeanOHLC(Long.valueOf(date)*1000, open, high, low, close, volume, EnumBarSize.ONEMINUTE);
                        break;
                    case ONESECOND:
                        ohlc = new BeanOHLC(Long.valueOf(date)*1000, open, high, low, close, volume, EnumBarSize.ONESECOND);
                        //System.out.println("Main:"+DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", ohlc.getOpenTime())+":"+ohlc.getClose());
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
            int serialno = getRequestDetails().get(reqId) != null ? (int) getRequestDetails().get(reqId).symbol.getSerialno() : 0;
            Request r;
            synchronized(lock_request){
                r=getRequestDetails().get(reqId);
            }
            if (r != null) {
                r.requestStatus = EnumRequestStatus.SERVICED;
            }
            int id = serialno - 1;
            //System.out.println("RealTime Bar: Symbol:"+Parameters.symbol.get(id).getSymbol() +"timeMS: "+time*1000+ "time: "+DateUtil.getFormatedDate("yyyyMMdd HH:mm:ss", time*1000) +" volume:"+volume);
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
        c.setConnectionTime(time * 1000);
        c.setTimeDiff(System.currentTimeMillis() - time * 1000);
        //System.out.println("Time Diff for:" + c.getIp() + ":" + c.getTimeDiff());
    }

    @Override
    public void fundamentalData(int reqId, String data) {
        Request r;
        synchronized(lock_request){
            r=getRequestDetails().get(reqId);
        }
        if (r != null) {
            r.requestStatus = EnumRequestStatus.SERVICED;
        }
        PrintWriter out = null;
        try {
            StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
            StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
            String methodName = e.getMethodName();
            out = new PrintWriter("snapshot.xml");
            out.println(data);
            out.close();
        } catch (FileNotFoundException ex) {
            logger.log(Level.INFO, "101", ex);
        } finally {
            out.close();
        }

        File fXmlFile = new File("snapshot.xml");
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
            try {
                Document doc = dBuilder.parse(fXmlFile);
                doc.getDocumentElement().normalize();
                //System.out.println("Root element :" + doc.getDocumentElement().getNodeName());

                NodeList nList = doc.getElementsByTagName("CoGeneralInfo");
                for (int temp = 0; temp < nList.getLength(); temp++) {
                    Node nNode = nList.item(temp);
                    if (nNode.getNodeType() == Node.ELEMENT_NODE) {
                        Element eElement = (Element) nNode;
                        String symbol = getRequestDetails().get(reqId).symbol.getSymbol();
                        String sharesOutstanding = eElement.getElementsByTagName("SharesOut").item(0).getTextContent();
                        String floatShares = ((Element) eElement.getElementsByTagName("SharesOut").item(0)).getAttribute("TotalFloat");
                        String effectiveDate = ((Element) eElement.getElementsByTagName("SharesOut").item(0)).getAttribute("Date");
                        TradingUtil.writeToFile("Outstanding Shares.csv", effectiveDate + "," + symbol + "," + sharesOutstanding + "," + floatShares);
                        //reqidFundamentalSnapshot.get(reqId).getFundamental().putSummary("");
                    }
                }

            } catch (SAXException | IOException ex) {
                logger.log(Level.INFO, "101", ex);
            }
        } catch (ParserConfigurationException ex) {
            logger.log(Level.INFO, "101", ex);
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
            if (getRequestDetails().containsKey(reqId)) {
                int symbolID = getRequestDetails().get(reqId).symbol.getSerialno();
                getRequestDetails().remove(reqId);
                getRequestDetailsWithSymbolKey().remove(symbolID);
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
        tes.fireErrors(0, 0, e.toString(), c);
    }

    @Override
    public void error(String str) {
        //     System.out.println(" [API.msg1] Connection: " + c.getIp() + ":" + c.getPort() + ":" + c.getClientID() + " Message: " + str);
        //      logger.log(Level.SEVERE, "API.msg1. IP:{0}, Port:{1},ClientID:{2},Message:{3}", new Object[]{c.getIp(), c.getPort(), c.getClientID(), str});
        tes.fireErrors(0, 0, "API.msg1: " + str, c);
    }

    @Override
    public void error(int id, int errorCode, String errorMsg) {
        try {
            switch (errorCode) {
                case 430://We are sorry, but fundamentals data for the security specified is not available.failed to fetch

                    String symbol = getRequestDetails().get(id) != null ? getRequestDetails().get(id).symbol.getDisplayname() : "";
                    logger.log(Level.INFO, "103,FundamentalDataNotReceived,{0}", new Object[]{symbol});
                    BeanSymbol s = getRequestDetails().get(id).symbol;
                    s.getFundamental().putSummary(s.getSymbol() + "," + errorMsg);
                    break;
                case 200: //No security definition has been found for the request
                    symbol = getRequestDetails().get(id) != null ? getRequestDetails().get(id).symbol.getSymbol() : "";
                    getRequestDetails().get(id).symbol.setStatus(false);
                    if (symbol.compareTo("") != 0) {
                        logger.log(Level.INFO, "103,ContractDetailsNotReceived,{0}", new Object[]{symbol});
                        requestDetails.get(id).requestStatus = EnumRequestStatus.CANCELLED;
                        TWSConnection.mTotalSymbols = TWSConnection.mTotalSymbols - 1;

                    }
                    break;
                case 502: //could not connect . Check port
                    if (!this.severeEmailSent) {
                        Thread t = new Thread(new Mail(c.getOwnerEmail(), "Connection: " + c.getIp() + ", Port: " + c.getPort() + ", ClientID: " + c.getClientID() + "could not connect. Check that TWSSend is accessible and API connections are enabled in TWSSend. ", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent = true;
                    }
                    break;
                case 504: //disconnected
                    if (!this.severeEmailSent) {
                        Thread t = new Thread(new Mail(c.getOwnerEmail(), "Connection: " + c.getIp() + ", Port: " + c.getPort() + ", ClientID: " + c.getClientID() + " disconnected. Trading Stopped on this account", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent = true;
                    }
                    break;
                case 326://client id is in use
                    if (!this.severeEmailSent) {
                        Thread t = new Thread(new Mail(c.getOwnerEmail(), "Connection: " + c.getIp() + ", Port: " + c.getPort() + ", ClientID: " + c.getClientID() + " could not connect. Client ID was already in use", "Algorithm SEVERE ALERT"));
                        t.start();
                        this.severeEmailSent = true;
                    }
                    break;
                default:
                    break;

            }
            tes.fireErrors(id, errorCode, "API.msg2: " + errorMsg, c);
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    @Override
    public void connectionClosed() {
        StackTraceElement[] stacktrace = Thread.currentThread().getStackTrace();
        StackTraceElement e = stacktrace[1];//coz 0th will be getStackTrace so 1st
        String methodName = e.getMethodName();
        logger.log(Level.SEVERE, "100,IBConnectionClosed", new Object[]{c.getAccountName()});
        //System.out.println(methodName);
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
     * @return the requestDetails
     */
    public static ConcurrentHashMap<Integer, Request> getRequestDetails() {
        return requestDetails;
    }

    /**
     * @param requestDetails the requestDetails to set
     */
    public static void setRequestDetails(ConcurrentHashMap<Integer, Request> requestDetails) {
        TWSConnection.requestDetails = requestDetails;
    }

    /**
     * @return the requestDetailsWithSymbolKey
     */
    public HashMap<Integer, Request> getRequestDetailsWithSymbolKey() {
        return requestDetailsWithSymbolKey;
    }

    /**
     * @param requestDetailsWithSymbolKey the requestDetailsWithSymbolKey to set
     */
    public void setRequestDetailsWithSymbolKey(HashMap<Integer, Request> requestDetailsWithSymbolKey) {
        this.requestDetailsWithSymbolKey = requestDetailsWithSymbolKey;
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
     * @return the orderIDSync
     */
    public Drop getOrderIDSync() {
        return orderIDSync;
    }

    /**
     * @param orderIDSync the orderIDSync to set
     */
    public void setOrderIDSync(Drop orderIDSync) {
        this.orderIDSync = orderIDSync;
    }
}
