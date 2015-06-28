/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.EventObject;
import java.util.HashMap;

/**
 *
 * @author admin
 */
public class OrderEvent extends EventObject {
    private int _internalorder;
    private int _internalorderentry;
    private BeanSymbol _symbolBean;
    private EnumOrderSide _side;
    private int _orderSize;
    private double _limitPrice;
    private double _firstLimitPrice;
    private double _triggerPrice;
    private String _ordReference;
    private int _expireTime;
    private EnumOrderStage _orderStage;
    private int _dynamicOrderDuration;
    private double _maxSlippage;
    private String _tag="";
    private boolean _transmit;
    private String _validity;
    private String _account;
    private boolean _scale=true;
    private EnumOrderType _orderType;
    private EnumOrderReason _reason;
    private String _orderGroup;
    private String _effectiveFrom;
    private HashMap _stubs;
    
    public OrderEvent(Object obj){
        super (obj);
    }
//triggered by csv orders
    /*
    public OrderEvent(Object obj,int internalorder,int internalorderentry,BeanSymbol s,EnumOrderSide side, EnumOrderReason reason, EnumOrderType orderType, int orderSize, double limitprice, double triggerprice, String ordReference, int expireTime, EnumOrderStage stage, int dynamicdur, double slippage, boolean transmit, String validity, boolean scale,String orderGroup,String effectiveFrom,HashMap<Integer,Integer>stubs){
        super(obj);
        this._internalorder=internalorder;
        this._internalorderentry=internalorderentry;
        this._symbolBean=s;
        this._side=side;
        this._orderType=orderType;
        this._orderSize=orderSize;
        this._limitPrice=limitprice;
        this._firstLimitPrice=limitprice;
        this._triggerPrice=triggerprice;
        this._ordReference=ordReference;
        this._expireTime=expireTime;
        this._orderStage=stage;
        this._dynamicOrderDuration=dynamicdur;
        this._maxSlippage=slippage/100;
        this._firstLimitPrice=limitprice;
        this._tag="";
        this._transmit=transmit;
        this._validity=validity;
        this._account="";
        this._scale=scale;
        this._reason=reason;
        this._orderGroup=orderGroup;
        this._effectiveFrom=effectiveFrom;
        this._stubs=stubs;
    }
 */
    //fired by adr orders
//  public OrderEvent(Object obj,int internalorder,int internalorderentry,BeanSymbol s,EnumOrderSide side, EnumOrderReason reason, EnumOrderType orderType, int orderSize, double limitprice, double triggerprice, String ordReference, int expireTime, EnumOrderStage stage, int dynamicdur, double slippage, boolean transmit, String validity, boolean scale,String orderGroup,String effectiveFrom,HashMap<Integer,Integer>stubs){
    public OrderEvent(Object obj,int internalorder,int internalorderentry,BeanSymbol s,EnumOrderSide side,EnumOrderReason reason,EnumOrderType orderType, int orderSize, double limitprice, double triggerprice, String ordReference, int expireTime, EnumOrderStage intent, int dynamicdur, double slippage, boolean transmit, String validity,boolean scale,String orderGroup,String effectiveFrom,HashMap<Integer,Integer>stubs){
        //after slippage add boolean trasmit,string validity, at end add String effectiveFrom, HashMap<Integer,Integer>stubs
        super(obj);
        this._internalorder=internalorder;
        this._internalorderentry=internalorderentry;
        this._symbolBean=s;
        this._side=side;
        this._orderType=orderType;
        this._orderSize=orderSize;
        this._limitPrice=limitprice;
        this._triggerPrice=triggerprice;
        this._ordReference=ordReference;
        this._expireTime=expireTime;
        this._orderStage=intent;
        this._dynamicOrderDuration=dynamicdur;
        this._maxSlippage=slippage;
        this._firstLimitPrice=limitprice;
        this._tag="";
        this._transmit=true;
        this._validity="DAY";
        this._account="";
        this._scale=scale;
        this._reason=reason;
        this._orderGroup=orderGroup;
        this._effectiveFrom="";
        
    }

    static OrderEvent fastClose(BeanSymbol s,EnumOrderSide side,int size,String orderReference){
        OrderEvent e=new OrderEvent(new Object(),-1,-1,s,side,EnumOrderReason.REGULAREXIT,EnumOrderType.MKT,size,0,0,orderReference,0, EnumOrderStage.INIT, 0, 0D, true, "DAY", false,"","",null);
        return e;
    }
    /**
     * @return the _symbolBean
     */
    public BeanSymbol getSymbolBean() {
        return _symbolBean;
    }

    /**
     * @param symbolBean the _symbolBean to set
     */
    public void setSymbolBean(BeanSymbol symbolBean) {
        this._symbolBean = symbolBean;
    }

    /**
     * @return the _side
     */
    public EnumOrderSide getSide() {
        return _side;
    }

    /**
     * @param side the _side to set
     */
    public void setSide(EnumOrderSide side) {
        this._side = side;
    }

    /**
     * @return the _orderSize
     */
    public int getOrderSize() {
        return _orderSize;
    }

    /**
     * @param orderSize the _orderSize to set
     */
    public void setOrderSize(int orderSize) {
        this._orderSize = orderSize;
    }

    /**
     * @return the _limitPrice
     */
    public double getLimitPrice() {
        return _limitPrice;
    }

