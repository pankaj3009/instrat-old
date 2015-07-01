/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.google.common.collect.HashMultimap;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;

/**
 *
 * @author admin
 */
public class BeanConnection implements Serializable, ReaderWriterInterface {
    private final static Logger logger = Logger.getLogger(BeanConnection.class.getName());

    public static final String PROP_ACCOUNT_NAME = "accountName";

    private String ip;
    private Integer port;
    private Integer clientID;
    private String purpose;
    private Integer rtMessageLimit; //x messages per second
    private Integer histMessageLimit; //one message per x seconds
    private Integer tickersLimit;
    private String strategy;
    private transient TWSConnection wrapper;
    private transient RequestIDManager idmanager;
    private long timeDiff;
    private transient ReqHandle reqHandle = new ReqHandle();
    private transient ReqHandleHistorical reqHistoricalHandle;
    //private transient HashMap mReqID = new HashMap();
    //private transient HashMap<Integer, Integer> mStreamingSymbolRequestID = new HashMap();
    //private transient HashMap<Integer, Integer> mSnapShotReqID = new HashMap(); // this holds reqID as key
    //private transient HashMap<Integer, Integer> mStreamingCancellableReqID = new HashMap();// this holds reqid that needs to be cancelled once data is retrieved
    //private transient HashMap<Integer, RequestID> mSnapShotSymbolID = new HashMap(); //this holds symbolID as key
    //private transient HashMap mRealTimeBarsReqID = new HashMap();
    private transient Long connectionTime;
    private HashMap<Integer, OrderBean> orders = new HashMap<>(); //holds map of orderid and order object
    private HashMap<Index, ArrayList<SymbolOrderMap>> ordersSymbols = new HashMap<>(); //holds map of <strategy,symbol> and a list of all open orders against the index.
    private HashMap<Index, BeanPosition> Positions = new HashMap<>(); //holds map of <symbol, strategy> and system position.
    private HashMap<Integer, BeanOrderInformation> ordersToBeCancelled = new HashMap(); //holds the orderid as integer
    private HashMap<Integer, BeanOrderInformation> ordersToBeFastTracked = new HashMap(); // holds the orderid as integer.
    private HashMap<Long, OrderEvent> ordersToBeRetried = new HashMap(); // holds the current system time and order event that needs to be retried. Orders are retried after 60sec
    private ArrayList<Integer> ordersMissed = new ArrayList();
    private ArrayList<Integer> ordersInProgress = new ArrayList();
    private String accountName;
    private PropertyChangeSupport propertySupport;
    private double pnlByAccount = 0;
    private HashMap<String, Double> pnlByStrategy = new HashMap<>(); //holds pnl by each strategy.
    private HashMap<Index, Double> pnlBySymbol = new HashMap<>(); //holds pnl by each strategy.
    private HashMap<String, Double> maxpnlByStrategy = new HashMap<>(); //holds pnl by each strategy.
    private HashMap<String, Double> minpnlByStrategy = new HashMap<>(); //holds pnl by each strategy.
    private int ordersHaltTrading=10;
    private String ownerEmail;
    private  HashMultimap <Index, BeanOrderInformation> activeOrders = HashMultimap.create(); //holds the symbol id and corresponding order information
    private HashMap<Index,ArrayList<Integer>> orderMapping=new HashMap<>(); //internal order id mapped to arraylist of IB orders
    
    final Object lockPNLStrategy=new Object();
    final Object lockActiveOrders=new Object();  
    final Object lockOrderMapping=new Object();
    final Object lockOrdersToBeCancelled=new Object();
    final Object lockOrders=new Object();
    
    public BeanConnection() {
        idmanager = new RequestIDManager();
        propertySupport = new PropertyChangeSupport(this);
    }

    public BeanConnection(String ip, Integer port, Integer clientID, String purpose, Integer rtMessageLimit, Integer histMessageLimit, Integer tickersLimit, String strategy, int ordersHaltTrading, String ownerEmail) {
        this.ip = ip;
        this.port = port;
        this.clientID = clientID;
        this.purpose = purpose;
        this.rtMessageLimit = rtMessageLimit;
        this.histMessageLimit = histMessageLimit;
        this.tickersLimit = tickersLimit;
        this.strategy = strategy;
        this.ordersHaltTrading=ordersHaltTrading;
        this.ownerEmail=ownerEmail;
    }

