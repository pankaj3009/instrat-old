/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;


import java.io.Serializable;

/**
 *
 * @author admin
 */
public class OrderBean implements Serializable {
    private int parentSymbolID;//starts with 1.
    private int childSymbolID;
    private int orderID;
    private int internalOrderID;
    private int internalOrderIDEntry;
    private Long orderDate;
    private EnumOrderSide parentOrderSide;
    private EnumOrderSide childOrderSide;
    private EnumOrderType orderType; //1. MKT 2. LMT 3. STP LMT 4. Take Profit 5. Other
    private EnumOrderStatus childStatus; //1. Submitted 2. Acknowledged 3. Cancelled
    private EnumOrderStatus parentStatus;
    private int parentOrderSize;
    private int childOrderSize;
    private int childFillSize;
    private int parentFillSize;
    private double fillPrice;
    private boolean cancelRequested=false;
    private int positionsize;
    private double childLimitPrice;
    private double parentLimitPrice;
    private double triggerPrice;
    private String orderValidity;
    private String expireTime;
    private String orderReference;
    private EnumOrderStage intent;
    private EnumOrderReason reason;
    private String ocaGroup;
    private int ocaExecutionLogic;
    private final Object lockparentlimitprice=new Object();
   
    

    
    public OrderBean() {
        
    }

    /**
     * @return the symbolID
     */
    public int getParentSymbolID() {
        return parentSymbolID;
    }

    /**
     * @param symbolID the symbolID to set
     */
    public void setParentSymbolID(int parentSymbolID) {
        this.parentSymbolID = parentSymbolID;
    }

    /**
     * @return the orderID
     */
    public synchronized int getOrderID() {
        return orderID;
    }

    /**
     * @param orderID the orderID to set
     */
    public synchronized void setOrderID(int orderID) {
        this.orderID = orderID;
    }


    /**
     * @return the orderSize
     */
    public int getParentOrderSize() {
        return parentOrderSize;
    }

    /**
     * @param orderSize the orderSize to set
     */
    public void setParentOrderSize(int parentOrderSize) {
        this.parentOrderSize = parentOrderSize;
    }

    /**
     * @return the fillSize
     */
    public int getChildFillSize() {
        return childFillSize;
    }

    /**
     * @param fillSize the fillSize to set
     */
    public void setChildFillSize(int childFillSize) {
        this.childFillSize = childFillSize;
    }

    /**
     * @return the cancelRequested
     */
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /**
     * @param cancelRequested the cancelRequested to set
     */
    public void setCancelRequested(boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }

    /**
     * @return the positionsize
     */
    public int getPositionsize() {
        return positionsize;
    }

    /**
     * @param positionsize the positionsize to set
     */
    public void setPositionsize(int positionsize) {
        this.positionsize = positionsize;
    }

    /**
     * @return the status
     */
    public EnumOrderStatus getChildStatus() {
        return childStatus;
    }

    /**
     * @param status the status to set
     */
    public void setChildStatus(EnumOrderStatus childStatus) {
        this.childStatus = childStatus;
    }

    /**
     * @return the orderType
     */
    public EnumOrderType getOrderType() {
        return orderType;
    }

    /**
     * @param orderType the orderType to set
     */
    public void setOrderType(EnumOrderType orderType) {
        this.orderType = orderType;
    }

    /**
     * @return the orderSide
     */
    public EnumOrderSide getParentOrderSide() {
        return parentOrderSide;
    }

    /**
     * @param orderSide the orderSide to set
     */
    public void setParentOrderSide(EnumOrderSide parentOrderSide) {
        this.parentOrderSide = parentOrderSide;
    }

    void setOrderType(String m_orderType) {
        this.orderType=m_orderType.compareTo("MKT")==0?EnumOrderType.MKT:m_orderType.compareTo("LMT")==0? EnumOrderType.LMT:
                m_orderType.compareTo("STP LMT")==0?EnumOrderType.STPLMT:m_orderType.compareTo("STP")==0?EnumOrderType.STP:
                EnumOrderType.UNDEFINED;
        
    }

    /**
     * @return the fillPrice
     */
    public double getFillPrice() {
        return fillPrice;
    }

    /**
     * @param fillPrice the fillPrice to set
     */
    public void setFillPrice(double fillPrice) {
        this.fillPrice = fillPrice;
    }

    /**
     * @return the childLimitPrice
     */
    public double getChildLimitPrice() {
        return childLimitPrice;
    }

    /**
     * @param childLimitPrice the childLimitPrice to set
     */
    public void setChildLimitPrice(double childLimitPrice) {
        this.childLimitPrice = childLimitPrice;
    }

    /**
     * @return the triggerPrice
     */
    public double getTriggerPrice() {
        return triggerPrice;
    }

    /**
     * @param triggerPrice the triggerPrice to set
     */
    public void setTriggerPrice(double triggerPrice) {
        this.triggerPrice = triggerPrice;
    }

