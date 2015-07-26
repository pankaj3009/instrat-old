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
            reconcile(args[0], args[1], args[2], args[3], args[5]);
        }
    }

    public synchronized static boolean reconcile(String prefix, String tradeFile, String orderFile, String account, String email) {
        //for(BeanConnection c:Parameters.connection){
        String tradeFileFullName = "logs" + File.separator + prefix + tradeFile;
        String orderFileFullName = "logs" + File.separator + prefix + orderFile;
        HashMap<String, ArrayList<Integer>> singleLegReconIssue = getPositionMismatch(orderFileFullName, tradeFileFullName, account, "SingleLeg");
        HashMap<String, ArrayList<Integer>> comboReconIssue = getPositionMismatch(orderFileFullName, tradeFileFullName, account, "Combo");
        ExtendedHashMap<String,String,String> comboParents = returnComboParent(tradeFileFullName);
        ExtendedHashMap<String,String,String> comboChildren = returnComboChildren(tradeFileFullName);
        HashMap<String, HashMap<String, ArrayList<Integer>>> comboChildrenReconIssue = reconComboChildren(comboParents, comboChildren, account);
        String singleLegIssues = "";
        String comboIssues = "";
        String comboChildrenIssues = "";
        Boolean reconStatus = true;
        if (!singleLegReconIssue.isEmpty()) {
            singleLegIssues = TradingUtil.padRight("Flag", 10) + TradingUtil.padRight("Order File", 25) + TradingUtil.padRight("Trade File", 25) + TradingUtil.padRight("Symbol", 25) + TradingUtil.padRight("Expected Pos:Orders", 25) + TradingUtil.padRight("Actual Pos:Trade", 25);
            //singleLegIssues="Symbol\t\t,Expected Position As per Orders\t\t,ActualPosition as per trades";
            for (Map.Entry<String, ArrayList<Integer>> issue : singleLegReconIssue.entrySet()) {
                int expected = issue.getValue().get(0);
                int actual = issue.getValue().get(1) == null ? 0 : issue.getValue().get(1);
                String flag = Math.abs(expected) < Math.abs(actual) || Integer.signum(expected) == -Integer.signum(actual) ? "Issue" : "Warn";
                reconStatus = reconStatus && (flag.equals("Issue") ? false : true);
                singleLegIssues = singleLegIssues + newline
                        + TradingUtil.padRight(flag, 10) + TradingUtil.padRight(orderFile, 25) + TradingUtil.padRight(tradeFile, 25) + TradingUtil.padRight(issue.getKey(), 25) + TradingUtil.padRight(String.valueOf(expected), 25) + TradingUtil.padRight(String.valueOf(actual), 25) + newline;
                //singleLegIssues = singleLegIssues + issue.getKey() + "\t\t," + expected + "\t\t," + actual + newline;
            }
            singleLegIssues = "Single Leg executions did not reconcile with orders. Please verify and correct 'Issue' rows in order and trade files before the next run of inStrat. 'Warn' rows are for information"
                    + newline + singleLegIssues;
        }
        if (!comboReconIssue.isEmpty()) {
            comboIssues = TradingUtil.padRight("Flag", 10) + TradingUtil.padRight("Order File", 25) + TradingUtil.padRight("Trade File", 25) + TradingUtil.padRight("Combo", 25) + TradingUtil.padRight("Child", 25) + TradingUtil.padRight("Expected Pos:Orders", 25) + TradingUtil.padRight("Actual Pos:Trade", 25);
            for (Map.Entry<String, ArrayList<Integer>> issue : comboReconIssue.entrySet()) {
                int expected = issue.getValue().get(0) == null ? 0 : issue.getValue().get(0);
                int actual = issue.getValue().get(1) == null ? 0 : issue.getValue().get(1);
                String flag = Math.abs(expected) < Math.abs(actual) || Integer.signum(expected) == -Integer.signum(actual) ? "Issue" : "Warn";
                reconStatus = reconStatus && ( flag.equals("Issue") ? false : true);
                comboIssues = comboIssues + newline
                        + TradingUtil.padRight(flag, 10) + TradingUtil.padRight(orderFile, 25) + TradingUtil.padRight(tradeFile, 25) + TradingUtil.padRight(issue.getKey(), 25) + TradingUtil.padRight("", 25) + TradingUtil.padRight(String.valueOf(expected), 25) + TradingUtil.padRight(String.valueOf(actual), 25) + newline;

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
                            + TradingUtil.padRight(flag, 10) + TradingUtil.padRight("", 25) + TradingUtil.padRight(tradeFile, 25) + TradingUtil.padRight(parent, 25) + TradingUtil.padRight(childSymbol, 25) + TradingUtil.padRight(String.valueOf(expected), 25) + TradingUtil.padRight(String.valueOf(actual), 25) + newline;
                }
            }
            comboChildrenIssues = "Combo child trades did not reconcile with combo trades. Please verify and correct 'Issue' rows in trade file before the next run of inStrat"
                    + newline + comboChildrenIssues;
        }
        if (!(singleLegIssues.equals("") && comboIssues.equals("") && comboChildrenIssues.equals(""))) {
            System.out.println(singleLegIssues + newline + comboIssues + newline + comboChildrenIssues);
            Thread t = new Thread(new Mail(email, singleLegIssues + newline + comboIssues + newline + comboChildrenIssues, "ACTION NEEDED: Recon difference, Files : "+tradeFile+" , "+orderFile));
            t.start();
            return reconStatus;
        } else {
            System.out.println("Trade and Order Files Reconile!");
            return reconStatus;
        }

        //}

    }

    public synchronized static String openPositions(String account, Strategy s) {
        String out = "";
        HashMap<String,Integer> openPosition=new HashMap();
        //String tradeFileFullName = "logs" + File.separator + prefix + tradeFile;
        try{
        ArrayList<String> tradeList = new ArrayList<>();
        Set<Entry>entries=s.getOms().getTrades().entrySet();
        for(Entry entry:entries){
            String key=(String)entry.getKey();
            tradeList.add(key);
        }
        ArrayList<String> singleLegTrades = returnSingleLegTrades(s.getOms().getTrades(),(ArrayList<String>) tradeList.clone(), account);
        ArrayList<String> comboTrades = returnComboParent(s.getOms().getTrades(),(ArrayList<String>) tradeList.clone(), account);
        boolean headerWritten = false;
        for (String key : singleLegTrades) {
            if (!headerWritten) {
                out = out + "List of OpenPositions" + newline;
                out = out + TradingUtil.padRight("Time", 25) + "," + TradingUtil.padRight("Symbol", 20) + "," + TradingUtil.padRight("Side", 10) + TradingUtil.padRight("Price", 10) + "," + TradingUtil.padRight("Brok", 10) + "," + TradingUtil.padRight("MTM", 10)+","+TradingUtil.padRight("Position", 10)+newline;
                headerWritten = true;
            }
            int entrySize=Trade.getEntrySize(s.getOms().getTrades(),key);
            int exitSize=Trade.getExitSize(s.getOms().getTrades(),key);
            String entryTime=Trade.getEntryTime(s.getOms().getTrades(),key);
            String childdisplayname=Trade.getEntrySymbol(s.getOms().getTrades(),key);
            EnumOrderSide entrySide=Trade.getEntrySide(s.getOms().getTrades(),key);
            double entryPrice=Trade.getEntryPrice(s.getOms().getTrades(),key);
            double entryBrokerage=Trade.getEntryBrokerage(s.getOms().getTrades(),key);
            double mtmToday=Trade.getMtmToday(s.getOms().getTrades(),key);
            if (entrySize-exitSize!=0 ) {
                out = out + TradingUtil.padRight(entryTime, 25) + "," + TradingUtil.padRight(childdisplayname, 20) + "," + TradingUtil.padRight(String.valueOf(entrySide), 10) + TradingUtil.padRight(String.valueOf(entryPrice), 10) + "," + TradingUtil.padRight(String.valueOf(entryBrokerage), 10) + "," + TradingUtil.padRight(String.valueOf(mtmToday), 10)+","+TradingUtil.padRight(String.valueOf(entrySize-exitSize), 10)+newline;
            }
        }
        for (String key : comboTrades) {
            if (!headerWritten) {
                out = out + "List of OpenPositions" + newline;
            int entrySize=Trade.getEntrySize(s.getOms().getTrades(),key);
            int exitSize=Trade.getExitSize(s.getOms().getTrades(),key);
                out = out + TradingUtil.padRight("Time", 25) + "," + TradingUtil.padRight("Symbol", 20) + "," + TradingUtil.padRight("Side", 10) + TradingUtil.padRight("Price", 10) + "," + TradingUtil.padRight("Brok", 10) + "," + TradingUtil.padRight("MTM", 10)+","+TradingUtil.padRight("Position", 10)+","+TradingUtil.padRight(String.valueOf(entrySize-exitSize), 10)+newline;
                headerWritten = true;
            }
            int entrySize=Trade.getEntrySize(s.getOms().getTrades(),key);
            int exitSize=Trade.getExitSize(s.getOms().getTrades(),key);
            String entryTime=Trade.getEntryTime(s.getOms().getTrades(),key);
            String childdisplayname=Trade.getEntrySymbol(s.getOms().getTrades(),key);
            EnumOrderSide entrySide=Trade.getEntrySide(s.getOms().getTrades(),key);
            double entryPrice=Trade.getEntryPrice(s.getOms().getTrades(),key);
            double entryBrokerage=Trade.getEntryBrokerage(s.getOms().getTrades(),key);
            double mtmToday=Trade.getMtmToday(s.getOms().getTrades(),key);
            if (entrySize-exitSize!=0 ) {
                out = out + TradingUtil.padRight(entryTime, 25) + "," + TradingUtil.padRight(childdisplayname, 20) + "," + TradingUtil.padRight(String.valueOf(entrySide), 10) + TradingUtil.padRight(String.valueOf(entryPrice), 10) + "," + TradingUtil.padRight(String.valueOf(entryBrokerage), 10) + "," + TradingUtil.padRight(String.valueOf(mtmToday), 10)+newline;
            }
        }
        }catch (Exception e){
            logger.log(Level.INFO,"101,e");
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
                ExtendedHashMap<String, String, String> orderList = new ExtendedHashMap<>();
                InputStream initialStream = new FileInputStream(new File(orderFileFullName));
                JsonReader jr = new JsonReader(initialStream);
                orderList = (ExtendedHashMap<String, String, String>) jr.readObject();
                jr.close();
            boolean symbolfileneeded = Boolean.parseBoolean(globalProperties.getProperty("symbolfileneeded", "false"));
         if (symbolfileneeded) {
            String symbolFileName = globalProperties.getProperty("symbolfile", "symbols.csv").toString().trim();
            new BeanSymbol().completeReader(symbolFileName, symbolList);
            Set<Entry>entries=orderList.entrySet();
            for(Entry entry:entries){
                String key=(String)entry.getKey();
            if (Trade.getParentSymbol(orderList, key).contains(":")) {//only combo trades should contain :
                    comboTrades.add(key);
                }
            }
            fileWriter = new FileWriter(symbolFileName, true);
            for (String key : comboTrades) {
                String childdisplayname=Trade.getEntrySymbol(orderList, key);
                String parentdisplayname=Trade.getParentSymbol(orderList, key);
                int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
                if (childid== -1) {
                    //add combo symbol to symbols file
                    fileWriter.write(newline+Parameters.symbol.size() + "," + parentdisplayname+ "," + childdisplayname + ",COMBO" + ",,,,,,,1,,1," + parentStrategy);
                    Parameters.symbol.add(new BeanSymbol(parentdisplayname, childdisplayname, "CSV"));
                }
            }
            fileWriter.close();
        }
        } catch (IOException ex) {
            logger.log(Level.INFO, "101", ex);
        } finally {
            try {
                if(fileWriter!=null){
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
                if(!symbolline.equals("")){
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
                        String  ud=input[2]+"_"+input[4]+"_"+input[8]+"_"+input[9]+"_"+input[10];
                        uniqueDisplayName.put(ud, input);
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
                    if(!input[12].equals("")){
                        if(!input[12].contains("?") ||(input[12].contains("?") && input[12].split("?").length!=3)){
                    correctFormat = correctFormat && false;
                      logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_13"});                            
                        }
                    }
                    i = i + 1;
                }   
            }
            //confirm displayname is unique
            if(uniqueDisplayName.size()!=existingSymbolsLoad.size()){
              correctFormat = correctFormat && false;
              logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"DuplicateDisplayNames"});
              logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"Also check that there are NO EMPTY LINES in symbol file"});
              
            }
            return correctFormat;

        } catch (IOException ex) {
            logger.log(Level.INFO, "101", ex);
        }finally{
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
                    if(isInteger(input[1])){
                    correctFormat = correctFormat && false;
                    logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_2"});
                    }
                    if(isInteger(input[2])){
                    correctFormat = correctFormat && false;
                    logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_3"});
                    }
                    if (input[3] == null) {//Purpose
                    correctFormat = correctFormat && false;
                    logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_4"});
                    } else if (!(input[3].equals("Trading")||input[3].equals("Data"))) {
                    correctFormat = correctFormat && false;
                    logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_4"});
                    }
                    
                    if(isInteger(input[4])){
                    correctFormat = correctFormat && false;
                    logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_5"});
                    }
                    if(isInteger(input[5])){
                    correctFormat = correctFormat && false;
                    logger.log(Level.INFO, "104,SymbolFileError,{0}", new Object[]{"IncorrectColumnValue_" + i + "_6"});
                    }
                    if(isInteger(input[6])){
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
                    if(isInteger(input[8])){
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
        }finally{
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
    
    public static boolean isInteger(String input){
                    if (input == null) {//Port
                    return false;
                    } else if (input.equals("")||!TradingUtil.isInteger(input)) {
                        return false;
                    }
                    return true;
    }

    public static boolean checkColumnFormat(String inputString, int column, String format) {
        return true;
    }

    public static HashMap<String, ArrayList<Integer>> getPositionMismatch(String orderFileFullName, String tradeFileFullName, String account, String reconType) {
                HashMap<String, ArrayList<Integer>> out = new HashMap<>();
                ExtendedHashMap<String, String, String> alltradelist = new ExtendedHashMap<>();
                try{
                InputStream initialStream = new FileInputStream(new File(tradeFileFullName));
                JsonReader jr = new JsonReader(initialStream);
                alltradelist = (ExtendedHashMap<String, String, String>) jr.readObject();
                jr.close();
                }catch (Exception e){
                    logger.log(Level.INFO,null,e);
                }
                ExtendedHashMap<String, String, String> allorderlist = new ExtendedHashMap<>();
                try{
                  InputStream initialStream = new FileInputStream(new File(tradeFileFullName));
                
                JsonReader jr = new JsonReader(initialStream);
                allorderlist = (ExtendedHashMap<String, String, String>) jr.readObject();
                jr.close();
                }catch (Exception e){
                    logger.log(Level.INFO,null,e);
                }
                

        ExtendedHashMap<String,String,String> t = new ExtendedHashMap<>();
        ExtendedHashMap<String,String,String>  o = new ExtendedHashMap<>();

        switch (reconType) {
            case "SingleLeg":
                t = returnSingleLegTrades(tradeFileFullName);
                o = returnSingleLegTrades(orderFileFullName);
                out = reconTrades(t, o, account, "Order");
                break;

            case "Combo":
                t = returnComboParent(tradeFileFullName);
                o = returnComboParent(orderFileFullName);
                out = reconTrades(t, o, account, "Order");
                break;
            default:
                break;
        }

        return out;
    }

    private static ExtendedHashMap<String,String,String> returnSingleLegTrades(String fileName) {
        
        ArrayList<Integer> childEntryOrders = new ArrayList<>();
                ExtendedHashMap<String, String, String> tradelist = new ExtendedHashMap<>();
                InputStream initialStream;
                try{
                    initialStream = new FileInputStream(new File(fileName));
                JsonReader jr = new JsonReader(initialStream);
                tradelist = (ExtendedHashMap<String, String, String>) jr.readObject();
                jr.close();
                }catch (Exception e){
                    logger.log(Level.SEVERE,null,e);
                }
                
        //Remove orders that are not in symbolist
        Set<Entry>entries=tradelist.entrySet();
                Iterator iter1=entries.iterator();
        while(iter1.hasNext()){
            Map.Entry pair = (Map.Entry)iter1.next();
            String key=(String)pair.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if(childid<0){
                iter1.remove();
            }
        }
        
        for (Entry entry : entries) {
            String key=(String)entry.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (Parameters.symbol.get(childid).getType().equals("COMBO")) {
                childEntryOrders.add(Trade.getEntryOrderIDInternal(tradelist, key));
            }
        }

        Iterator iter2 = entries.iterator();
        while (iter2.hasNext()) {
            Map.Entry pair = (Map.Entry) iter2.next();
            String key=(String)pair.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            int entryorderidint=Trade.getEntryOrderIDInternal(tradelist, key);
            if (childEntryOrders.contains(Integer.valueOf(entryorderidint)) && !Parameters.symbol.get(childid).getType().equals("COMBO")) {
                iter2.remove();
            }
        }

        Iterator iter3 = entries.iterator();
        while (iter3.hasNext()) {
            Map.Entry pair = (Map.Entry) iter2.next();
            String key=(String)pair.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (Parameters.symbol.get(childid).getType().equals("COMBO")) {
                iter3.remove();
            }
        }
        return tradelist;

    }

    private static ArrayList<String> returnSingleLegTrades(ExtendedHashMap<String,String,String> trades,ArrayList<String> keys, String accountName) {
        //Remove orders that are not in symbolist
        Iterator iter1=keys.iterator();
        while(iter1.hasNext()){
             Map.Entry pair = (Map.Entry) iter1.next();
             String key = (String) pair.getKey();
             String childdisplayname=Trade.getEntrySymbol(trades, key);
             int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if(childid<0){
                iter1.remove();
            }
        }
        ArrayList<Integer> childEntryOrders = new ArrayList<>();
        for (String key : keys) { //identify combo orders
            String childdisplayname=Trade.getEntrySymbol(trades, key);
             int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            String account=Trade.getAccountName(trades, key);
            int entryorderidint=Trade.getEntryOrderIDInternal(trades, key);
            if (Parameters.symbol.get(childid).getType().equals("COMBO") && account.equals(accountName)) {
                childEntryOrders.add(entryorderidint);
            }
        }

        Iterator iter2 = keys.iterator();
        while (iter2.hasNext()) { //remove orders that are linked to the combo and are not combo themselves. i.e. combo child orders will be removed
            String key = (String) iter2.next();
            int entryorderidint=Trade.getEntryOrderIDInternal(trades, key);
            String childdisplayname=Trade.getEntrySymbol(trades, key);
             int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            String account=Trade.getAccountName(trades, key);
            if (childEntryOrders.contains(Integer.valueOf(entryorderidint)) && !Parameters.symbol.get(childid).getType().equals("COMBO") || !account.equals(accountName)) {
                iter2.remove();
            }
        }

        Iterator iter3 = keys.iterator();
        while (iter3.hasNext()) {
            String key = (String) iter2.next();
            int entryorderidint=Trade.getEntryOrderIDInternal(trades, key);
            String childdisplayname=Trade.getEntrySymbol(trades, key);
             int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            String account=Trade.getAccountName(trades, key);
            if (Parameters.symbol.get(childid).getType().equals("COMBO") || !account.equals(accountName)) {
                iter3.remove();
            }
        }
        return keys;

    }

    private static ExtendedHashMap<String,String,String> returnComboParent(String fileName) {
                ExtendedHashMap<String, String, String> tradelist = new ExtendedHashMap<>();
                InputStream initialStream;
                try{
                    initialStream = new FileInputStream(new File(fileName));
                JsonReader jr = new JsonReader(initialStream);
                tradelist = (ExtendedHashMap<String, String, String>) jr.readObject();
                jr.close();
                }catch (Exception e){
                    logger.log(Level.SEVERE,null,e);
                }
                //Remove orders that are not in symbolist
        Set<Entry>entries=tradelist.entrySet();
                Iterator iter1=entries.iterator();
        while(iter1.hasNext()){
            Map.Entry pair = (Map.Entry)iter1.next();
            String key=(String)pair.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if(childid<0){
                iter1.remove();
            }
        }
        Iterator iter2 = entries.iterator();
        while (iter2.hasNext()) {
            Map.Entry pair = (Map.Entry)iter1.next();
            String key=(String)pair.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (!Parameters.symbol.get(childid).getType().equals("COMBO")) {
                iter2.remove();
            }
        }
        return tradelist;
    }

    private static ArrayList<String> returnComboParent(ExtendedHashMap<String,String,String> trades,ArrayList<String> tradelist, String accountName) {
                //Remove orders that are not in symbolist
        Iterator iter1=tradelist.iterator();
        while(iter1.hasNext()){
            String key = (String) iter1.next();
                        String childdisplayname=Trade.getEntrySymbol(trades, key);
             int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);

            if(childid<0){
                iter1.remove();
            }
        }
        
        Iterator iter2 = tradelist.iterator();
        while (iter2.hasNext()) {
            String key = (String) iter2.next();
            String childdisplayname=Trade.getEntrySymbol(trades, key);
             int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            String account=Trade.getAccountName(trades, key);
            if (!Parameters.symbol.get(childid).getType().equals("COMBO") || !account.equals(accountName)) {
                iter2.remove();
            }
        }
        return tradelist;
    }

    private static ExtendedHashMap<String,String,String> returnComboChildren(String fileName) {
        ArrayList<Integer> comboOrders = new ArrayList<>();
              ExtendedHashMap<String, String, String> tradelist = new ExtendedHashMap<>();
                try{
                    InputStream initialStream = new FileInputStream(new File(fileName));
                JsonReader jr = new JsonReader(initialStream);
                tradelist = (ExtendedHashMap<String, String, String>) jr.readObject();
                jr.close();
                }catch (Exception e){
                    logger.log(Level.SEVERE,null,e);
                }
        //Remove orders that are not in symbolist
        Set<Entry>entries=tradelist.entrySet();
                Iterator iter1=entries.iterator();
        while(iter1.hasNext()){
            Map.Entry pair = (Map.Entry)iter1.next();
            String key=(String)pair.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if(childid<0){
                iter1.remove();
            }
        }
        
        for (Entry entry : entries) {
            String key=(String)entry.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int entryorderidint=Trade.getEntryOrderIDInternal(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (Parameters.symbol.get(childid).getType().equals("COMBO")) {
                comboOrders.add(entryorderidint);
            }
        }
        Iterator iter2 = entries.iterator();
        while (iter2.hasNext()) {
            Map.Entry pair = (Map.Entry)iter1.next();
            String key=(String)pair.getKey();
            String childdisplayname=Trade.getEntrySymbol(tradelist, key);
            int entryorderidint=Trade.getEntryOrderIDInternal(tradelist, key);
            int childid=Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
            if (!comboOrders.contains(Integer.valueOf(entryorderidint)) || comboOrders.contains(Integer.valueOf(entryorderidint)) && Parameters.symbol.get(childid).getType().equals("COMBO")) {
                iter2.remove();
            }
        }
        return tradelist;

    }

    private static HashMap<String, ArrayList<Integer>> reconTrades(ExtendedHashMap<String,String,String> tr, ExtendedHashMap<String,String,String> or, String tradeAccount, String orderAccount) {
        HashMap<String, ArrayList<Integer>> out = new HashMap<>(); //ArrayList contains two values: Index 0 is expected, index 1 is actual
        SortedMap<String, Integer> tradePosition = new TreeMap<>();
        SortedMap<String, Integer> orderPosition = new TreeMap<>();
        Set<Entry>entryTR=tr.entrySet();
        for (Entry entry : entryTR) {
            String key=(String)entry.getKey();
            String accountName=Trade.getAccountName(tr, key);
            String childdisplayname=Trade.getEntrySymbol(tr, key);
            EnumOrderSide entrySide=Trade.getEntrySide(tr, key);
            EnumOrderSide exitSide=Trade.getExitSide(tr, key);
            int entrySize=Trade.getEntrySize(tr, key);
            int exitSize=Trade.getExitSize(tr, key);
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
        Set<Entry>entryOR=or.entrySet();
        for (Entry entry : entryOR) {
            String key=(String)entry.getKey();
            String accountName=Trade.getAccountName(tr, key);
            String childdisplayname=Trade.getEntrySymbol(tr, key);
            EnumOrderSide entrySide=Trade.getEntrySide(tr, key);
            EnumOrderSide exitSide=Trade.getExitSide(tr, key);
            int entrySize=Trade.getEntrySize(tr, key);
            int exitSize=Trade.getExitSize(tr, key);
        
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
        for (Map.Entry<String, Integer> entry : orderPosition.entrySet()) {
            String key = entry.getKey();
            if (!entry.getValue().equals(tradePosition.get(key))) {
                ArrayList<Integer> i = new ArrayList<Integer>();
                i.add(entry.getValue());
                i.add(tradePosition.get(key));
                out.put(key, i);
            }
        }
        return out;
    }

    private static HashMap<String, HashMap<String, ArrayList<Integer>>> reconComboChildren(ExtendedHashMap<String,String,String> combos, ExtendedHashMap<String,String,String> children, String tradeAccount) {
        HashMap<String, HashMap<String, ArrayList<Integer>>> out = new HashMap<>(); //ArrayList contains two values: Index 0 is expected, index 1 is actual
        SortedMap<String, HashMap<String, Integer>> comboPosition = new TreeMap<>();
        SortedMap<String, HashMap<String, Integer>> childPosition = new TreeMap<>();
        Set<Entry> entryCombos=combos.entrySet();
        for (Entry entry : entryCombos) {
            String key=(String)entry.getKey();
            String accountName=Trade.getAccountName(combos, key);
            String childdisplayname=Trade.getEntrySymbol(combos, key);
            String parentdisplayname=Trade.getParentSymbol(combos, key);
            EnumOrderSide entrySide=Trade.getEntrySide(combos, key);
            EnumOrderSide exitSide=Trade.getExitSide(combos, key);
            int entrySize=Trade.getEntrySize(combos, key);
            int exitSize=Trade.getExitSize(combos, key);
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
        Set<Entry> entryChildren=children.entrySet();
        for (Entry entry : entryChildren) {
            String key=(String)entry.getKey();
            String accountName=Trade.getAccountName(combos, key);
            String childdisplayname=Trade.getEntrySymbol(combos, key);
            String parentdisplayname=Trade.getParentSymbol(combos, key);
            EnumOrderSide entrySide=Trade.getEntrySide(combos, key);
            EnumOrderSide exitSide=Trade.getExitSide(combos, key);
            int entrySize=Trade.getEntrySize(combos, key);
            int exitSize=Trade.getExitSize(combos, key);
            
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
                if(childPosition.get(comboName)==null){//position required as per combo row, but does not exist in child row
                    HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                    if(out.get(comboName)!=null){
                        mismatchChildNameValue=out.get(comboName);
                    }
                    ArrayList<Integer> mismatchValue = new ArrayList<>();
                    mismatchValue.add(child.getValue());//expected
                    mismatchValue.add(0);
                    mismatchChildNameValue.put(child.getKey(), mismatchValue);
                    out.put(comboName, mismatchChildNameValue);
                }else
                if (!child.getValue().equals(childPosition.get(comboName).get(child.getKey()))) {//child positions do not recon
                    HashMap<String, ArrayList<Integer>> mismatchChildNameValue = new HashMap<>();//i => value of out variable
                    if(out.get(comboName)!=null){
                        mismatchChildNameValue=out.get(comboName);
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
            int id = Utilities.getIDFromBrokerSymbol(Parameters.symbol,s.getBrokerSymbol(), s.getType(), s.getExpiry(), s.getRight(), s.getOption());
            s.setDisplayname(Parameters.symbol.get(id).getDisplayname());
            out.put(s, Integer.parseInt(components[5]));
        }
        return out;


    }
}