    public BeanConnection(String[] input) {
        try{
        idmanager = new RequestIDManager();
        propertySupport = new PropertyChangeSupport(this);
        this.ip = input[0];
        this.port = input[1].equals("")?7496:Integer.parseInt(input[1]);
        this.clientID = input[2].equals("")?0:Integer.parseInt(input[2]);
        this.purpose = input[3];
        this.rtMessageLimit = input[4].equals("")?48:Integer.parseInt(input[4]);
        this.histMessageLimit = input[5].equals("")?10:Integer.parseInt(input[5]);
        this.tickersLimit = input[6].equals("")?70:Integer.parseInt(input[6]);
        this.strategy = input[7];
        this.ordersHaltTrading=input[8].equals("")?7:Integer.parseInt(input[8]);
        this.ownerEmail=input[9];
        this.reqHandle.maxreqpersec = this.rtMessageLimit;
        this.reqHistoricalHandle=new ReqHandleHistorical(ip+"-"+String.valueOf(port)+"-"+String.valueOf(clientID));
        this.getReqHistoricalHandle().delay=histMessageLimit;
        }catch (Exception e){
            logger.log(Level.INFO,null,e);
            JOptionPane.showMessageDialog(null, "The connection file has invalid data. inStrat will close.");
            System.exit(0);
        }
    }

    public void initializeConnection(String strategyName) {
        this.pnlByStrategy.put(strategyName, 0D);
        this.maxpnlByStrategy.put(strategyName, 0D);
        this.minpnlByStrategy.put(strategyName, 0D);

        for (int i = 0; i < Parameters.symbol.size(); i++) {
            Index ind = new Index(strategyName, i);
            ordersSymbols.put(ind, new ArrayList<SymbolOrderMap>());
            pnlBySymbol.put(ind, 0D);
            /*
            for (int j = 0; j <= 5; j++) {
                ordersSymbols.get(ind).add(new ArrayList<Integer>());
            }
            */
        }
    }

    public BeanConnection clone(BeanConnection orig) {
        BeanConnection b = new BeanConnection();
        b.setIp(orig.getIp());
        b.setPort(orig.getPort());
        b.setClientID(orig.getClientID());
        b.setPurpose(orig.getPurpose());
        b.setRtMessageLimit(orig.getRtMessageLimit());
        b.setHistMessageLimit(orig.getHistMessageLimit());
        b.setTickersLimit(orig.getTickersLimit());
        b.getReqHandle().maxreqpersec = orig.getRtMessageLimit();
        b.setStrategy(orig.getStrategy().toLowerCase());
        return b;
    }


    /**
     * @return the ip
     */
    public String getIp() {
        return ip;
    }

    /**
     * @param ip the ip to set
     */
    public void setIp(String ip) {
        this.ip = ip;
    }

    /**
     * @return the port
     */
    public Integer getPort() {
        return port;
    }

    /**
     * @param port the port to set
     */
    public void setPort(Integer port) {
        this.port = port;
    }

    /**
     * @return the clientID
     */
    public Integer getClientID() {
        return clientID;
    }

    /**
     * @param clientID the clientID to set
     */
    public void setClientID(Integer clientID) {
        this.clientID = clientID;
    }

    /**
     * @return the purpose
     */
    public String getPurpose() {
        return purpose;
    }

    /**
     * @param purpose the purpose to set
     */
    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    /**
     * @return the rtMessageLimit
     */
    public Integer getRtMessageLimit() {
        return rtMessageLimit;
    }

    /**
     * @param rtMessageLimit the rtMessageLimit to set
     */
    public void setRtMessageLimit(Integer rtMessageLimit) {
        this.rtMessageLimit = rtMessageLimit;
    }

    /**
     * @return the histMessageLimit
     */
    public Integer getHistMessageLimit() {
        return histMessageLimit;
    }

    /**
     * @param histMessageLimit the histMessageLimit to set
     */
    public void setHistMessageLimit(Integer histMessageLimit) {
        this.histMessageLimit = histMessageLimit;
    }

    /**
     * @return the tickersLimit
     */
    public Integer getTickersLimit() {
        return tickersLimit;
    }

    /**
     * @param tickersLimit the tickersLimit to set
     */
    public void setTickersLimit(Integer tickersLimit) {
        this.tickersLimit = tickersLimit;
    }

    /**
     * @return the wrapper
     */
    public TWSConnection getWrapper() {
        return wrapper;
    }

    /**
     * @param wrapper the wrapper to set
     */
    public void setWrapper(TWSConnection wrapper) {
        this.wrapper = wrapper;
    }

    /**
     * @return the idmanager
     */
    public RequestIDManager getIdmanager() {
        return idmanager;
    }

    /**
     * @param idmanager the idmanager to set
     */
    public void setIdmanager(RequestIDManager idmanager) {
        this.idmanager = idmanager;
    }

