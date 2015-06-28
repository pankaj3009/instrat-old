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

    public Request(EnumSource source,int requestID,BeanSymbol symbol, EnumRequestType requestType,EnumBarSize barSize, EnumRequestStatus requestStatus,long requestTime) {
        this.requestID=requestID;
        this.symbol = symbol;
        this.requestType = requestType;
        this.barSize=barSize;
        this.source=source;
        this.requestStatus = requestStatus;
        this.requestTime=requestTime;
    }
    
        public Request(int requestID,BeanSymbol s,EnumBarSize barSize){
        this.requestID=requestID;
        this.symbol = s;
        this.barSize=barSize;
    }
    
}
