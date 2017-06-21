/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.ArrayList;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 * @author psharma
 */
public class OrderBean extends ConcurrentHashMap<String, String> {

    public OrderBean(OrderBean ob){
        super(ob);
    }
    
    public OrderBean(){
        
    }
    
    public void createLinkedAction(int parentid, String action, String status, String delay) {
        this.put("LinkInternalOrderID", String.valueOf(parentid));
        this.put("LinkStatusTrigger", status);
        this.put("LinkAction", action);
        this.put("LinkDelay", delay);
    }

    public boolean linkedActionExists() {
        return this.get("LinkInternalOrderID") != null ? Boolean.TRUE : Boolean.FALSE;
    }

    public int getParentSymbolID() {
        String parentSymbolDisplayName = this.get("ParentDisplayName");
        return Utilities.getIDFromDisplayName(Parameters.symbol, parentSymbolDisplayName);
    }

    public int getChildSymbolID() {
        String childSymbolDisplayName = this.get("ChildDisplayName");
        return Utilities.getIDFromDisplayName(Parameters.symbol, childSymbolDisplayName);
    }

    public EnumOrderSide getOrderSide() {
        String orderSide = this.get("OrderSide");
        if (orderSide != null) {
            return (EnumOrderSide.valueOf(orderSide));
        } else {
            return EnumOrderSide.UNDEFINED;
        }
    }

    public EnumOrderReason getOrderReason() {
        String orderReason = this.get("OrderReason");
        if (orderReason != null) {
            return (EnumOrderReason.valueOf(orderReason));
        } else {
            return EnumOrderReason.UNDEFINED;
        }
    }

    public EnumOrderType getOrderType() {
        String orderType = this.get("OrderType");
        if (orderType != null) {
            return (EnumOrderType.valueOf(orderType));
        } else {
            return EnumOrderType.UNDEFINED;
        }
    }

    public EnumOrderStage getOrderStage() {
        String orderStage = this.get("OrderStage");
        if (orderStage != null) {
            return (EnumOrderStage.valueOf(orderStage));
        } else {
            return EnumOrderStage.UNDEFINED;
        }
    }

    public EnumOrderStatus getOrderStatus() {
        String orderStatus = this.get("OrderStatus");
        if (orderStatus != null) {
            return (EnumOrderStatus.valueOf(orderStatus));
        } else {
            return EnumOrderStatus.UNDEFINED;
        }
    }

    public int getOriginalOrderSize() {
        String orderSize = this.get("OriginalOrderSize");
        return Utilities.getInt(orderSize, 0);

    }

    public int getCurrentOrderSize() {
        String orderSize = this.get("CurrentOrderSize");
        return Utilities.getInt(orderSize, 0);
    }

    public int getCurrentFillSize() {
        String fillSize = this.get("CurrentFillSize");
        return Utilities.getInt(fillSize, 0);

    }

    public int getTotalFillSize() {
        String fillSize = this.get("TotalFillSize");
        return Utilities.getInt(fillSize, 0);

    }
    
        public int getTotalFillPrice() {
        String fillSize = this.get("TotalFillPrice");
        return Utilities.getInt(fillSize, 0);

    }

    public int getDisplaySize() {
        String displaySize = this.get("DisplaySize");
        return Utilities.getInt(displaySize, 0);

    }

    public int getMaximumOrderValue() {
        String maximumOrderValue = this.get("MaximumOrderValue");
        return Utilities.getInt(maximumOrderValue, 0);
    }

    public int getParentInternalOrderID() {
        String parentInternalOrderID = this.get("ParentInternalOrderID");
        return Utilities.getInt(parentInternalOrderID, -1);
    }

    public int getOrderIDForSquareOff() {
        String parentEntryInternalOrderID = this.get("OrderIDForSquareOff");
        return Utilities.getInt(parentEntryInternalOrderID, -1);
    }

    public int getInternalOrderID() {
        String childInternalOrderID = this.get("InternalOrderID");
        return Utilities.getInt(childInternalOrderID, -1);
    }

    public int getExternalOrderID() {
        String externalOrderID = this.get("ExternalOrderID");
        return Utilities.getInt(externalOrderID, 0);
    }

    public int getLinkDelay() {
        String linkDelay = this.get("LinkDelay");
        return Utilities.getInt(linkDelay, 0);
    }

    public double getLimitPrice() {
        String limitPrice = this.get("LimitPrice");
        return Utilities.getDouble(limitPrice, 0);
    }

    public double getTriggerPrice() {
        String triggerPrice = this.get("TriggerPrice");
        return Utilities.getDouble(triggerPrice, 0);
    }

    public double getMaxPermissibleImpactCost() {
        String maxPermissibleImpactCost = this.get("MaxPermissibleImpactCost");
        return Utilities.getDouble(maxPermissibleImpactCost, 0);
    }

    public double getCurrentFillPrice(){
        return Utilities.getDouble("CurrentFillPrice", 0);
    }
    
    public boolean isScale() {
        String scale = this.get("Scale");
        if (scale != null) {
            return Boolean.valueOf(scale);
        } else {
            return Boolean.FALSE;
        }
    }