    /**
     * @param limitPrice the _limitPrice to set
     */
    public void setLimitPrice(double limitPrice) {
        this._limitPrice = limitPrice;
    }

    /**
     * @return the _triggerPrice
     */
    public double getTriggerPrice() {
        return _triggerPrice;
    }

    /**
     * @param triggerPrice the _triggerPrice to set
     */
    public void setTriggerPrice(double triggerPrice) {
        this._triggerPrice = triggerPrice;
    }

    /**
     * @return the ordReference
     */
    public String getOrdReference() {
        return _ordReference;
    }

    /**
     * @param ordReference the ordReference to set
     */
    public void setOrdReference(String ordReference) {
        this._ordReference = ordReference;
    }

    /**
     * @return the _expireTime
     */
    public int getExpireTime() {
        return _expireTime;
    }

    /**
     * @param expireTime the _expireTime to set
     */
    public void setExpireTime(int expireTime) {
        this._expireTime = expireTime;
    }

    /**
     * @return the _orderType
     */
    public EnumOrderStage getOrderStage() {
        return _orderStage;
    }

    /**
     * @param orderType the _orderType to set
     */
    public void setOrderStage(EnumOrderStage orderStage) {
        this._orderStage = orderStage;
    }

    /**
     * @return the _dynamicOrderDuration
     */
    public int getDynamicOrderDuration() {
        return _dynamicOrderDuration;
    }

    /**
     * @param dynamicOrderDuration the _dynamicOrderDuration to set
     */
    public void setDynamicOrderDuration(int dynamicOrderDuration) {
        this._dynamicOrderDuration = dynamicOrderDuration;
    }

    /**
     * @return the _maxSlippage
     */
    public double getMaxSlippage() {
        return _maxSlippage;
    }

    /**
     * @param maxSlippage the _maxSlippage to set
     */
    public void setMaxSlippage(double maxSlippage) {
        this._maxSlippage = maxSlippage;
    }

    /**
     * @return the _firstLimitPrice
     */
    public double getFirstLimitPrice() {
        return _firstLimitPrice;
    }

    /**
     * @return the _internalorder
     */
    public int getInternalorder() {
        return _internalorder;
    }

    /**
     * @param internalorder the _internalorder to set
     */
    public void setInternalorder(int internalorder) {
        this._internalorder = internalorder;
    }

    /**
     * @return the _internalorderentry
     */
    public int getInternalorderentry() {
        return _internalorderentry;
    }

    /**
     * @param internalorderentry the _internalorderentry to set
     */
    public void setInternalorderentry(int internalorderentry) {
        this._internalorderentry = internalorderentry;
    }

    /**
     * @return the _tag
     */
    public String getTag() {
        return _tag;
    }

    /**
     * @param link the _tag to set
     */
    public void setTag(String tag) {
        this._tag = tag;
    }

    /**
     * @return the _transmit
     */
    public boolean isTransmit() {
        return _transmit;
    }

    /**
     * @param transmit the _transmit to set
     */
    public void setTransmit(boolean transmit) {
        this._transmit = transmit;
    }

    /**
     * @return the _validity
     */
    public String getValidity() {
        return _validity;
    }

    /**
     * @param validity the _validity to set
     */
    public void setValidity(String validity) {
        this._validity = validity;
    }

    /**
     * @return the _account
     */
    public String getAccount() {
        return _account;
    }

    /**
     * @param account the _account to set
     */
    public void setAccount(String account) {
        this._account = account;
    }

    /**
     * @return the _scale
     */
    public boolean isScale() {
        return _scale;
    }

    /**
     * @param safemode the _scale to set
     */
    public void setScale(boolean safemode) {
        this._scale = safemode;
    }

    /**
     * @return the _orderType
     */
    public EnumOrderType getOrderType() {
        return _orderType;
    }

    /**
     * @param orderType the _orderType to set
     */
    public void setOrderType(EnumOrderType orderType) {
        this._orderType = orderType;
    }

    /**
     * @return the _reason
     */
    public EnumOrderReason getReason() {
        return _reason;
    }

    /**
     * @param reason the _reason to set
     */
    public void setReason(EnumOrderReason reason) {
        this._reason = reason;
    }

    /**
     * @return the _orderGroup
     */
    public String getOrderGroup() {
        return _orderGroup;
    }

    /**
     * @param orderGroup the _orderGroup to set
     */
    public void setOrderGroup(String orderGroup) {
        this._orderGroup = orderGroup;
    }

    /**
     * @return the _effectiveFrom
     */
    public String getEffectiveFrom() {
        return _effectiveFrom;
    }

    /**
     * @param effectiveFrom the _effectiveFrom to set
     */
    public void setEffectiveFrom(String effectiveFrom) {
        this._effectiveFrom = effectiveFrom;
    }

    /**
     * @return the _stubs
     */
    public HashMap getStubs() {
        return _stubs;
    }

    /**
     * @param stubs the _stubs to set
     */
    public void setStubs(HashMap stubs) {
        this._stubs = stubs;
    }

    /**
     * @param firstLimitPrice the _firstLimitPrice to set
     */
    public void setFirstLimitPrice(double firstLimitPrice) {
        this._firstLimitPrice = firstLimitPrice;
    }
}