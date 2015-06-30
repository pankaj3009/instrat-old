/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

/**
 *
 * @author pankaj
 */
public class HistoricalDataRequest {
    String name;
    String fullname;
    String expiry;
    String startDate;
    String endDate;
    String closeReferenceDate;
    String periodicity;
    String type;
    String right;
    String strikePrice;
    String topic;

    public HistoricalDataRequest(String symbol,String type,String expiry, String right, String strikePrice,String startDate, String endDate,String closeReferenceDate, String periodicity) {
        this.name = symbol;
        this.type=type;
        this.expiry=expiry;
        this.right=right;
        this.strikePrice=strikePrice;
        this.startDate = startDate;
        this.endDate = endDate;
        this.closeReferenceDate=closeReferenceDate;
        this.periodicity = periodicity;
    }
    public HistoricalDataRequest(){
        
    }
    
}
