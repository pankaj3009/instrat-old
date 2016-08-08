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
    private int _orderidint;
    private int _entryorderidint;
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
    private String _log;
    private int _disclosedsize;
    
    public OrderEvent(Object obj){
        super (obj);
    }
    
       public OrderEvent clone(OrderEvent orig) {
        OrderEvent b = new OrderEvent(new Object());
    b._orderidint=orig._orderidint;
    b._entryorderidint=orig._entryorderidint;
    b._symbolBean=orig._symbolBean;
    b._side=orig._side;
    b._orderSize=orig._orderSize;
    b._limitPrice=orig._limitPrice;
    b._firstLimitPrice=orig._firstLimitPrice;
    b._triggerPrice=orig._triggerPrice;
    b._ordReference=orig._ordReference;
    b._expireTime=orig._expireTime;
    b._orderStage=orig._orderStage;
    b._dynamicOrderDuration=orig._dynamicOrderDuration;
    b._maxSlippage=orig._maxSlippage;
    b._tag=orig._tag;
    b._transmit=orig._transmit;
    b._validity=orig._validity;
    b._account=orig._account;
    b._scale=orig._scale;
    b._orderType=orig._orderType;
    b._reason=orig._reason;
    b._orderGroup=orig._orderGroup;
    b._effectiveFrom=orig._effectiveFrom;
    b._stubs=orig._stubs;
    b._log=orig._log;
    b._disclosedsize=orig._disclosedsize;
    return b;
    }

    public OrderEvent(Object obj,int internalorder,int internalorderentry,BeanSymbol s,EnumOrderSide side,EnumOrderReason reason,EnumOrderType orderType, int orderSize, double limitprice, double triggerprice, String ordReference, int expireTime, EnumOrderStage intent, int dynamicdur, double slippage, boolean transmit, String validity,boolean scale,String orderGroup,String effectiveFrom,HashMap<Integer,Integer>stubs,String log){
        //after slippage add boolean trasmit,string validity, at end add String effectiveFrom, HashMap<Integer,Integer>stubs
        super(obj);
        this._orderidint=internalorder;
        this._entryorderidint=internalorderentry;
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
        this._log=log;
        
    }

    public OrderEvent(Object obj,HashMap<String,Object>order){
        //after slippage add boolean trasmit,string validity, at end add String effectiveFrom, HashMap<Integer,Integer>stubs
        super(obj);
        this._orderidint=Utilities.getInt(order.get("orderidint").toString(),-1);
        this._entryorderidint=Utilities.getInt(order.get("entryorderidint"),-1);
        int id=Utilities.getInt(order.get("id"),-1);
        this._symbolBean=id>=0?Parameters.symbol.get(id):null;
        this._side=(order.get("side")!=null&&order.get("side")!="")?EnumOrderSide.valueOf(order.get("side").toString()):EnumOrderSide.UNDEFINED;
        this._orderType=(order.get("type")!=null&&order.get("type")!="")?EnumOrderType.valueOf(order.get("type").toString()):EnumOrderType.UNDEFINED;
        this._orderSize=Utilities.getInt(order.get("size"),0);
        this._limitPrice=Utilities.getDouble(order.get("limitprice"),0);
        this._triggerPrice=Utilities.getDouble(order.get("triggerprice"),0);
        this._ordReference=(order.get("orderref")!=null&&order.get("orderref")!="")?order.get("orderref").toString().toLowerCase():"NOTSPECIFIED";
        this._expireTime=Utilities.getInt(order.get("expiretime"),0);
        this._orderStage=(order.get("orderstage")!=null&&order.get("orderstage")!="")?EnumOrderStage.valueOf(order.get("orderstage").toString()):EnumOrderStage.UNDEFINED;
        this._dynamicOrderDuration=Utilities.getInt(order.get("dynamicorderduration"),0);
        this._maxSlippage=Utilities.getDouble(order.get("maxslippage"),0);
        this._firstLimitPrice=this._limitPrice;
        this._tag="";
        this._transmit=(order.get("transmit")!=null&&order.get("transmit")!="")?Boolean.valueOf(order.get("transmit").toString()):Boolean.TRUE;
        this._validity=(order.get("validity")!=null&&order.get("validity")!="")?order.get("validity").toString():"DAY";
        this._account="";
        this._scale=(order.get("scale")!=null&&order.get("scale")!="")?Boolean.valueOf(order.get("scale").toString()):Boolean.FALSE;
        this._reason=(order.get("reason")!=null&&order.get("reason")!="")?EnumOrderReason.valueOf(order.get("reason").toString()):EnumOrderReason.UNDEFINED;
        this._orderGroup=(order.get("ordergroup")!=null&&order.get("ordergroup")!="")?order.get("ordergroup").toString():null;
        this._effectiveFrom=(order.get("effectivefrom")!=null&&order.get("effectivefrom")!="")?order.get("effectivefrom").toString():null;
        this._log=(order.get("log")!=null&&order.get("log")!="")?order.get("log").toString():null;
        this._disclosedsize=order.get("disclosedsize")!=null?Utilities.getInt(order.get("disclosedsize").toString(),0):0;
    }

    static OrderEvent fastClose(BeanSymbol s,EnumOrderSide side,int size,String orderReference){
        OrderEvent e=new OrderEvent(new Object(),-1,-1,s,side,EnumOrderReason.REGULAREXIT,EnumOrderType.MKT,size,0,0,orderReference,0, EnumOrderStage.INIT, 0, 0D, true, "DAY", false,"","",null,"fastclose");
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
     * @return the _orderidint
     */
    public int getInternalorder() {
        return _orderidint;
    }

    /**
     * @param internalorder the _orderidint to set
     */
    public void setInternalorder(int internalorder) {
        this._orderidint = internalorder;
    }

    /**
     * @return the _entryorderidint
     */
    public int getInternalorderentry() {
        return _entryorderidint;
    }

    /**
     * @param internalorderentry the _entryorderidint to set
     */
    public void setInternalorderentry(int internalorderentry) {
        this._entryorderidint = internalorderentry;
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

    /**
     * @return the _log
     */
    public String getLog() {
        return _log;
    }

    /**
     * @param log the _log to set
     */
    public void setLog(String log) {
        this._log = log;
    }

    /**
     * @return the _disclosedsize
     */
    public int getDisclosedsize() {
        return _disclosedsize;
    }

    /**
     * @param disclosedsize the _disclosedsize to set
     */
    public void setDisclosedsize(int disclosedsize) {
        this._disclosedsize = disclosedsize;
    }
}
