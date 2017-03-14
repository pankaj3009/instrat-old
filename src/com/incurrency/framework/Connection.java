/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.ib.client.Contract;
import com.ib.client.EWrapper;
import com.ib.client.ExecutionFilter;
import com.ib.client.Order;
import com.incurrency.framework.fundamental.FundamentalDataListener;
import java.util.ArrayList;
import java.util.HashMap;

/**
 *
 * @author psharma
 */
public interface Connection {

    public boolean connect();
    public void disconnect();
    public Drop getAccountIDSync();
    public void getAccountUpdates();
    public void cancelAccountUpdates();
    public void getContractDetails(BeanSymbol s,String overrideType);
    public void requestSingleSnapshot(BeanSymbol s);
    public void getMktData(BeanSymbol s, boolean isSnap);
    public int getOutstandingSnapshots();
    public HashMap<Integer, Order> createOrder(OrderEvent e);
    public HashMap<Integer, Order> createOrderFromExisting(BeanConnection c, int internalorderid, String strategy) ;
    public ArrayList<Contract> createContract(int id);
    public Contract createContract(BeanSymbol s);
    public ArrayList<Integer> placeOrder(BeanConnection c, OrderEvent e, HashMap<Integer, Order> orders, ExecutionManager oms);
    boolean tradeIntegrityOK(EnumOrderSide side, EnumOrderStage stage, HashMap<Integer, Order> orders, boolean reset);   
    public void cancelMarketData(BeanSymbol s);
    public void cancelOrder(BeanConnection c, int orderID, boolean force);
    public void requestFundamentalData(BeanSymbol s, String reportType);
    public void cancelFundamentalData(int reqId);
    public void requestOpenOrders();
    public void requestExecutionDetails(ExecutionFilter filter);
    public void requestHistoricalData(BeanSymbol s, String endDate, String duration, String barSize);
    public void cancelHistoricalData(int reqid);   
    public void getCurrentTime();
    public boolean isHistoricalDataFarmConnected();
    public boolean isConnected();
    public BeanCassandraConnection getCassandraDetails();
    public void addOrderStatusListener(OrderStatusListener l);
    public void removeOrderStatusListener(OrderStatusListener l);   
    public void addTWSErrorListener(TWSErrorListener l) ;
    public void removeTWSErrorListener(TWSErrorListener l);
    public void addBidAskListener(BidAskListener l);
    public void removeBidAskListener(BidAskListener l);
    public void addFundamentalListener(FundamentalDataListener l);
    public void removeFundamentalListener(FundamentalDataListener l);
    public void addTradeListener(TradeListener l);
    public void removeTradeListener(TradeListener l) ;
}