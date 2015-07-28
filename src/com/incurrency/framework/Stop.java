/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

/**
 *
 * @author pankaj
 */
public class Stop {
    EnumStopType stopType=EnumStopType.STOPLOSS;
    EnumStopMode stopMode=EnumStopMode.PERCENTAGE;
    double stopValue=1;
    boolean recalculate=false;
    double StopLevel;
    
    public Stop(){
        
    }
}
