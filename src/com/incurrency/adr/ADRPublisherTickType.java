/*
 * TickType.java
 *
 */
package com.incurrency.adr;

import java.util.logging.Logger;


public class ADRPublisherTickType {

    private static final Logger logger = Logger.getLogger(ADRPublisherTickType.class.getName());
    // constants - tick types
    public static final int D_ADVANCE       = 0;
    public static final int D_DECLINE       = 1;
    public static final int D_TOTAL_SYMB    = 2;
    public static final int D_ADVANCE_VOL   = 3;
    public static final int D_DECLINE_VOL   = 4;
    public static final int D_TOTAL_VOL     = 5;
    public static final int T_ADVANCE       = 6;
    public static final int T_DECLINE       = 7;
    public static final int T_TOTAL_SYMB    = 8;
    public static final int T_ADVANCE_VOL   = 9;
    public static final int T_DECLINE_VOL   = 10;
    public static final int T_TOTAL_VOL     = 11;
    public static final int D_ADR           = 10001;
    public static final int D_TRIN          = 10002;
    public static final int T_TICK          = 10003;
    public static final int T_TRIN          = 10004;
    public static final int INDEX           = 10005;


    public static String getField( int adrType) {
        switch( adrType) {
            case D_ADVANCE:                    return "dailyAdvance";
            case D_DECLINE:                    return "dailyDecline";
            case D_TOTAL_SYMB:                 return "dailyTotalSymbol";
            case D_ADVANCE_VOL:                return "dailyAdvanceVolume";
            case D_DECLINE_VOL:                return "dailyDeclineVolume";
            case D_TOTAL_VOL:                  return "dailyTotalVolume";
            case T_ADVANCE:                    return "tickAdvance";
            case T_DECLINE:                    return "tickDecline";
            case T_TOTAL_SYMB:                 return "tickTotalSymbol";
            case T_ADVANCE_VOL:                return "tickAdvanceVolume";
            case T_DECLINE_VOL:                return "tickDeclineVolume";
            case T_TOTAL_VOL:                  return "tickTotalVolume";                    
            default:                           return "unknown";
        }
    }
}