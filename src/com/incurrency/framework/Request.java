/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class Request {
    int requestID;
    BeanSymbol symbol;
    EnumRequestType requestType;
    EnumBarSize barSize;
    EnumSource source;
    EnumRequestStatus requestStatus;
    long requestTime;
    String accountName;

    public Request(EnumSource source,int requestID,BeanSymbol symbol, EnumRequestType requestType,EnumBarSize barSize, EnumRequestStatus requestStatus,long requestTime,String accountName) {
        this.requestID=requestID;
        this.symbol = symbol;
        this.requestType = requestType;
        this.barSize=barSize;
        this.source=source;
        this.requestStatus = requestStatus;
        this.requestTime=requestTime;
        this.accountName=accountName;
    }
    
        public Request(int requestID,BeanSymbol s,EnumBarSize barSize, String accountName){
        this.requestID=requestID;
        this.symbol = s;
        this.barSize=barSize;
        this.accountName=accountName;
    }
    
}
