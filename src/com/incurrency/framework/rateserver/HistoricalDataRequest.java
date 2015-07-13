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
    String displayName;
    String expiry;
    String startDate;
    String endDate;
    String closeReferenceDate;
    String periodicity;
    String type;
    String right;
    String strikePrice;
    String topic;

    public HistoricalDataRequest(String displayName,String startDate, String endDate,String closeReferenceDate, String periodicity) {
        this.displayName=displayName;
        String[] symbol=displayName.split("_");
        this.name=symbol[0]==null||symbol[0].equalsIgnoreCase("null")?"":symbol[0];
        this.type=symbol[1]==null||symbol[1].equalsIgnoreCase("null")?"":symbol[1];
        this.expiry=symbol[2]==null||symbol[2].equalsIgnoreCase("null")?"":symbol[2];
        this.right=symbol[3]==null||symbol[3].equalsIgnoreCase("null")?"":symbol[3];
        this.strikePrice=symbol[4]==null||symbol[4].equalsIgnoreCase("null")?"":symbol[4];
        this.startDate = startDate;
        this.endDate = endDate;
        this.closeReferenceDate=closeReferenceDate;
        this.periodicity = periodicity;
    }
    public HistoricalDataRequest(){
        
    }
    
}
