/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.cedarsoftware.util.io.JsonReader;
import static com.incurrency.framework.Algorithm.globalProperties;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class Validator {

    public static String newline = System.getProperty("line.separator");
    private static final Logger logger = Logger.getLogger(Validator.class.getName());

    public static void main(String[] args) {
        //args = new String[]{"", "INRPAIRTrades2.csv", "INRPAIROrders2.csv", "DU15103", "symbols-inr.csv"};
        if (new File(args[4]).exists() && !new File(args[4]).isDirectory()) {
            new BeanSymbol().readerAll(args[4], Parameters.symbol);
//            reconcile(args[0], args[1], args[2], args[3], args[5]);
        }
    }

    public synchronized static String pnlSummary(Database<String,String>db,String account,Strategy s){
         String out = "";
         TreeMap<String,String>pnlSummary=new TreeMap<>();
             for (String key : db.getKeys("pnl")) {
                 if(key.contains(account)&& key.contains("_"+s.getStrategy())){
                     out = TradingUtil.padRight(db.getValue("pnl", key, "todaypnl"), 25)
                             + TradingUtil.padRight(db.getValue("pnl", key, "ytd"), 25);
                             pnlSummary.put(key, out);
                 }                 
            }
           out=TradingUtil.padRight("Date", 45)+TradingUtil.padRight("Today PNL", 25)+TradingUtil.padRight("YTD PNL", 25)+"\n";
           for(Entry <String,String>e:pnlSummary.entrySet()){
               out=out+TradingUtil.padRight(e.getKey(), 45)+e.getValue()+"\n";               
           }  
           return out;
    }
    public synchronized static boolean reconcile(String prefix, Database<String,String>orderDB, Database<String,String>tradeDB, String account, String email,String strategy, Boolean fix) {
        //for(BeanConnection c:Parameters.connection){
//        String tradeFileFullName = "logs" + File.separator + prefix + tradeFile;
//        String orderFileFullName = "logs" + File.separator + prefix + orderFile;
        HashMap<String, ArrayList<Integer>> singleLegReconIssue = getPositionMismatch(orderDB, tradeDB, account, "SingleLeg",strategy);
        HashMap<String, ArrayList<Integer>> comboReconIssue = getPositionMismatch(orderDB, tradeDB, account, "Combo",strategy);
        Set<String> comboParents = returnComboParent(tradeDB,account,strategy);
        Set<String> comboChildren = returnComboChildren(tradeDB,account,strategy);
        HashMap<String, HashMap<String, ArrayList<Integer>>> comboChildrenReconIssue = reconComboChildren(comboParents, comboChildren, tradeDB,account);
        String singleLegIssues = "";
        String comboIssues = "";
        String comboChildrenIssues = "";
        Boolean reconStatus = true;
        if (!singleLegReconIssue.isEmpty()) {
            singleLegIssues = TradingUtil.padRight("Flag", 10) + TradingUtil.padRight("Order File", 25) + TradingUtil.padRight("Trade File", 25) + TradingUtil.padRight("Symbol", 40) + TradingUtil.padRight("Expected Pos:Orders", 25) + TradingUtil.padRight("Actual Pos:Trade", 25);
            //singleLegIssues="Symbol\t\t,Expected Position As per Orders\t\t,ActualPosition as per trades";
            for (Map.Entry<String, ArrayList<Integer>> issue : singleLegReconIssue.entrySet()) {
                int expected = Utilities.getInt(issue.getValue().get(0), 0);
                int actual = issue.getValue().get(1) == null ? 0 : issue.getValue().get(1);
                String flag = Math.abs(expected) < Math.abs(actual) || Integer.signum(expected) == -Integer.signum(actual) ? "Issue" : "Warn";
                reconStatus = reconStatus && (flag.equals("Issue") ? false : true);
                singleLegIssues = singleLegIssues + newline
                        + TradingUtil.padRight(flag, 10) + TradingUtil.padRight("OrderFile", 25) + TradingUtil.padRight("TradeFile", 25) + TradingUtil.padRight(issue.getKey(), 40) + TradingUtil.padRight(String.valueOf(expected), 25) + TradingUtil.padRight(String.valueOf(actual), 25) + newline;
                //singleLegIssues = singleLegIssues + issue.getKey() + "\t\t," + expected + "\t\t," + actual + newline;
            }
            singleLegIssues = "Single Leg executions did not reconcile with orders. Please verify and correct 'Issue' rows in order and trade files before the next run of inStrat. 'Warn' rows are for information"
                    + newline + singleLegIssues;
        }
        if (!comboReconIssue.isEmpty()) {
            comboIssues = TradingUtil.padRight("Flag", 10) + TradingUtil.padRight("Order File", 25) + TradingUtil.padRight("Trade File", 25) + TradingUtil.padRight("Combo", 40) + TradingUtil.padRight("Child", 40) + TradingUtil.padRight("Expected Pos:Orders", 25) + TradingUtil.padRight("Actual Pos:Trade", 25);
            for (Map.Entry<String, ArrayList<Integer>> issue : comboReconIssue.entrySet()) {
                int expected = issue.getValue().get(0) == null ? 0 : issue.getValue().get(0);
                int actual = issue.getValue().get(1) == null ? 0 : issue.getValue().get(1);
                String flag = Math.abs(expected) < Math.abs(actual) || Integer.signum(expected) == -Integer.signum(actual) ? "Issue" : "Warn";
                reconStatus = reconStatus && (flag.equals("Issue") ? false : true);
                comboIssues = comboIssues + newline
                        + TradingUtil.padRight(flag, 10) + TradingUtil.padRight("OrderFile", 25) + TradingUtil.padRight("TradeFile", 25) + TradingUtil.padRight(issue.getKey(), 40) + TradingUtil.padRight("", 25) + TradingUtil.padRight(String.valueOf(expected), 25) + TradingUtil.padRight(String.valueOf(actual), 25) + newline;

            }
            comboIssues = "Combo trades did not reconcile with combo orders. Please verify and correct 'Issue' rows in order and trade files before the next run of inStrat. 'Warn' rows are for information"
                    + newline + comboIssues;
        }
        if (!comboChildrenReconIssue.isEmpty()) {
            comboChildrenIssues = TradingUtil.padRight("Flag", 10) + TradingUtil.padRight("Order File", 25) + TradingUtil.padRight("Trade File", 25) + TradingUtil.padRight("Combo", 25) + TradingUtil.padRight("Child", 25) + TradingUtil.padRight("Expected Pos", 25) + TradingUtil.padRight("Actual Pos", 25);
            for (Map.Entry<String, HashMap<String, ArrayList<Integer>>> issue : comboChildrenReconIssue.entrySet()) {
                HashMap<String, ArrayList<Integer>> child = issue.getValue();
                String parent = issue.getKey();
                HashMap<String, ArrayList<Integer>> children = issue.getValue();
                for (Map.Entry<String, ArrayList<Integer>> childLeg : children.entrySet()) {
                    String childSymbol = childLeg.getKey();
                    int expected = childLeg.getValue().get(0);
                    int actual = childLeg.getValue().get(1);
                    String flag = "Issue";
                    reconStatus = reconStatus && (flag.equals("Issue") ? false : true);
                    comboChildrenIssues = comboChildrenIssues + newline
                            + TradingUtil.padRight(flag, 10) + TradingUtil.padRight("", 25) + TradingUtil.padRight("TradeFile", 25) + TradingUtil.padRight(parent, 25) + TradingUtil.padRight(childSymbol, 25) + TradingUtil.padRight(String.valueOf(expected), 25) + TradingUtil.padRight(String.valueOf(actual), 25) + newline;
                }
            }
            comboChildrenIssues = "Combo child trades did not reconcile with combo trades. Please verify and correct 'Issue' rows in trade file before the next run of inStrat"
                    + newline + comboChildrenIssues;
        }
        if (!(singleLegIssues.equals("") && comboIssues.equals("") && comboChildrenIssues.equals(""))) {
            if(fix){
                Set<String> openorders=orderDB.getKeys("opentrades_"+strategy+"*"+"Order"); 
                Set<String> opentrades=tradeDB.getKeys("opentrades_"+strategy+"*"+account);
                Set<String> closedorders=orderDB.getKeys("closedtrades_"+strategy+"*"+"Order");
                for(String tradekey:opentrades){
                    String orderkey=tradekey.replace(":"+account, ":Order");
                    if(!openorders.contains(orderkey)){//we have an opentrade with no openorder
                        //see if the order was closed
                        String neworderkey=orderkey.replace("opentrades", "closedtrades");
                        if(closedorders.contains(neworderkey)){
                            orderDB.rename(neworderkey, orderkey);
                            String subkeyOrder=orderkey.split("_")[1];
                            String subkeyTrade=tradekey.split("_")[1];
                            
                            String exitsize=tradeDB.getValue("opentrades",subkeyTrade,"exitsize");
                            String exitprice=tradeDB.getValue("opentrades",subkeyTrade,"exitprice");
                            String exitbrokerage=tradeDB.getValue("opentrades",subkeyTrade,"exitbrokerage");
                            exitsize=exitsize==null?"0":exitsize;
                            exitprice=exitprice==null?"0":exitprice;
                            exitbrokerage=exitbrokerage==null?"0":exitbrokerage;
                            orderDB.setHash("opentrades", subkeyOrder, "exitsize", exitsize);
                            orderDB.setHash("opentrades", subkeyOrder, "exitprice", exitprice);
                            orderDB.setHash("opentrades", subkeyOrder, "exitbrokerage", exitbrokerage);
                        }                    
                    }
                }
            } else{
            System.out.println(singleLegIssues + newline + comboIssues + newline + comboChildrenIssues);
            Thread t = new Thread(new Mail(email, singleLegIssues + newline + comboIssues + newline + comboChildrenIssues, "ACTION NEEDED: Recon difference, Files : " + "TradeFile" + " , " + "OrderFile"));
            t.start();
            }
            return reconStatus;
        } else {
            System.out.println("Trade and Order Files Reconile for account " + account+";"+strategy + "  !");
            return reconStatus;
        }

        //}

    }

    public synchronized static String openPositions(String account, Strategy s) {
        String out = "";
        HashMap<String, Integer> openPosition = new HashMap();
        //String tradeFileFullName = "logs" + File.separator + prefix + tradeFile;
        try {
            ArrayList<String> tradeList = new ArrayList<>();
            for (String key : s.db.getKeys("opentrades")) {
                tradeList.add(key);
            }
            Set<String> singleLegTrades = returnSingleLegTrades(s.getOms().getDb(), account,s.getStrategy());
            Set<String> comboTrades = returnComboParent(s.getOms().getDb(), account,s.getStrategy());
            boolean headerWritten = false;
            for (String key : singleLegTrades) {
                if (!headerWritten) {
                    out = out + "List of OpenPositions" + newline;
                    out = out + TradingUtil.padRight("Time", 25) + TradingUtil.padRight("Symbol", 40) + TradingUtil.padRight("Side", 10) + TradingUtil.padRight("Price", 10) + TradingUtil.padRight("Brok", 10) + TradingUtil.padRight("MTM", 10) + TradingUtil.padRight("Position", 10) + newline;
                    headerWritten = true;
                }
                int entrySize = Trade.getEntrySize(s.getOms().getDb(),key);
                int exitSize = Trade.getExitSize(s.getOms().getDb(),key);
                String entryTime = Trade.getEntryTime(s.getOms().getDb(),key);
                String childdisplayname = Trade.getEntrySymbol(s.getOms().getDb(),key);
                EnumOrderSide entrySide = Trade.getEntrySide(s.getOms().getDb(),key);
                double entryPrice = Trade.getEntryPrice(s.getOms().getDb(),key);
                double entryBrokerage = Trade.getEntryBrokerage(s.getOms().getDb(),key);
                double mtmToday = Trade.getMtmToday(s.getOms().getDb(),key);
                if (entrySize - exitSize != 0) {
                    out = out + TradingUtil.padRight(entryTime, 25) + TradingUtil.padRight(childdisplayname, 40) + TradingUtil.padRight(String.valueOf(entrySide), 10) + TradingUtil.padRight(String.valueOf(entryPrice), 10) + TradingUtil.padRight(String.valueOf(Utilities.round(entryBrokerage,2)), 10) + TradingUtil.padRight(String.valueOf(mtmToday), 10) + TradingUtil.padRight(String.valueOf(entrySize - exitSize), 10) + newline;
                }
            }
            for (String key : comboTrades) {
                if (!headerWritten) {
                    out = out + "List of OpenPositions" + newline;
                    int entrySize = Trade.getEntrySize(s.getOms().getDb(),key);
                    int exitSize = Trade.getExitSize(s.getOms().getDb(),key);
                    out = out + TradingUtil.padRight("Time", 25) + "," + TradingUtil.padRight("Symbol", 20) + "," + TradingUtil.padRight("Side", 10) + TradingUtil.padRight("Price", 10) + "," + TradingUtil.padRight("Brok", 10) + "," + TradingUtil.padRight("MTM", 10) + "," + TradingUtil.padRight("Position", 10) + "," + TradingUtil.padRight(String.valueOf(entrySize - exitSize), 10) + newline;
                    headerWritten = true;
                }
                int entrySize = Trade.getEntrySize(s.getOms().getDb(),key);
                int exitSize = Trade.getExitSize(s.getOms().getDb(),key);
                String entryTime = Trade.getEntryTime(s.getOms().getDb(),key);
                String childdisplayname = Trade.getEntrySymbol(s.getOms().getDb(),key);
                EnumOrderSide entrySide = Trade.getEntrySide(s.getOms().getDb(),key);
                double entryPrice = Trade.getEntryPrice(s.getOms().getDb(),key);
                double entryBrokerage = Trade.getEntryBrokerage(s.getOms().getDb(),key);
                double mtmToday = Trade.getMtmToday(s.getOms().getDb(),key);
                if (entrySize - exitSize != 0) {
                    out = out + TradingUtil.padRight(entryTime, 25) + TradingUtil.padRight(childdisplayname, 40) +  TradingUtil.padRight(String.valueOf(entrySide), 10) + TradingUtil.padRight(String.valueOf(Utilities.round(entryPrice, 2)), 10) + "," + TradingUtil.padRight(String.valueOf(Utilities.round(entryBrokerage,0)), 10) + "," + TradingUtil.padRight(String.valueOf(mtmToday), 10) + newline;
                }
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101,e");
        }
        return out;
    }

    /**
     * Updates the symbols in the symbolfile of inStrat with any symbols that
     * exist in open positions but are not present in symbolfile.
     *
     * @param positions a newline seperated list of
     */
    public synchronized static void updateComboSymbols(String prefix, String orderFile, Strategy s, String parentStrategy) {
        FileWriter fileWriter = null;
        try {
            String out = "";
            String orderFileFullName = "logs" + File.separator + prefix + orderFile;
            ArrayList<String> comboTrades = new ArrayList<>();
            ArrayList<BeanSymbol> symbolList = new ArrayList<>();
            DataStore<String, String> orderList = new DataStore<>();
            InputStream initialStream = new FileInputStream(new File(orderFileFullName));
            JsonReader jr = new JsonReader(initialStream);
            orderList = (DataStore<String, String>) jr.readObject();
            jr.close();
            boolean symbolfileneeded = Boolean.parseBoolean(globalProperties.getProperty("symbolfileneeded", "false"));
            if (symbolfileneeded) {
                String symbolFileName = globalProperties.getProperty("symbolfile", "symbols.csv").toString().trim();
                new BeanSymbol().completeReader(symbolFileName, symbolList);
                for (String key : orderList.getKeys("opentrades")) {
                    if (isCombo(s.getOms().getDb(),key)) {//only combo trades should contain :
                        comboTrades.add(key);
                    }
                }
                fileWriter = new FileWriter(symbolFileName, true);
                for (String key : comboTrades) {
                    String childdisplayname = Trade.getEntrySymbol(s.getOms().getDb(),key);
                    String parentdisplayname = Trade.getParentSymbol(s.getOms().getDb(),key);
                    int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
                    if (childid == -1) {
                        //add combo symbol to symbols file
                        fileWriter.write(newline + Parameters.symbol.size() + "," + parentdisplayname + "," + childdisplayname + ",COMBO" + ",,,,,,,1,,1," + parentStrategy);
                        Parameters.symbol.add(new BeanSymbol(parentdisplayname, childdisplayname, "CSV"));
                    }
                }
                fileWriter.close();
            }
        } catch (IOException ex) {
            logger.log(Level.INFO, "101", ex);
        } finally {
            try {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            } catch (IOException ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }

    }

    public synchronized static boolean validateSymbolFile(String symbolFileName) {
        boolean correctFormat = true;
        try {
            List<String> existingSymbolsLoad = Files.readAllLines(Paths.get(symbolFileName), StandardCharsets.UTF_8);
            existingSymbolsLoad.remove(0);
            int i = 1;
            HashMap<String, String[]> uniqueDisplayName = new HashMap<>();
            for (String symbolline : existingSymbolsLoad) {
                //check columnCount
                if (!symbolline.equals("")) {
                    String[] input = symbolline.split(",");
                    if (!checkColumnSize(symbolline, 15)) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnSize_" + i});
                        //check for unique value in serial no
                    }
                    if (!TradingUtil.isInteger(input[0])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_1"});
                    } else if (Integer.parseInt(input[0]) != i) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_1"});
                    }
                    //String Values are needed in symbol,displayname,type
                    if (input[1] == null) {//symbol
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_2"});
                    } else if (input[1].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_2"});
                    }
                    if (input[2] == null) {//displayname
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_3"});
                    } else if (input[2].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_3"});
                    } else {
                        String ud = input[2] + "_" + input[4] + "_" + input[8] + "_" + input[9] + "_" + input[10];
                        if(!uniqueDisplayName.containsKey(ud))
                        {
                            uniqueDisplayName.put(ud, input);
                        }else{
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"DuplicateSymbols_" + ud});
                  
                        }
                    }
                    
                    if (input[4] == null) {//type
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_5"});
                    } else if (input[4].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_5"});

                    }
                    //Integer values are needed in size and streaming priority
                    if (input[13] == null) {//streaming
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_14"});
                    } else if (input[13].equals("") || !TradingUtil.isInteger(input[13])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_14"});
                    }
                    if (input[11] == null) {//size
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_12"});
                    } else if (input[11].equals("") || !TradingUtil.isInteger(input[11])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_12"});
                    }
                    if (!input[12].equals("")) {
                        if (!input[12].contains("?") || (input[12].contains("?") && input[12].split("?").length != 3)) {
                            correctFormat = correctFormat && false;
                            logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_13"});
                        }
                    }
                    i = i + 1;
                }
            }
            //confirm displayname is unique
            if (uniqueDisplayName.size() != existingSymbolsLoad.size()) {
                correctFormat = correctFormat && false;
                logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"DuplicateDisplayNames"});
                logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"Also check that there are NO EMPTY LINES in symbol file"});

            }
            return correctFormat;

        } catch (IOException ex) {
            logger.log(Level.INFO, "101", ex);
        } finally {
            return correctFormat;
        }

    }

    public synchronized static boolean validateConnectionFile(String connectionFileName) {
        boolean correctFormat = true;
        try {
            List<String> existingConnectionsLoad = Files.readAllLines(Paths.get(connectionFileName), StandardCharsets.UTF_8);
            existingConnectionsLoad.remove(0);
            int i = 1;
            for (String connectionline : existingConnectionsLoad) {
                //check columnCount
                String[] input = connectionline.split(",");
                if (!checkColumnSize(connectionline, 10)) {
                    correctFormat = correctFormat && false;
                    logger.log(Level.INFO, "104,ConnectionFileError,{0}", new Object[]{"IncorrectColumnSize_" + i});
                    //Validate Columns
                    if (input[0] == null) {//IP
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_1"});
                    } else if (input[1].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_1"});
                    }
                    if (isInteger(input[1])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_2"});
                    }
                    if (isInteger(input[2])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_3"});
                    }
                    if (input[3] == null) {//Purpose
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_4"});
                    } else if (!(input[3].equals("Trading") || input[3].equals("Data"))) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_4"});
                    }

                    if (isInteger(input[4])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_5"});
                    }
                    if (isInteger(input[5])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_6"});
                    }
                    if (isInteger(input[6])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_7"});
                    }
                    if (input[7] == null) {//Strategy
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_8"});
                    } else if (input[7].equals("")) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_8"});
                    }
                    if (isInteger(input[8])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_9"});
                    }

                    if (input[9] == null) {//Email
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_10"});
                    } else if (!TradingUtil.isValidEmailAddress(input[10])) {
                        correctFormat = correctFormat && false;
                        logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_10"});
                    }
                    i = i + 1;
                }
            }
            return correctFormat;

        } catch (IOException ex) {
            logger.log(Level.INFO, "101", ex);
        } finally {
            return correctFormat;
        }

    }

    public static boolean checkColumnSize(String inputString, int expectedSize) {

        if (inputString.split(",").length < expectedSize) {
            return false;
        } else {
            return true;
        }
    }

    public static boolean isInteger(String input) {
        if (input == null) {//Port
            return false;
        } else if (input.equals("") || !TradingUtil.isInteger(input)) {
            return false;
        }
        return true;
    }

    public static boolean checkColumnFormat(String inputString, int column, String format) {
        return true;
    }

    public static HashMap<String, ArrayList<Integer>> getPositionMismatch(Database<String,String>orderDB, Database<String,String>tradeDB, String account, String reconType,String strategy) {
        HashMap<String, ArrayList<Integer>> out = new HashMap<>();
        Set<String> t = new HashSet<>();
        Set<String> o = new HashSet<>();

        switch (reconType) {
            case "SingleLeg":
                t = returnSingleLegTrades(tradeDB,account,strategy);
                o = returnSingleLegTrades(orderDB,"Order",strategy);
                out = reconTrades(t, o, account, "Order",orderDB,tradeDB);
                break;

            case "Combo":
                t = returnComboParent(tradeDB,account,strategy);
                o = returnComboParent(orderDB,"Order",strategy);
                out = reconTrades(t, o, account, "Order",orderDB,tradeDB);
                break;
            default:
                break;
        }

        return out;
    }

    private static Set<String> returnSingleLegTrades(Database<String,String>db) {
         //Remove orders that are not in symbolist
        Set<String> keys=db.getKeys("closedtrades");
        keys.addAll(db.getKeys("opentrades"));
        Iterator<String> iter=keys.iterator();
        while(iter.hasNext()){
            String key=iter.next();
            String childdisplayname = Trade.getEntrySymbol(db,key);
            if (Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname) == -1) {
                iter.remove();
            }
        }
        iter=keys.iterator();
        while(iter.hasNext()){
            String key=iter.next();
            if(isCombo(db,key)){
                iter.remove();
            }
        
        }
        return keys;

    }

    private static Set<String> returnSingleLegTrades(Database<String, String> db,String accountName, String strategy) {
        //Remove orders that are not in symbolist or are combos
        Set<String> keys = db.getKeys("opentrades");
        Iterator<String> iter = keys.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            /*
             * Removed next three lines as not sure of their existence
             */
            //String childdisplayname = Trade.getEntrySymbol(db, key);
            //int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
           // if (!Trade.getAccountName(db, key).equals(accountName)||!key.contains("_"+strategy)||childid < 0 || isCombo(db, key)) {
            if (!Trade.getAccountName(db, key).equals(accountName) || !key.contains("_"+strategy)||isCombo(db, key)) {
                iter.remove();
            }
        }
        return keys;

    }

    private static boolean isCombo(Database<String, String> db, String key) {
        int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, Trade.getParentSymbol(db, key));
        String type = "";
        if (parentid >= 0) {
            type = Parameters.symbol.get(parentid).getType();
        }
        if (!Trade.getParentSymbol(db, key).equals(Trade.getEntrySymbol(db, key)) || type.equals("COMBO")) {
            return true;
        } else {
            return false;
        }
    }

       private static boolean isComboParent(Database<String, String> db, String key) {
        int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, Trade.getParentSymbol(db, key));
        String type = "";
        if (parentid >= 0) {
            type = Parameters.symbol.get(parentid).getType();
        }
        if (Trade.getParentSymbol(db, key).equals(Trade.getEntrySymbol(db, key)) && type.equals("COMBO")) {
            return true;
        } else {
            return false;
        }
    }

    private static Set<String> returnComboParent(Database<String,String>db) {
         //Remove orders that are not in symbolist
        Set<String> keys=db.getKeys("closedtrades");
        keys.addAll(db.getKeys("opentrades"));
        Iterator<String> iter=keys.iterator();
        while(iter.hasNext()){
            String key=iter.next();
            String childdisplayname = Trade.getEntrySymbol(db,key);
            if (Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname) == -1) {
                iter.remove();
            }
        }
        
        iter=keys.iterator();
        while(iter.hasNext()){
            String key=iter.next();
            if(!isComboParent(db,key)){
                iter.remove();
            }
        
        }
        return keys;
    }

    private static Set<String> returnComboParent(Database<String, String> db, String accountName,String strategy) {
        //Remove orders that are not in symbolist or are combos
        Set<String> keys = db.getKeys("opentrades");
        Iterator<String> iter = keys.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            String childdisplayname = Trade.getEntrySymbol(db, key);
            int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (!Trade.getAccountName(db, key).equals(accountName)||!key.contains("_"+strategy)||childid < 0 || !isComboParent(db, key)) {
                iter.remove();
            }
        }
        return keys;

    }

    private static Set<String> returnComboChildren(Database<String,String>db,String accountName,String strategy) {
        //Remove orders that are not in symbolist or are combos
        Set<String> keys = db.getKeys("opentrades");
        Iterator<String> iter = keys.iterator();
        while (iter.hasNext()) {
            String key = iter.next();
            String childdisplayname = Trade.getEntrySymbol(db, key);
            int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (!Trade.getAccountName(db, key).equals(accountName)||!key.contains("_"+strategy)||childid < 0 || !(isCombo(db,key)&& !isComboParent(db, key))) {
                iter.remove();
            }
        }
        return keys;

    }

    private static HashMap<String, ArrayList<Integer>> reconTrades(Set<String> tr, Set<String> or, String tradeAccount, String orderAccount,Database<String,String>orderDB,Database<String,String>tradeDB) {
        HashMap<String, ArrayList<Integer>> out = new HashMap<>(); //ArrayList contains two values: Index 0 is expected, index 1 is actual
        SortedMap<String, Integer> tradePosition = new TreeMap<>();
        SortedMap<String, Integer> orderPosition = new TreeMap<>();
        for (String key : tr) {
            String accountName = Trade.getAccountName(tradeDB, key);
            String childdisplayname = Trade.getEntrySymbol(tradeDB, key);
            EnumOrderSide entrySide = Trade.getEntrySide(tradeDB, key);
            EnumOrderSide exitSide = Trade.getExitSide(tradeDB, key);
            int entrySize = Trade.getEntrySize(tradeDB, key);
            int exitSize = Trade.getExitSize(tradeDB, key);
            if (accountName.equals(tradeAccount)) {
                int lastPosition = tradePosition.get(childdisplayname) == null ? 0 : tradePosition.get(childdisplayname);
                entrySize = entrySide.equals(EnumOrderSide.BUY) || entrySide.equals(EnumOrderSide.COVER) ? entrySize : -entrySize;
                exitSize = exitSide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER) ? exitSize : -exitSize;
                int netSize = entrySize + exitSize;
                if (netSize != 0) {
                    if (lastPosition == 0) {
                        tradePosition.put(childdisplayname, netSize);
                    } else {
                        tradePosition.put(childdisplayname, lastPosition + netSize);
                    }
                }
            }
        }
        for (String key : or) {
            String accountName = Trade.getAccountName(orderDB, key);
            String childdisplayname = Trade.getEntrySymbol(orderDB, key);
            EnumOrderSide entrySide = Trade.getEntrySide(orderDB, key);
            EnumOrderSide exitSide = Trade.getExitSide(orderDB, key);
            int entrySize = Trade.getEntrySize(orderDB, key);
            int exitSize = Trade.getExitSize(orderDB, key);

            if (accountName.equals(orderAccount)) {
                int lastPosition = orderPosition.get(childdisplayname) == null ? 0 : orderPosition.get(childdisplayname);
                entrySize = entrySide.equals(EnumOrderSide.BUY) || entrySide.equals(EnumOrderSide.COVER) ? entrySize : -entrySize;
                exitSize = exitSide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER) ? exitSize : -exitSize;
                int netSize = entrySize + exitSize;
                if (netSize != 0) {
                    if (lastPosition == 0) {
                        orderPosition.put(childdisplayname, netSize);
                    } else {
                        orderPosition.put(childdisplayname, lastPosition + netSize);
                    }
                }
            }
        }
        //recon positions - 2 way recon
        //Confirm trades in tradePosition exists in order
        for (Map.Entry<String, Integer> entry : tradePosition.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().equals(orderPosition.get(key))) {
                ArrayList<Integer> i = new ArrayList<Integer>();
                i.add(orderPosition.get(key));
                i.add(entry.getValue());
                out.put(key, i);
            }
        }
        //2nd recon
        //Confirm trades in orderPosition exist in trades
        for (Map.Entry<String, Integer> entry : orderPosition.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().equals(tradePosition.get(key))) {
                ArrayList<Integer> i = new ArrayList<Integer>();
                i.add(entry.getValue());
                i.add(tradePosition.get(key) == null ? 0 : tradePosition.get(key));
                out.put(key, i);
            }
        }

        //remove zeros from out
        Iterator iter1 = out.entrySet().iterator();
        while (iter1.hasNext()) {
            Map.Entry<String, ArrayList<Integer>> pair = (Map.Entry) iter1.next();
            ArrayList<Integer> position = pair.getValue();
            if (position.get(0) == position.get(1)) {
                iter1.remove();
            }
        }
        return out;
    }

    private static HashMap<String, HashMap<String, ArrayList<Integer>>> reconComboChildren(Set<String> combos, Set<String> children,Database<String,String>tradeDB,String tradeAccount) {
        HashMap<String, HashMap<String, ArrayList<Integer>>> out = new HashMap<>(); //ArrayList contains two values: Index 0 is expected, index 1 is actual
        SortedMap<String, HashMap<String, Integer>> comboPosition = new TreeMap<>();
        SortedMap<String, HashMap<String, Integer>> childPosition = new TreeMap<>();
        for (String key : combos) {
            String accountName = Trade.getAccountName(tradeDB, key);
            String childdisplayname = Trade.getEntrySymbol(tradeDB, key);
            String parentdisplayname = Trade.getParentSymbol(tradeDB, key);
            EnumOrderSide entrySide = Trade.getEntrySide(tradeDB, key);
            EnumOrderSide exitSide = Trade.getExitSide(tradeDB, key);
            int entrySize = Trade.getEntrySize(tradeDB, key);
            int exitSize = Trade.getExitSize(tradeDB, key);
            if (accountName.equals(tradeAccount)) {
                HashMap<String, Integer> lastPosition = new HashMap<>();
                if (comboPosition.get(childdisplayname) != null) {
                    lastPosition = comboPosition.get(childdisplayname);
                }
                entrySize = entrySide.equals(EnumOrderSide.BUY) || entrySide.equals(EnumOrderSide.COVER) ? entrySize : -entrySize;
                exitSize = exitSide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER) ? exitSize : -exitSize;
                int netSize = entrySize + exitSize;
                //HashMap<String,Integer>childPositions=new HashMap<>();
                if (netSize != 0) {
                    HashMap<BeanSymbol, Integer> comboLegs = getComboLegsFromComboString(parentdisplayname);
                    if (lastPosition.isEmpty()) {
                        for (Map.Entry<BeanSymbol, Integer> comboLeg : comboLegs.entrySet()) {
                            lastPosition.put(comboLeg.getKey().getDisplayname(), netSize * comboLeg.getValue());
                        }
                    } else {
                        for (Map.Entry<BeanSymbol, Integer> comboLeg : comboLegs.entrySet()) {
                            int positionValue = lastPosition.get(comboLeg.getKey().getBrokerSymbol());
                            lastPosition.put(comboLeg.getKey().getDisplayname(), positionValue + netSize * comboLeg.getValue());
                        }
                    }
                    comboPosition.put(childdisplayname, lastPosition);
                }

            }
        }
        for (String key : children) {
            String accountName = Trade.getAccountName(tradeDB, key);
            String childdisplayname = Trade.getEntrySymbol(tradeDB, key);
            String parentdisplayname = Trade.getParentSymbol(tradeDB, key);
            EnumOrderSide entrySide = Trade.getEntrySide(tradeDB, key);
            EnumOrderSide exitSide = Trade.getExitSide(tradeDB, key);
            int entrySize = Trade.getEntrySize(tradeDB, key);
            int exitSize = Trade.getExitSize(tradeDB, key);

            if (accountName.equals(tradeAccount)) {
                HashMap<String, Integer> lastPosition = new HashMap<>();
                int parentid = TradingUtil.getIDFromComboLongName(parentdisplayname);
                if (childPosition.get(Parameters.symbol.get(parentid).getDisplayname()) != null) {
                    lastPosition = childPosition.get(Parameters.symbol.get(parentid).getDisplayname());
                }
                entrySize = entrySide.equals(EnumOrderSide.BUY) || entrySide.equals(EnumOrderSide.COVER) ? entrySize : -entrySize;
                exitSize = exitSide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER) ? exitSize : -exitSize;
                int netSize = entrySize + exitSize;
                //HashMap<String,Integer>childPositionMap=new HashMap<>();
                if (netSize != 0) {
                    if (lastPosition.isEmpty()) {
                        lastPosition.put(childdisplayname, netSize);
                    } else {
                        int positionValue = lastPosition.get(childdisplayname) == null ? 0 : lastPosition.get(childdisplayname);
                        lastPosition.put(childdisplayname, positionValue + netSize);
                    }
                    String comboLongName = parentdisplayname;
                    int comboid = TradingUtil.getIDFromComboLongName(comboLongName);
                    childPosition.put(Parameters.symbol.get(comboid).getDisplayname(), lastPosition);
                }

            }
        }

        //recon positions - 2 way recon
        //1st recon - reconcile comboPosition to childPosition
        for (Map.Entry<String, HashMap<String, Integer>> combo : comboPosition.entrySet()) {//child details calculated from combo position
            String comboName = combo.getKey(); //displayname
            HashMap<String, Integer> childDetails = combo.getValue();//child legs
            for (Map.Entry<String, Integer> child : childDetails.entrySet()) {
                if (childPosition.get(comboName) == null) {//position required as per combo row, but does not exist in child row
                    HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                    if (out.get(comboName) != null) {
                        mismatchChildNameValue = out.get(comboName);
                    }
                    ArrayList<Integer> mismatchValue = new ArrayList<>();
                    mismatchValue.add(child.getValue());//expected
                    mismatchValue.add(0);
                    mismatchChildNameValue.put(child.getKey(), mismatchValue);
                    out.put(comboName, mismatchChildNameValue);
                } else if (!child.getValue().equals(childPosition.get(comboName).get(child.getKey()))) {//child positions do not recon
                    HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                    if (out.get(comboName) != null) {
                        mismatchChildNameValue = out.get(comboName);
                    }
                    ArrayList<Integer> mismatchValue = new ArrayList<>();
                    mismatchValue.add(child.getValue());//expected
                    mismatchValue.add(childPosition.get(comboName).get(child.getKey()) == null ? 0 : childPosition.get(comboName).get(child.getKey()));
                    mismatchChildNameValue.put(child.getKey(), mismatchValue);
                    out.put(comboName, mismatchChildNameValue);
                }
            }
        }

        //2nd recon - reconcile childPosition to comboPosition
        for (Map.Entry<String, HashMap<String, Integer>> combo : childPosition.entrySet()) {//loop through child positions
            String comboName = combo.getKey();
            HashMap<String, Integer> childDetails = combo.getValue();
            for (Map.Entry<String, Integer> child : childDetails.entrySet()) {//loop through expected/actual of child position
                if (comboPosition.get(comboName) != null) {
                    if (!child.getValue().equals(comboPosition.get(comboName).get(child.getKey()))) {//child positions do not recon
                        HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                        ArrayList<Integer> mismatchValue = new ArrayList<>();
                        mismatchValue.add(comboPosition.get(comboName).get(child.getKey()));
                        mismatchValue.add(child.getValue());
                        mismatchChildNameValue.put(child.getKey(), mismatchValue);
                        out.put(comboName, mismatchChildNameValue);
                    }
                } else {//no combo position but there are child positions!! This will occur if there is a short stub on entry
                    HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                    ArrayList<Integer> mismatchValue = new ArrayList<>();
                    if (child.getValue() != 0) {
                        mismatchValue.add(0);
                        mismatchValue.add(child.getValue());
                        mismatchChildNameValue.put(child.getKey(), mismatchValue);
                        out.put(comboName, mismatchChildNameValue);
                    }
                }
            }
        }
        return out;
    }

    private static HashMap<BeanSymbol, Integer> getComboLegsFromComboString(String combo) {
        HashMap<BeanSymbol, Integer> out = new HashMap<>();
        String[] comboLegs = combo.split(":");
        for (String comboLeg : comboLegs) {
            String[] components = comboLeg.split("_");
            BeanSymbol s = new BeanSymbol();
            s.setBrokerSymbol(components[0] == null ? "" : components[0]);
            s.setType(components[1] == null ? "" : components[1]);
            s.setExpiry(components[2] == null ? "" : components[2]);
            s.setRight(components[3] == null ? "" : components[3]);
            s.setOption(components[4] == null ? "" : components[4]);
            int id = Utilities.getIDFromBrokerSymbol(Parameters.symbol, s.getBrokerSymbol(), s.getType(), s.getExpiry(), s.getRight(), s.getOption());
            s.setDisplayname(Parameters.symbol.get(id).getDisplayname());
            out.put(s, Integer.parseInt(components[5]));
        }
        return out;


    }
}