    public boolean isCancelRequested() {
        String cancelRequested = this.get("CancelRequested");
        if (cancelRequested != null) {
            return Boolean.valueOf(cancelRequested);
        } else {
            return Boolean.FALSE;
        }
    }

    public String getOrderReference() {
        return this.get("OrderReference");
    }

    public String getEffectiveFrom() {
        return this.get("EffectiveFrom");
    }

    public String getEffectiveTill() {
        return this.get("EffectiveTill");
    }

    public String getParentDisplayName() {
        return this.get("ParentDisplayName");
    }

    public String getChildDisplayName() {
        return this.get("ChildDisplayName");
    }    

    public String getOrderLog(){
        String value=this.get("OrderLog");
        if(value==null){
            return "";
        }else{
            return value;
        }
    }
    
     public String getSpecifiedBrokerAccount(){
        String value=this.get("SpecifiedBrokerAccount");
        return value;
    }
    
    public String getStubs() {
        return null;
    }
    public Date getEffectiveTillDate(){
        return DateUtil.parseDate("yyyyMMdd HH:mm:ss", this.get("EffectiveTill"), Algorithm.timeZone);
    }
    
    public Date getOrderTime(){
         return DateUtil.parseDate("yyyyMMdd HH:mm:ss", this.get("OrderTime"), Algorithm.timeZone);
    }
    
    
    
    //Setters
    
    public void setParentDisplayName(String value){
        this.put("ParentDisplayName", value);
    }
    
    public void setOrderReference(String value){
        this.put("OrderReference", value);
    }
    
    public void setInternalOrderID(int value){
        this.put("InternalOrderID", String.valueOf(value));
    }
    
    public void setOrderSide(EnumOrderSide value){
        this.put("OrderSide",String.valueOf(value));
    }
    
    public void setParentInternalOrderID(int value){
        this.put("ParentInternalOrderID", String.valueOf(value));
    }
    
    public void setOriginalOrderSize(int value){
        this.put("OriginalOrderSize",String.valueOf(value));
    }
    
     public void setOrderIDForSquareOff(int value){
        this.put("OrderIDForSquareOff", String.valueOf(value));
    }
    
    
    public void setOrderLog(String value){
        this.put("OrderLog",value);
    }
    
    public void setOrderStatus(EnumOrderStatus value) {
        this.put("OrderStatus", String.valueOf(value));
    }

    public void setTriggerPrice(double value) {
        this.put("TriggerPrice", String.valueOf(value));
    }

    public void setLimitPrice(double value) {
        this.put("LimitPrice", String.valueOf(value));
    }

    public void setCurrentOrderSize(int value) {
        this.put("CurrentOrderSize", String.valueOf(value));
    }
    
    public void setChildDisplayName(String value) {
        this.put("ChildDisplayName", String.valueOf(value));
    }

    public void setExternalOrderID(int value) {
        this.put("ExternalOrderID", String.valueOf(value));
    }

    public void setOrderTime() {
        this.put("OrderTime", DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss", new Date().getTime()));
    }
    
    public void setOrderStage(EnumOrderStage value){
        this.put("OrderStage", String.valueOf(value));
    }

    public void setCurrentFillSize(int value){
        this.put("CurrentFillSize", String.valueOf(value));
    }
    
    public void setCurrentFillPrice(double value){
        this.put("CurrentFillPrice", String.valueOf(this));
    }
    
    public void setTotalFillSize(int value) {
        this.put("TotalFillSize", String.valueOf(value));
    }
    
    public void setTotalFillPrice(double value) {
        this.put("TotalFillPrice", String.valueOf(value));
    }
    
    public void setOrderReason(EnumOrderReason value) {
        this.put("OrderReason", String.valueOf(value));
    }
    
    public void setSpecifiedBrokerAccount(String value){
        this.put("SpecifiedBrokerAccount", value);
    }
    
    public void setOrderType(EnumOrderType orderType){
        this.put("OrderType", String.valueOf(orderType));
    }
    
    //Order Attributes
    public int getOrdersPerMinute() {
        return Utilities.getInt(this.get("OrdersPerMinute"), 1);
    }

    public void setOrdersPerMinute(int value) {
        this.put("OrdersPerMinute", String.valueOf(value));
    }

    public double getImproveProbability() {
        return Utilities.getInt(this.get("ImproveProbability"), 1);
    }

    public void setImproveProbability(double value) {
        this.put("ImproveProbability", String.valueOf(value));
    }

    public double getImproveAmount() {
        return Utilities.getInt(this.get("ImproveAmount"), 0);
    }

    public void setImproveAmount(double value) {
        this.put("ImproveAmount", String.valueOf(value));
    }

    public int getFatFingerWindow() {
        return Utilities.getInt(this.get("FatFingerWindow"), 120);
    }

    public void setFatFingerWindow(int value) {
        this.put("FatFingerWindow", String.valueOf(value));
    }

    public int getStickyPeriod() {
        return Utilities.getInt(this.get("StickyPeriod"), 60);
    }

    public void setStickyPeriod(int value) {
        this.put("StickyPeriod", String.valueOf(value));
    }
}