    /**
     * @return the timeDiff
     */
    public long getTimeDiff() {
        return timeDiff;
    }

    /**
     * @param timeDiff the timeDiff to set
     */
    public void setTimeDiff(long timeDiff) {
        this.timeDiff = timeDiff;
    }

    /**
     * @return the reqHandle
     */
    public ReqHandle getReqHandle() {
        return reqHandle;
    }

    /**
     * @param reqHandle the reqHandle to set
     */
    public void setReqHandle(ReqHandle reqHandle) {
        this.reqHandle = reqHandle;
    }



    /**
     * @return the connectionTime
     */
    public Long getConnectionTime() {
        return connectionTime;
    }

    /**
     * @param connectionTime the connectionTime to set
     */
    public void setConnectionTime(Long connectionTime) {
        this.connectionTime = connectionTime;
    }

    /**
     * @return the orders
     */
    public HashMap<Integer, OrderBean> getOrders() {
        
            return orders;
            }

    /**
     * @param orders the orders to set
     */
    public void setOrders(HashMap<Integer, OrderBean> orders) {
        
        this.orders = orders;
            }

    /**
     * @return the ordersSymbols
     */
    public HashMap<Index, ArrayList<SymbolOrderMap>> getOrdersSymbols() {
        return ordersSymbols;
    }

    /**
     * @param ordersSymbols the ordersSymbols to set
     */
    public void setOrdersSymbols(HashMap<Index, ArrayList<SymbolOrderMap>> ordersSymbols) {
        this.ordersSymbols = ordersSymbols;
    }

    /**
     * @return the notionalPositions
     */
    public HashMap<Index, BeanPosition> getPositions() {
        return Positions;
    }

    /**
     * @param notionalPositions the notionalPositions to set
     */
    public void setPositions(HashMap<Index, BeanPosition> Positions) {
        this.Positions = Positions;
    }

    /**
     * @return the ordersToBeCancelled
     */
    public synchronized HashMap<Integer, BeanOrderInformation> getOrdersToBeCancelled() {
        return ordersToBeCancelled;
    }

    /**
     * @param ordersToBeCancelled the ordersToBeCancelled to set
     */
    public void setOrdersToBeCancelled(HashMap<Integer, BeanOrderInformation> ordersToBeCancelled) {
        this.ordersToBeCancelled = ordersToBeCancelled;
    }

    /**
     * @return the ordersToBeFastTracked
     */
    public HashMap<Integer, BeanOrderInformation> getOrdersToBeFastTracked() {
        return ordersToBeFastTracked;
    }

    /**
     * @param ordersToBeFastTracked the ordersToBeFastTracked to set
     */
    public void setOrdersToBeFastTracked(HashMap<Integer, BeanOrderInformation> ordersToBeFastTracked) {
        this.ordersToBeFastTracked = ordersToBeFastTracked;
    }

    /**
     * @return the ordersToBeRetried
     */
    public HashMap<Long, OrderEvent> getOrdersToBeRetried() {
        return ordersToBeRetried;
    }

    /**
     * @param ordersToBeRetried the ordersToBeRetried to set
     */
    public void setOrdersToBeRetried(HashMap<Long, OrderEvent> ordersToBeRetried) {
        this.ordersToBeRetried = ordersToBeRetried;
    }

    /**
     * @return the ordersMissed
     */
    public synchronized ArrayList<Integer> getOrdersMissed() {
        return ordersMissed;
    }

    /**
     * @param ordersMissed the ordersMissed to set
     */
    public synchronized void setOrdersMissed(ArrayList<Integer> ordersMissed) {
        this.ordersMissed = ordersMissed;
    }

    /**
     * @return the ordersInProgress
     */
    public synchronized ArrayList<Integer> getOrdersInProgress() {
        return ordersInProgress;
    }

    /**
     * @param ordersInProgress the ordersInProgress to set
     */
    public synchronized void setOrdersInProgress(ArrayList<Integer> ordersInProgress) {
        this.ordersInProgress = ordersInProgress;
    }

    /**
     * @return the strategy
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * @param strategy the strategy to set
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    /**
     * @return the accountName
     */
    public String getAccountName() {
        return accountName;
    }

    /**
     * @param accountName the accountName to set
     */
    public void setAccountName(String accountName) {
        String oldValue = this.accountName;
        this.accountName = accountName;
        propertySupport.firePropertyChange(PROP_ACCOUNT_NAME, oldValue, this.accountName);
    }

    /**
     * @return the pnlByAccount
     */
    public double getPnlByAccount() {
        synchronized(lockPNLStrategy){
        return pnlByAccount;
        }
    }