    /**
     * @return the orderValidity
     */
    public String getOrderValidity() {
        return orderValidity;
    }

    /**
     * @param orderValidity the orderValidity to set
     */
    public void setOrderValidity(String orderValidity) {
        this.orderValidity = orderValidity;
    }

    /**
     * @return the expireTime
     */
    public String getExpireTime() {
        return expireTime;
    }

    /**
     * @param expireTime the expireTime to set
     */
    public void setExpireTime(String expireTime) {
        this.expireTime = expireTime;
    }

    /**
     * @return the orderReference
     */
    public String getOrderReference() {
        return orderReference;
    }

    /**
     * @param orderReference the orderReference to set
     */
    public void setOrderReference(String orderReference) {
        this.orderReference = orderReference;
    }

    /**
     * @return the orderDate
     */
    public Long getOrderDate() {
        return orderDate;
    }

    /**
     * @param orderDate the orderDate to set
     */
    public void setOrderDate(Long orderDate) {
        this.orderDate = orderDate;
    }

    /**
     * @return the internalOrderID
     */
    public int getInternalOrderID() {
        return internalOrderID;
    }

    /**
     * @param internalOrderID the internalOrderID to set
     */
    public void setInternalOrderID(int internalOrderID) {
        this.internalOrderID = internalOrderID;
    }

    /**
     * @return the internalOrderIDEntry
     */
    public int getInternalOrderIDEntry() {
        return internalOrderIDEntry;
    }

    /**
     * @param internalOrderIDEntry the internalOrderIDEntry to set
     */
    public void setInternalOrderIDEntry(int internalOrderIDEntry) {
        this.internalOrderIDEntry = internalOrderIDEntry;
    }

    /**
     * @return the intent
     */
    public EnumOrderStage getIntent() {
        return intent;
    }

    /**
     * @param intent the intent to set
     */
    public void setIntent(EnumOrderStage intent) {
        this.intent = intent;
    }

    /**
     * @return the reason
     */
    public EnumOrderReason getReason() {
        return reason;
    }

    /**
     * @param reason the reason to set
     */
    public void setReason(EnumOrderReason reason) {
        this.reason = reason;
    }

    /**
     * @return the ocaGroup
     */
    public String getOcaGroup() {
        return ocaGroup;
    }

    /**
     * @param ocaGroup the ocaGroup to set
     */
    public void setOcaGroup(String ocaGroup) {
        this.ocaGroup = ocaGroup;
    }

    /**
     * @return the ocaExecutionLogic
     */
    public int getOcaExecutionLogic() {
        return ocaExecutionLogic;
    }

    /**
     * @param ocaExecutionLogic the ocaExecutionLogic to set
     */
    public void setOcaExecutionLogic(int ocaExecutionLogic) {
        this.ocaExecutionLogic = ocaExecutionLogic;
    }

    /**
     * @return the childSymbolID
     */
    public int getChildSymbolID() {
        return childSymbolID;
    }

    /**
     * @param childSymbolID the childSymbolID to set
     */
    public void setChildSymbolID(int childSymbolID) {
        this.childSymbolID = childSymbolID;
    }

    /**
     * @return the childOrderSide
     */
    public EnumOrderSide getChildOrderSide() {
        return childOrderSide;
    }

    /**
     * @param childOrderSide the childOrderSide to set
     */
    public void setChildOrderSide(EnumOrderSide childOrderSide) {
        this.childOrderSide = childOrderSide;
    }

    /**
     * @return the childOrderSize
     */
    public int getChildOrderSize() {
        return childOrderSize;
    }

    /**
     * @param childOrderSize the childOrderSize to set
     */
    public void setChildOrderSize(int childOrderSize) {
        this.childOrderSize = childOrderSize;
    }

    /**
     * @return the parentFillSize
     */
    public int getParentFillSize() {
        return parentFillSize;
    }

    /**
     * @param parentFillSize the parentFillSize to set
     */
    public void setParentFillSize(int parentFillSize) {
        this.parentFillSize = parentFillSize;
    }

    /**
     * @return the parentStatus
     */
    public EnumOrderStatus getParentStatus() {
        return parentStatus;
    }

    /**
     * @param parentStatus the parentStatus to set
     */
    public void setParentStatus(EnumOrderStatus parentStatus) {
        this.parentStatus = parentStatus;
    }

    /**
     * @return the parentLimitPrice
     */
    public double getParentLimitPrice() {
        synchronized(lockparentlimitprice){
            return parentLimitPrice;
    }
    }

    /**
     * @param parentLimitPrice the parentLimitPrice to set
     */
    public void setParentLimitPrice(double parentLimitPrice) {
        synchronized(lockparentlimitprice){
            this.parentLimitPrice = parentLimitPrice;
        }
    }


    
    
}
