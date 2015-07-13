/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Trade implements ReaderWriterInterface {

    private String entrySymbol = "";
    private int entrySymbolID = -1;
    private String parentSymbol="";
    private EnumOrderSide entrySide = EnumOrderSide.UNDEFINED;
    private double entryPrice;
    private int entrySize;
    private String entryTime = "";
    private int entryID;
    private int entryOrderID;
    private EnumOrderReason entryReason;
    private double entryBrokerage;
    private boolean filtered;
    private String exitSymbol = "";
    private int exitSymbolID = -1;
    private EnumOrderSide exitSide = EnumOrderSide.UNDEFINED;
    private double exitPrice;
    private int exitSize;
    private String exitTime = "";
    private int exitID;
    private int exitOrderID;
    private EnumOrderReason exitReason=EnumOrderReason.UNDEFINED;;
    private double exitBrokerage;
    private double mtmToday;
    private double mtmYesterday;
    private double mtmPriorMonth;
    private String todayDate = "";
    private String yesterdayDate = "";
    private String accountName = "";
    private static final Logger logger = Logger.getLogger(Trade.class.getName());

    public Trade() {
    }

    public Trade(String[] input) {
        this.entrySymbol = input[0];
        this.parentSymbol = input[1];
        //this.entrySymbolID=Integer.parseInt(input[1]);
        this.entrySymbolID=TradingUtil.getEntryIDFromDisplayName(this,Parameters.symbol);
        this.entrySide = input[2].equals("")?EnumOrderSide.UNDEFINED:EnumOrderSide.valueOf(input[2]);
        this.entryPrice = Double.parseDouble(input[3]);
        this.entrySize = Integer.parseInt(input[4]);
        this.entryTime = input[5];
        this.entryID = Integer.parseInt(input[6]);
        this.entryOrderID=Integer.parseInt(input[7]);
        this.entryReason=(input[8].equals("")||input[8]==null)?EnumOrderReason.UNDEFINED: EnumOrderReason.valueOf(input[8]);
        this.entryBrokerage = (input[9].equals("")||input[9]==null)?0D:Double.parseDouble(input[9]);
        this.exitSymbol = input[10]!=null?input[10]:"";
        this.exitSymbolID=TradingUtil.getExitIDFromSymbol(this);
        this.exitSide = (input[11].equals("")||input[11]==null)?EnumOrderSide.UNDEFINED:EnumOrderSide.valueOf(input[11]);
        this.exitPrice = (input[12].equals("")||input[12]==null)?0D:Double.parseDouble(input[12]);
        this.exitSize =  (input[13].equals("")||input[13]==null)?0:Integer.parseInt(input[13]);
        this.exitTime = input[14]!=null?input[14]:"";
        this.exitID =  (input[15].equals("")||input[15]==null)?-1:Integer.parseInt(input[15]);
        this.exitOrderID=Integer.parseInt(input[16]);
        this.exitReason=(input[17].equals("")||input[17]==null)?EnumOrderReason.UNDEFINED: EnumOrderReason.valueOf(input[17]);
        this.exitBrokerage = (input[18].equals("")||input[18]==null)?0D:Double.parseDouble(input[18]);
        this.mtmToday=input[19].equals("")?0D:Double.parseDouble(input[19]);
        this.mtmYesterday=input[20].equals("")?0D:Double.parseDouble(input[20]);
        this.mtmPriorMonth=input[21].equals("")?0D:Double.parseDouble(input[21]);
        this.todayDate=input[22];
        this.yesterdayDate=input[23];
        this.accountName = input[24];
    }


    public Trade(int id, int parentid, EnumOrderReason reason,EnumOrderSide side, double price, int size, int internalid,int orderid,String timeZone,String accountName){
        this.entrySymbol=Parameters.symbol.get(id).getHappyName();
        this.parentSymbol=Parameters.symbol.get(parentid).getBrokerSymbol();
        this.entryReason=reason;
        this.entrySymbolID=id;
        this.entrySide=side;
        this.entryPrice=price;
        this.entrySize=size;
                if(timeZone.compareTo("")==0){
            this.entryTime=DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getDefault());
        } else {
            this.entryTime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        }
        this.entryID = internalid;
        this.entryOrderID = orderid;
        this.accountName = accountName;
    }

    public void updateEntry(int id, EnumOrderSide side, double price, int size, int internalid, int orderid, String timeZone, String accountName) {
        
        this.setEntrySymbol(Parameters.symbol.get(id).getHappyName());
        this.setEntrySymbolID(id);
        this.setEntrySide(side);
        this.setEntryPrice(price);
        this.setEntrySize(size);
        if (timeZone.compareTo("") == 0) {
            this.setEntryTime(DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getDefault()));
        } else {
            this.setEntryTime(DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone)));
        }
        this.setEntryID(internalid);
        this.setEntryOrderID(orderid);
        this.setAccountName(accountName);
    }

    public void updateExit(int id,EnumOrderReason reason, EnumOrderSide side, double price, int size, int internalid, int orderid, String timeZone, String accountName) {
        this.setExitSymbol(Parameters.symbol.get(id).getHappyName());
        this.setExitSymbolID(id);
        this.exitReason=reason;
        this.setExitSide(side);
        this.setExitPrice(price);
        this.setExitSize(size);
        if (timeZone.compareTo("") == 0) {
            this.setExitTime(DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getDefault()));
        } else {
            this.setExitTime(DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone)));
        }
        this.setExitID(internalid);
        this.setExitOrderID(orderid);
        this.setAccountName(accountName);
    }
   
    @Override
    public void reader(String inputfile, ArrayList target) {
        File inputFile = new File(inputfile);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> existingTradesLoad = Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
                existingTradesLoad.remove(0);//remove header
                for (String Trade : existingTradesLoad) {
                    if (!Trade.equals("")) {//attempt to split if its not a blank line
                        String[] input = Trade.split(",");
                        target.add(new Trade(input));
                    }
                }
            } catch (Exception ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }
    }

    @Override
    public void writer(String fileName) {
        File f = new File(fileName);

        try {

            if (!f.exists() || f.isDirectory()) {
                String header = "EntrySymbol,ParentSymbol,EntrySide,EntryPrice,EntrySize,EntryTime,EntryID,EntryOrderID,EntryReason,EntryBrokerage,ExitSymbol,ExitSide,ExitPrice,ExitSize,ExitTime,ExitID,ExitOrderID,ExitReason,ExitBrokerage,MTMToday,MTMYesterday,MTMPriorMonth,TodayDate, YesterdayDate,AccountName";
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
                out.println(header);
                out.close();
            }

            String data = entrySymbol + "," + getParentSymbol()+ "," + entrySide + "," + entryPrice + "," + entrySize + "," + entryTime + "," + entryID + "," + entryOrderID + "," + entryReason+","+entryBrokerage + "," + exitSymbol + "," + exitSide + "," + exitPrice + "," + exitSize + "," + exitTime + "," + exitID + "," + exitOrderID + "," +exitReason+","+ exitBrokerage + "," + mtmToday + "," + mtmYesterday + "," + mtmPriorMonth + "," + todayDate + "," + yesterdayDate + "," + accountName;
            PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
            out.println(data);
            out.close();

        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
    }

    /**
     * @return the entrySymbol
     */
    public String getEntrySymbol() {
        return entrySymbol;
    }

    /**
     * @param entrySymbol the entrySymbol to set
     */
    public void setEntrySymbol(String entrySymbol) {
        this.entrySymbol = entrySymbol;
    }

    /**
     * @return the entrySide
     */
    public EnumOrderSide getEntrySide() {
        return entrySide;
    }

    /**
     * @param entrySide the entrySide to set
     */
    public void setEntrySide(EnumOrderSide entrySide) {
        this.entrySide = entrySide;
    }
    /*
     public void setEntrySide(String entrySide){
     this.entrySide=EnumOrderSide.valueOf(entrySide);
     }
     */

    /**
     * @return the entryPrice
     */
    public double getEntryPrice() {
        return entryPrice;
    }

    /**
     * @param entryPrice the entryPrice to set
     */
    public void setEntryPrice(double entryPrice) {
        this.entryPrice = entryPrice;
    }

    /**
     * @return the entrySize
     */
    public int getEntrySize() {
        return entrySize;
    }

    /**
     * @param entrySize the entrySize to set
     */
    public void setEntrySize(int entrySize) {
        this.entrySize = entrySize;
    }

    /**
     * @return the entryTime
     */
    public String getEntryTime() {
        return entryTime;
    }

    /**
     * @param entryTime the entryTime to set
     */
    public void setEntryTime(String entryTime) {
        this.entryTime = entryTime;
    }

    /**
     * @return the entryID
     */
    public int getEntryID() {
        return entryID;
    }

    /**
     * @param entryID the entryID to set
     */
    public void setEntryID(int entryID) {
        this.entryID = entryID;
    }

    /**
     * @return the exitSymbol
     */
    public String getExitSymbol() {
        return exitSymbol;
    }

    /**
     * @param exitSymbol the exitSymbol to set
     */
    public void setExitSymbol(String exitSymbol) {
        this.exitSymbol = exitSymbol;
    }

    /**
     * @return the exitSide
     */
    public EnumOrderSide getExitSide() {
        return exitSide;
    }

    /**
     * @param exitSide the exitSide to set
     */
    public void setExitSide(EnumOrderSide exitSide) {
        this.exitSide = exitSide;
    }
    /*
     public void setExitSide(String exitSide){
     this.exitSide=EnumOrderSide.valueOf(exitSide);
     }
     */

    /**
     * @return the exitPrice
     */
    public double getExitPrice() {
        return exitPrice;
    }

    /**
     * @param exitPrice the exitPrice to set
     */
    public void setExitPrice(double exitPrice) {
        this.exitPrice = exitPrice;
    }

    /**
     * @return the exitSize
     */
    public int getExitSize() {
        return exitSize;
    }

    /**
     * @param exitSize the exitSize to set
     */
    public void setExitSize(int exitSize) {
        this.exitSize = exitSize;
    }

    /**
     * @return the exitTime
     */
    public String getExitTime() {
        return exitTime;
    }

    /**
     * @param exitTime the exitTime to set
     */
    public void setExitTime(String exitTime) {
        this.exitTime = exitTime;
    }

    /**
     * @return the exitID
     */
    public int getExitID() {
        return exitID;
    }

    /**
     * @param exitID the exitID to set
     */
    public void setExitID(int exitID) {
        this.exitID = exitID;
    }

    /**
     * @return the filtered
     */
    public boolean isFiltered() {
        return filtered;
    }

    /**
     * @param filtered the filtered to set
     */
    public void setFiltered(boolean filtered) {
        this.filtered = filtered;
    }

    /**
     * @return the exitBrokerage
     */
    public double getExitBrokerage() {
        return exitBrokerage;
    }

    /**
     * @param exitBrokerage the exitBrokerage to set
     */
    public void setExitBrokerage(double exitBrokerage) {
        this.exitBrokerage = exitBrokerage;
    }

    /**
     * @return the entryBrokerage
     */
    public double getEntryBrokerage() {
        return entryBrokerage;
    }

    /**
     * @param entryBrokerage the entryBrokerage to set
     */
    public void setEntryBrokerage(double entryBrokerage) {
        this.entryBrokerage = entryBrokerage;
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

    /**
     * @return the mtmToday
     */
    public double getMtmToday() {
        return mtmToday;
    }

    /**
     * @param mtmToday the mtmToday to set
     */
    public void setMtmToday(double mtmToday) {
        this.mtmToday = mtmToday;
    }

    /**
     * @return the mtmYesterday
     */
    public double getMtmYesterday() {
        return mtmYesterday;
    }

    /**
     * @param mtmYesterday the mtmYesterday to set
     */
    public void setMtmYesterday(double mtmYesterday) {
        this.mtmYesterday = mtmYesterday;
    }

    /**
     * @return the mtmPriorMonth
     */
    public double getMtmPriorMonth() {
        return mtmPriorMonth;
    }

    /**
     * @param mtmPriorMonth the mtmPriorMonth to set
     */
    public void setMtmPriorMonth(double mtmPriorMonth) {
        this.mtmPriorMonth = mtmPriorMonth;
    }

    /**
     * @return the todayDate
     */
    public String getTodayDate() {
        return todayDate;
    }

    /**
     * @param todayDate the todayDate to set
     */
    public void setTodayDate(String todayDate) {
        this.todayDate = todayDate;
    }

    /**
     * @return the yesterdayDate
     */
    public String getYesterdayDate() {
        return yesterdayDate;
    }

    /**
     * @param yesterdayDate the yesterdayDate to set
     */
    public void setYesterdayDate(String yesterdayDate) {
        this.yesterdayDate = yesterdayDate;
    }

    /**
     * @return the exitOrderID
     */
    public int getExitOrderID() {
        return exitOrderID;
    }

    /**
     * @param exitOrderID the exitOrderID to set
     */
    public void setExitOrderID(int exitOrderID) {
        this.exitOrderID = exitOrderID;
    }

    /**
     * @return the entryOrderID
     */
    public int getEntryOrderID() {
        return entryOrderID;
    }

    /**
     * @param entryOrderID the entryOrderID to set
     */
    public void setEntryOrderID(int entryOrderID) {
        this.entryOrderID = entryOrderID;
    }

    /**
     * @return the entrySymbolID
     */
    public int getEntrySymbolID() {
        return entrySymbolID;
    }

    /**
     * @param entrySymbolID the entrySymbolID to set
     */
    public void setEntrySymbolID(int entrySymbolID) {
        this.entrySymbolID = entrySymbolID;
    }

    /**
     * @return the exitSymbolID
     */
    public int getExitSymbolID() {
        return exitSymbolID;
    }

    /**
     * @param exitSymbolID the exitSymbolID to set
     */
    public void setExitSymbolID(int exitSymbolID) {
        this.exitSymbolID = exitSymbolID;
    }

    /**
     * @return the parentSymbol
     */
    public String getParentSymbol() {
        return parentSymbol;
    }

    /**
     * @param parentSymbol the parentSymbol to set
     */
    public void setParentSymbol(String parentSymbol) {
        this.parentSymbol = parentSymbol;
    }

    /**
     * @return the exitReason
     */
    public EnumOrderReason getExitReason() {
        return exitReason;
    }

    /**
     * @param exitReason the exitReason to set
     */
    public void setExitReason(EnumOrderReason exitReason) {
        this.exitReason = exitReason;
    }

    /**
     * @return the entryReason
     */
    public EnumOrderReason getEntryReason() {
        return entryReason;
    }

    /**
     * @param entryReason the entryReason to set
     */
    public void setEntryReason(EnumOrderReason entryReason) {
        this.entryReason = entryReason;
    }
}
