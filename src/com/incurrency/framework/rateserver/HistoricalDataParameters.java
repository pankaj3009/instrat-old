/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework.rateserver;

/**
 *
 * @author pankaj
 */
public class HistoricalDataParameters {
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
    String metric;

    public HistoricalDataParameters(String displayName, String periodicity,String startDate, String endDate,String closeReferenceDate,String metric) {
        this.displayName=displayName;
        String[] symbol=displayName.split("_");
        this.name=symbol[0]==null||symbol[0].equalsIgnoreCase("null")?"":symbol[0];
        this.type=symbol[1]==null||symbol[1].equalsIgnoreCase("null")?"":symbol[1];
        this.expiry=symbol.length<=2 || (symbol[2]==null||symbol[2].equalsIgnoreCase("null"))?"":symbol[2];
        this.right=symbol.length<=3 || (symbol[3]==null||symbol[3].equalsIgnoreCase("null"))?"":symbol[3];
        this.strikePrice=symbol.length<=4 || (symbol[4]==null||symbol[4].equalsIgnoreCase("null"))?"":symbol[4];
        this.startDate = startDate;
        this.endDate = endDate;
        this.closeReferenceDate=closeReferenceDate;
        this.periodicity = periodicity;
        this.metric=metric;
    }
    public HistoricalDataParameters(){
        
    }
    
}
