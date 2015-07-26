/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Trade {

    //public static ExtendedHashMap <String,String,String> trades=new ExtendedHashMap<>();
    private static final Logger logger = Logger.getLogger(Trade.class.getName());

    public Trade() {
    }

    public Trade(ExtendedHashMap <String,String,String> trades,String[] input) {     
        //the names are all display names
        ConcurrentHashMap<String, String> tr;
        if (trades.get(input[6]) != null) {
            tr = (ConcurrentHashMap<String,String>)trades.get(input[6]);
        } else {
            tr = new ConcurrentHashMap<>();
        }
        tr.put("entrysymbol", input[0]);
        tr.put("parentsymbol", input[1]);
        tr.put("entrysymbolid", String.valueOf(Utilities.getIDFromDisplayName(Parameters.symbol,input[0])));
        tr.put("entryside", String.valueOf(input[2].equals("")?EnumOrderSide.UNDEFINED:EnumOrderSide.valueOf(input[2])));
        tr.put("entryprice", input[3]);
        tr.put("entrysize", input[4]);
        tr.put("entrytime", input[5]);
        tr.put("entryorderidint", input[6]);
        tr.put("entryorderidext", input[7]);
        tr.put("entryreason", String.valueOf((input[8].equals("")||input[8]==null)?EnumOrderReason.UNDEFINED: EnumOrderReason.valueOf(input[8])));
        tr.put("entrybrokerage", String.valueOf((input[9].equals("")||input[9]==null)?0D:Double.parseDouble(input[9])));
        
        tr.put("exitsymbol", input[10]!=null?input[10]:"");
        tr.put("exitsymbolid", String.valueOf(Utilities.getIDFromDisplayName(Parameters.symbol,input[10])));
        tr.put("exitside", String.valueOf((input[11].equals("")||input[11]==null)?EnumOrderSide.UNDEFINED:EnumOrderSide.valueOf(input[11])));
        tr.put("exitprice", String.valueOf((input[12].equals("")||input[12]==null)?0D:Double.parseDouble(input[12])));
        tr.put("exitsize", String.valueOf((input[13].equals("")||input[13]==null)?0:Integer.parseInt(input[13])));
        tr.put("exittime", String.valueOf(input[14]!=null?input[14]:""));
        tr.put("exitorderidint", String.valueOf((input[15].equals("")||input[15]==null)?-1:Integer.parseInt(input[15])));
        tr.put("exitorderidext", input[16]);
        tr.put("exitreason", String.valueOf((input[17].equals("")||input[17]==null)?EnumOrderReason.UNDEFINED: EnumOrderReason.valueOf(input[17])));
        tr.put("exitbrokerage", String.valueOf((input[18].equals("")||input[18]==null)?0D:Double.parseDouble(input[18])));

        tr.put("mtmtoday", String.valueOf(input[19].equals("")?0D:Double.parseDouble(input[19])));
        tr.put("mtmyesterday", String.valueOf(input[19].equals("")?0D:Double.parseDouble(input[19])));
        tr.put("mtmpriormonth", String.valueOf(input[21].equals("")?0D:Double.parseDouble(input[21])));
        tr.put("todaydate", String.valueOf(input[22]));
        tr.put("yesterdaydate", String.valueOf(input[23]));
        tr.put("accountname", input[24]);
        trades.put(input[7], tr);
    }

    public  Trade(ExtendedHashMap <String,String,String> trades,int id, int parentid, EnumOrderReason reason,EnumOrderSide side, double price, int size, int internalid,int orderid,int parentorderid,String timeZone,String accountName){
        ConcurrentHashMap<String, String> tr;
        if (trades.get(String.valueOf(internalid)) != null) {
            tr = (ConcurrentHashMap<String,String>)trades.get(String.valueOf(internalid));
        } else {
            tr = new ConcurrentHashMap<>();
        }
        tr.put("entrysymbol", Parameters.symbol.get(id).getDisplayname());
        tr.put("parentsymbol", Parameters.symbol.get(parentid).getDisplayname());
        tr.put("entrysymbolid", String.valueOf(id));
        tr.put("entryside", String.valueOf(side));
        tr.put("entryprice", String.valueOf(price));
        tr.put("entrysize", String.valueOf(size));
        String entryTime;
        if(timeZone.compareTo("")==0){
            entryTime=DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getDefault());
        } else {
            entryTime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        }
        tr.put("entryreason", reason.toString());
        tr.put("entrytime", entryTime);
        tr.put("entryorderidint", String.valueOf(internalid));
        tr.put("entryorderidext", String.valueOf(orderid));
        tr.put("parententryorderidint", String.valueOf(parentorderid));
        tr.put("accountname", accountName);
        trades.put(String.valueOf(internalid), tr);
    }