    /**
     * @param pnlByAccount the pnlByAccount to set
     */
    public void setPnlByAccount(double pnlByAccount) {
        synchronized(lockPNLStrategy){
            this.pnlByAccount = pnlByAccount;
        }
    }

    /**
     * @return the pnlByStrategy
     */
    public HashMap<String, Double> getPnlByStrategy() {
        synchronized(lockPNLStrategy){
            return pnlByStrategy;
        }
    }

    /**
     * @param pnlByStrategy the pnlByStrategy to set
     */
    public void setPnlByStrategy(HashMap<String, Double> pnlByStrategy) {
        synchronized(lockPNLStrategy){
            this.pnlByStrategy = pnlByStrategy;
        }
    }

    /**
     * @return the pnlBySymbol
     */
    public HashMap<Index, Double> getPnlBySymbol() {
        synchronized(lockPNLStrategy){
            return pnlBySymbol;
        }
    }

    /**
     * @param pnlBySymbol the pnlBySymbol to set
     */
    public void setPnlBySymbol(HashMap<Index, Double> pnlBySymbol) {
        synchronized(lockPNLStrategy){
            this.pnlBySymbol = pnlBySymbol;
        }
    }

    /**
     * @return the maxpnlByStrategy
     */
    public HashMap<String, Double> getMaxpnlByStrategy() {
        synchronized(lockPNLStrategy){
            return maxpnlByStrategy;
        }
    }

    /**
     * @param maxpnlByStrategy the maxpnlByStrategy to set
     */
    public void setMaxpnlByStrategy(HashMap<String, Double> maxpnlByStrategy) {
        synchronized(lockPNLStrategy){
            this.maxpnlByStrategy = maxpnlByStrategy;
        }
    }

    /**
     * @return the minpnlByStrategy
     */
    public HashMap<String, Double> getMinpnlByStrategy() {
        synchronized(lockPNLStrategy){
            return minpnlByStrategy;
        }
    }

    /**
     * @param minpnlByStrategy the minpnlByStrategy to set
     */
    public void setMinpnlByStrategy(HashMap<String, Double> minpnlByStrategy) {
        synchronized(lockPNLStrategy){
            this.minpnlByStrategy = minpnlByStrategy;
        }
    }



    @Override
    public void reader(String inputfile, ArrayList target) {
        File inputFile = new File(inputfile);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> existingConnectionsLoad = Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
                existingConnectionsLoad.remove(0);
                for (String connectionLine : existingConnectionsLoad) {
                    if(!connectionLine.equals("")){
                    String[] input = connectionLine.split(",");
                    target.add(new BeanConnection(input));
                    }
                    }                    
            } catch (IOException ex) {
                logger.log(Level.SEVERE, null, ex);
            }
        }

    }

    @Override
    public void writer(String fileName) {
    }

    /**
     * @return the ordersHaltTrading
     */
    public int getOrdersHaltTrading() {
        return ordersHaltTrading;
    }

    /**
     * @param ordersHaltTrading the ordersHaltTrading to set
     */
    public void setOrdersHaltTrading(int ordersHaltTrading) {
        this.ordersHaltTrading = ordersHaltTrading;
    }

    /**
     * @return the ownerEmail
     */
    public String getOwnerEmail() {
        return ownerEmail;
    }

    /**
     * @param ownerEmail the ownerEmail to set
     */
    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    /**
     * @return the reqHistoricalHandle
     */
    public ReqHandleHistorical getReqHistoricalHandle() {
        return reqHistoricalHandle;
    }

    /**
     * @param reqHistoricalHandle the reqHistoricalHandle to set
     */
    public void setReqHistoricalHandle(ReqHandleHistorical reqHistoricalHandle) {
        this.reqHistoricalHandle = reqHistoricalHandle;
    }

    /**
     * @return the activeOrders
     */
    public HashMultimap<Index, BeanOrderInformation> getActiveOrders() {
        synchronized(lockActiveOrders){
        return activeOrders;
        }
    }

    /**
     * @param activeOrders the activeOrders to set
     */
    public void setActiveOrders(HashMultimap<Index, BeanOrderInformation> activeOrders) {
        synchronized(lockActiveOrders){
        this.activeOrders = activeOrders;
        }
    }

    /**
     * @return the orderMapping
     */
    public HashMap<Index,ArrayList<Integer>> getOrderMapping() {
        return orderMapping;
    }

    /**
     * @param orderMapping the orderMapping to set
     */
    public void setOrderMapping(HashMap<Index,ArrayList<Integer>> orderMapping) {
        this.orderMapping = orderMapping;
    }
}
