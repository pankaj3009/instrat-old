/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author psharma
 */
public class OrderQueueKey {

    /**
     * @return the externalorderid
     */
    public int getExternalorderid() {
        return externalorderid;
    }

    /**
     * @param externalorderid the externalorderid to set
     */
    public void setExternalorderid(int externalorderid) {
        this.externalorderid = externalorderid;
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
        this.accountName = accountName;
    }

    private String accountName;
    private String strategy;
    private String parentDisplayName;
    private String childDisplayName;
    private int parentorderidint;
    private int childorderidint;
    private int externalorderid;

    public OrderQueueKey(String accountName, String strategy, String parentDisplayName,String childDisplayName, int parentorderidint,int childorderidint) {
        this.accountName = accountName;
        this.strategy = strategy;
        this.parentDisplayName = parentDisplayName;
        this.childDisplayName=childDisplayName;
        this.parentorderidint=parentorderidint;
        this.childorderidint=childorderidint;
    }

    public OrderQueueKey(String oqk) {
        String[] splitValue = oqk.split(":");
        //splitValue[0] will always be OQ
        switch (splitValue.length) {
            case 6:
                this.externalorderid=Utilities.getInt(splitValue[1],-1);
                this.accountName = splitValue[2];
                this.strategy = splitValue[3];
                this.parentDisplayName = splitValue[4];
                this.childDisplayName=splitValue[5];
                break;
            case 8:
                this.externalorderid=Utilities.getInt(splitValue[1],-1);
                this.accountName = splitValue[2];
                this.strategy = splitValue[3];
                this.parentDisplayName = splitValue[4];
                this.childDisplayName=splitValue[5];
                this.parentorderidint=Utilities.getInt(splitValue[6],-1);
                this.childorderidint=Utilities.getInt(splitValue[7],-1);
                break;            
            default:
                this.accountName="UNDEFINED";
                break;
        }
    }

    /**
     * @return the parentDisplayName
     */
    public String getParentDisplayName() {
        return parentDisplayName;
    }

    /**
     * @param parentDisplayName the parentDisplayName to set
     */
    public void setParentDisplayName(String parentDisplayName) {
        this.parentDisplayName = parentDisplayName;
    }

    /**
     * @return the childDisplayName
     */
    public String getChildDisplayName() {
        return childDisplayName;
    }

    /**
     * @param childDisplayName the childDisplayName to set
     */
    public void setChildDisplayName(String childDisplayName) {
        this.childDisplayName = childDisplayName;
    }

    /**
     * @return the parentorderidint
     */
    public int getParentorderidint() {
        return parentorderidint;
    }

    /**
     * @param parentorderidint the parentorderidint to set
     */
    public void setParentorderidint(int parentorderidint) {
        this.parentorderidint = parentorderidint;
    }

    /**
     * @return the childorderidint
     */
    public int getChildorderidint() {
        return childorderidint;
    }

    /**
     * @param childorderidint the childorderidint to set
     */
    public void setChildorderidint(int childorderidint) {
        this.childorderidint = childorderidint;
    }
}