/*
    public static void updateEntry(ExtendedHashMap <String,String,String> trades,int id, EnumOrderSide side, double price, int size, int internalid, int orderid, String timeZone, String accountName) {
     
        ConcurrentHashMap<String, String> tr;
        if (trades.get(String.valueOf(internalid)) != null) {
            tr = (ConcurrentHashMap<String,String>)trades.get(String.valueOf(internalid));
        } else {
            tr = new ConcurrentHashMap<>();
        }
        tr.put("entrysymbol", Parameters.symbol.get(id).getDisplayname());
        tr.put("entrysymbolid", String.valueOf(id));
        tr.put("entryside", String.valueOf(side));
        tr.put("entryprice", String.valueOf(price));
        tr.put("entrysize", String.valueOf(size));
        String entryTime;
        if(timeZone.compareTo("")==0){
            entryTime=DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getDefault());
        } else {
            entryTime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        }
        
        tr.put("entrytime", entryTime);
        tr.put("entryorderidint", String.valueOf(internalid));
        tr.put("entryorderidext", String.valueOf(orderid));
        tr.put("accountname", accountName);
        trades.put(String.valueOf(internalid), tr);
    }
*/
    public static void updateExit(ExtendedHashMap <String,String,String> trades,int id,EnumOrderReason reason, EnumOrderSide side, double price, int size, int internalid, int orderid, int parentorderid,int entryidint,String timeZone, String accountName) {
        ConcurrentHashMap<String, String> tr;
        if (trades.get(String.valueOf(internalid)) != null) {
            tr = (ConcurrentHashMap<String,String>)trades.get(String.valueOf(internalid));
        } else {
            tr = new ConcurrentHashMap<>();
        }

        tr.put("exitsymbol", Parameters.symbol.get(id).getDisplayname());
        tr.put("exitsymbolid", String.valueOf(id));

        tr.put("exitside", String.valueOf(side));
        tr.put("exitprice", String.valueOf(price));
        tr.put("exitsize", String.valueOf(size));
        String exitTime;
        if (timeZone.compareTo("") == 0) {
            exitTime=DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getDefault());
        } else {
            exitTime=DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        }
        tr.put("exittime", String.valueOf(exitTime));
        tr.put("exitorderidint", String.valueOf(internalid));
        tr.put("exitorderidext", String.valueOf(orderid));
        tr.put("exitorderidex", String.valueOf(parentorderid));
        tr.put("accountname", accountName);
        tr.put("exitreason", String.valueOf(reason));
        trades.put(String.valueOf(entryidint), tr);
    }
  

    /**
     * @return the entrySymbol
     */
    public static String getEntrySymbol(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return trades.get(internalOrderID.toString(), "entrysymbol");
    }

    /**
     * @param entrySymbol the entrySymbol to set
     */
    public static void setEntrySymbol(ExtendedHashMap <String,String,String> trades,Object internalOrderID,String entrySymbol) {
        trades.add(internalOrderID.toString(), "entrysymbol", entrySymbol);
    }

    /**
     * @return the entrySide
     */
    public static EnumOrderSide getEntrySide(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return EnumOrderSide.valueOf(trades.get(internalOrderID.toString(), "entryside"));
    }

    /**
     * @param entrySide the entrySide to set
     */
    public static void setEntrySide(ExtendedHashMap <String,String,String> trades,Object internalOrderID,EnumOrderSide entrySide) {
        trades.add(internalOrderID.toString(), "entryside", entrySide.toString());
    }
    /*
     public void setEntrySide(String entrySide){
     this.entrySide=EnumOrderSide.valueOf(entrySide);
     }
     */

    /**
     * @return the entryPrice
     */
    public static double getEntryPrice(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Double.valueOf(trades.get(internalOrderID.toString(), "entryprice"));
    }

    /**
     * @param entryPrice the entryPrice to set
     */
    public static void setEntryPrice(ExtendedHashMap <String,String,String> trades,Object internalOrderID,double entryPrice) {
        trades.add(internalOrderID.toString(), "entryprice", String.valueOf(entryPrice));
    }

    /**
     * @return the entrySize
     */
    public static int getEntrySize(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "entrysize"));

    }

    /**
     * @param entrySize the entrySize to set
     */
    public static void setEntrySize(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int entrySize) {
            trades.add(internalOrderID.toString(), "entrysize", String.valueOf(entrySize));    }

    /**
     * @return the entryTime
     */
    public static String getEntryTime(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return trades.get(internalOrderID.toString(), "entrytime");
    }

    /**
     * @param entryTime the entryTime to set
     */
    public static void setEntryTime(ExtendedHashMap <String,String,String> trades,Object internalOrderID,String entryTime) {
            trades.add(internalOrderID.toString(), "entrytime", entryTime);    }

    /**
     * @return the entryID
     */
    public static int getEntryOrderIDInternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "entryorderidint"));

    }

    /**
     * @param entryID the entryID to set
     */
    public static void setEntryOrderIDInternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int entryID) {
            trades.add(internalOrderID.toString(), "entryorderidint", String.valueOf(entryID));
    }

    /**
     * @return the exitSymbol
     */
    public static String getExitSymbol(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return trades.get(internalOrderID.toString(), "exitsymbol");

    }

    /**
     * @param exitSymbol the exitSymbol to set
     */
    public static void setExitSymbol(ExtendedHashMap <String,String,String> trades,Object internalOrderID,String exitSymbol) {
        trades.add(internalOrderID.toString(), "exitsymbol", exitSymbol);
    }

    /**
     * @return the exitSide
     */
    public static EnumOrderSide getExitSide(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return EnumOrderSide.valueOf(trades.get(internalOrderID.toString(), "exitside"));
    }

    /**
     * @param exitSide the exitSide to set
     */
    public static void setExitSide(ExtendedHashMap <String,String,String> trades,Object internalOrderID,EnumOrderSide exitSide) {
        trades.add(internalOrderID.toString(), "exitSide", exitSide.toString());
    }
    /*
     public void setExitSide(String exitSide){
     this.exitSide=EnumOrderSide.valueOf(exitSide);
     }
     */

    /**
     * @return the exitPrice
     */
    public static double getExitPrice(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
         return Double.valueOf(trades.get(internalOrderID.toString(), "exitprice"));
   }

    /**
     * @param exitPrice the exitPrice to set
     */
    public static void setExitPrice(ExtendedHashMap <String,String,String> trades,Object internalOrderID,double exitPrice) {
        trades.add(internalOrderID.toString(), "exitprice", String.valueOf(exitPrice));
    }

    /**
     * @return the exitSize
     */
    public static int getExitSize(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "exitside"));
    }

    /**
     * @param exitSize the exitSize to set
     */
    public static void setExitSize(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int exitSize) {
        trades.add(internalOrderID.toString(), "exitsize", String.valueOf(exitSize));    
    }

    /**
     * @return the exitTime
     */
    public static String getExitTime(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return trades.get(internalOrderID.toString(), "exittime");
    }

    /**
     * @param exitTime the exitTime to set
     */
    public static void setExitTime(ExtendedHashMap <String,String,String> trades,Object internalOrderID, String exitTime) {
        trades.add(internalOrderID.toString(), "exittime", exitTime);
    }

    /**
     * @return the exitID
     */
    public static int getExitOrderIDInternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "exitorderidint"));
    }

    /**
     * @param exitID the exitID to set
     */
    public static void setExitOrderIDInternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int exitID) {
        trades.add(internalOrderID.toString(), "exitorderidint", String.valueOf(exitID));
    }


    /**
     * @return the exitBrokerage
     */
    public static double getExitBrokerage(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Double.valueOf(trades.get(internalOrderID.toString(), "exitbrokerage"));
    }

    /**
     * @param exitBrokerage the exitBrokerage to set
     */
    public static void setExitBrokerage(ExtendedHashMap <String,String,String> trades,Object internalOrderID,double exitBrokerage) {
        trades.add(internalOrderID.toString(), "exitbrokerage", String.valueOf(exitBrokerage));
    }

    /**
     * @return the entryBrokerage
     */
    public static double getEntryBrokerage(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Double.valueOf(trades.get(internalOrderID.toString(), "entrybrokerage"));
    }

    /**
     * @param entryBrokerage the entryBrokerage to set
     */
    public static void setEntryBrokerage(ExtendedHashMap <String,String,String> trades,Object internalOrderID,double entryBrokerage) {
trades.add(internalOrderID.toString(), "entrybrokerage", String.valueOf(entryBrokerage));
    }

    /**
     * @return the accountName
     */
    public static String getAccountName(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return trades.get(internalOrderID.toString(), "accountname");
    }

    /**
     * @param accountName the accountName to set
     */
    public static void setAccountName(ExtendedHashMap <String,String,String> trades,Object internalOrderID,String accountName) {
    trades.add(internalOrderID.toString(), "accountname", accountName);
    }

    /**
     * @return the mtmToday
     */
    public static double getMtmToday(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Double.valueOf(trades.get(internalOrderID.toString(), "mtmtoday"));

    }

    /**
     * @param mtmToday the mtmToday to set
     */
    public static void setMtmToday(ExtendedHashMap <String,String,String> trades,Object internalOrderID,double mtmToday) {
trades.add(internalOrderID.toString(), "mtmtoday", String.valueOf(mtmToday));
    }

    /**
     * @return the mtmYesterday
     */
    public static double getMtmYesterday(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Double.valueOf(trades.get(internalOrderID.toString(), "mtmyesterday"));
    }

    /**
     * @param mtmYesterday the mtmYesterday to set
     */
    public static void setMtmYesterday(ExtendedHashMap <String,String,String> trades,Object internalOrderID,double mtmYesterday) {
     trades.add(internalOrderID.toString(), "mtmyesterday", String.valueOf(mtmYesterday));
    }

    /**
     * @return the mtmPriorMonth
     */
    public static double getMtmPriorMonth(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Double.valueOf(trades.get(internalOrderID.toString(), "mtmpriormonth"));
    }

    /**
     * @param mtmPriorMonth the mtmPriorMonth to set
     */
    public static void setMtmPriorMonth(ExtendedHashMap <String,String,String> trades,Object internalOrderID,double mtmPriorMonth) {
    trades.add(internalOrderID.toString(), "mtmpriormonth", String.valueOf(mtmPriorMonth));
    }

    /**
     * @return the todayDate
     */
    public static String getTodayDate(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return trades.get(internalOrderID.toString(), "todaydate");
    }

    /**
     * @param todayDate the todayDate to set
     */
    public static void setTodayDate(ExtendedHashMap <String,String,String> trades,Object internalOrderID,String todayDate) {
        trades.add(internalOrderID.toString(), "todaydate", todayDate);    }

    /**
     * @return the yesterdayDate
     */
    public static String getYesterdayDate(ExtendedHashMap <String,String,String> trades,String internalOrderID) {
        return trades.get(internalOrderID.toString(), "yesterdaydate");
    }

    /**
     * @param yesterdayDate the yesterdayDate to set
     */
    public static void setYesterdayDate(ExtendedHashMap <String,String,String> trades,Object internalOrderID,String yesterdayDate) {
        trades.add(internalOrderID.toString(), "yesterdayday", yesterdayDate);    }

    /**
     * @return the exitOrderID
     */
    public static int getExitOrderIDExternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "exitorderidext"));
    }

    /**
     * @param exitOrderID the exitOrderID to set
     */
    public static void setExitOrderIDExternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int exitOrderID) {
trades.add(internalOrderID.toString(), "exitorderidext", String.valueOf(exitOrderID));
    }

    /**
     * @return the entryOrderID
     */
    public static int getEntryOrderIDExternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "entryorderidext"));
    }

    /**
     * @param entryOrderID the entryOrderID to set
     */
    public static void setEntryOrderIDExternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int entryOrderID) {
trades.add(internalOrderID.toString(), "entryorderidext", String.valueOf(entryOrderID));    }

    /**
     * @return the entrySymbolID
     */
    public static int getEntrySymbolID(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "entrysymbolid"));
    }

    /**
     * @param entrySymbolID the entrySymbolID to set
     */
    public static void setEntrySymbolID(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int entrySymbolID) {
     trades.add(internalOrderID.toString(), "entrysymbolid", String.valueOf(entrySymbolID));
    }

    /**
     * @return the exitSymbolID
     */
    public static int getExitSymbolID(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "exitsymbolid"));
    }

    /**
     * @param exitSymbolID the exitSymbolID to set
     */
    public static void setExitSymbolID(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int exitSymbolID) {
    trades.add(internalOrderID.toString(), "exitsymbolid", String.valueOf(exitSymbolID));
    }

    /**
     * @return the parentSymbol
     */
    public static String getParentSymbol(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return trades.get(internalOrderID.toString(), "parentsymbol");
    }

    /**
     * @param parentSymbol the parentSymbol to set
     */
    public static void setParentSymbol(ExtendedHashMap <String,String,String> trades,Object internalOrderID,String parentSymbol) {
trades.add(internalOrderID.toString(), "parentsymbol", parentSymbol);
    }

    /**
     * @return the exitReason
     */
    public static EnumOrderReason getExitReason(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return EnumOrderReason.valueOf(trades.get(internalOrderID.toString(), "exitreason"));
    }

    /**
     * @param exitReason the exitReason to set
     */
    public static void setExitReason(ExtendedHashMap <String,String,String> trades,Object internalOrderID,EnumOrderReason exitReason) {
    trades.add(internalOrderID.toString(), "exitreason", exitReason.toString());
    }

    /**
     * @return the entryReason
     */
    public static EnumOrderReason getEntryReason(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return EnumOrderReason.valueOf(trades.get(internalOrderID.toString(), "entryreason"));
    }

    /**
     * @param entryReason the entryReason to set
     */
    public static void setEntryReason(ExtendedHashMap <String,String,String> trades,Object internalOrderID,EnumOrderReason entryReason) {
        trades.add(internalOrderID.toString(), "entryreason", entryReason.toString());    
    }
    
     public static void setParentEntryOrderIDInternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int orderid) {
        trades.add(internalOrderID.toString(), "parententryorderidint", String.valueOf(orderid));    
    }

     public static int getParentEntryOrderIDInternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "parententryorderidint"));    
    }

     public static void setParentExitOrderIDInternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID,int orderid) {
        trades.add(internalOrderID.toString(), "parentexitorderidint", String.valueOf(orderid));    
    }

     public static int getParentExitOrderIDInternal(ExtendedHashMap <String,String,String> trades,Object internalOrderID) {
        return Integer.valueOf(trades.get(internalOrderID.toString(), "parentexitorderidint"));    
    }
}
