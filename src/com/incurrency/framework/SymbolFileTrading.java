/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.ScanResult;

/**
 *
 * @author psharma
 */
public class SymbolFileTrading {

    private JedisPool jPool;
    private String currentDay;
    private List<BeanSymbol> nifty50 = new ArrayList<>();
    private List<BeanSymbol> symbols = new ArrayList<>();
    private List<BeanSymbol> fno = new ArrayList<>();
    private String symbolFileName;
    private static final Logger logger = Logger.getLogger(SymbolFileTrading.class.getName());

    public SymbolFileTrading(String redisurl, String symbolFileName) {
        this.symbolFileName = symbolFileName;
        jPool = RedisConnect(redisurl.split(":")[0], Integer.valueOf(redisurl.split(":")[1]), Integer.valueOf(redisurl.split(":")[2]));
        currentDay = DateUtil.getFormatedDate("yyyyMMdd", new Date().getTime(), TimeZone.getTimeZone(MainAlgorithm.timeZone));
        //currentDay="20160930";
        loadAllSymbols();
        nifty50 = loadNifty50Stocks();
        swing();
    }

    public static JedisPool RedisConnect(String uri, Integer port, Integer database) {
        return new JedisPool(new JedisPoolConfig(), uri, port, 2000, null, database);
    }

    public void swing() {
        try {
            ArrayList<BeanSymbol> out = new ArrayList<>();
            String expiry = Utilities.getNextExpiry(currentDay);
            BeanSymbol s = new BeanSymbol("NIFTY50", "NSENIFTY", "IND", "", "", "");
            s.setCurrency("INR");
            s.setExchange("NSE");
            s.setStreamingpriority(1);
            s.setStrategy("SWING:OPTSALE");
            out.add(s);
            s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", expiry, "", "");
            s.setCurrency("INR");
            s.setExchange("NSE");
            s.setStreamingpriority(1);
            s.setStrategy("SWING:OPTSALE");
            s.setMinsize(75);
            s.setStrikeDistance(100);
            out.add(s);
            Date dtExpiry=new SimpleDateFormat("yyyyMMdd").parse(expiry);
            String expiryplus=DateUtil.getFormatedDate("yyyyMMdd", DateUtil.addDays(dtExpiry, 1).getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
           
            s = new BeanSymbol("NIFTY50", "NSENIFTY", "FUT", Utilities.getNextExpiry(expiryplus), "", "");
            s.setCurrency("INR");
            s.setExchange("NSE");
            s.setStreamingpriority(1);
            s.setStrategy("SWING:OPTSALE");
            s.setMinsize(75);
            s.setStrikeDistance(100);
            out.add(s);

            ArrayList<BeanSymbol> out2 = new ArrayList<>();
            for (int i = 0; i < nifty50.size(); i++) {
                s = nifty50.get(i);
                s.setStrategy("CONTRA");
                out2.add(s);
            }
            fno = loadFutures(expiry);
            for (int i = 0; i < fno.size(); i++) {
                String exchangesymbol = fno.get(i).getExchangeSymbol();
                int id = Utilities.getIDFromExchangeSymbol(nifty50, exchangesymbol, "STK", "", "", "");
                if (id >= 0) {
                    id = Utilities.getIDFromExchangeSymbol(fno, exchangesymbol, "FUT", expiry, "", "");
                    s = fno.get(id);
                    BeanSymbol s1 = s.clone(s);
                    s1.setStreamingpriority(2);
                    s1.setStrategy("CONTRA");
                    out2.add(s1);
                }
            }
            ArrayList<BeanSymbol> fwdout = loadFutures(Utilities.getNextExpiry(expiryplus));            
            for (int i = 0; i < fno.size(); i++) {
                String exchangesymbol = fno.get(i).getExchangeSymbol();
                int id = Utilities.getIDFromExchangeSymbol(nifty50, exchangesymbol, "STK", "", "", "");
                if (id >= 0) {
                    id = Utilities.getIDFromExchangeSymbol(fwdout, exchangesymbol, "FUT", Utilities.getNextExpiry(expiryplus), "", "");
                    s = fwdout.get(id);
                    BeanSymbol s1 = s.clone(s);
                    s1.setStreamingpriority(2);
                    s1.setStrategy("CONTRA");
                    out2.add(s1);
                }
            }
            out.addAll(out2);
            Utilities.printSymbolsToFile(out, symbolFileName,false);
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    public void loadAllSymbols() {
        String cursor = "";
        String shortlistedkey = "";
        while (!cursor.equals("0")) {
            cursor = cursor.equals("") ? "0" : cursor;
            try (Jedis jedis = jPool.getResource()) {
                ScanResult s = jedis.scan(cursor);
                cursor = s.getStringCursor();
                for (Object key : s.getResult()) {
                    if (key.toString().contains("ibsymbols")) {
                        if (shortlistedkey.equals("")) {
                            shortlistedkey = key.toString();
                        } else {
                            int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                            int newdate = Integer.valueOf(key.toString().split(":")[1]);
                            if (newdate > date) {
                                shortlistedkey = key.toString();//replace with latest nifty setup
                            }
                        }
                    }
                }
            }
        }
        Map<String, String> ibsymbols = new HashMap<>();
        try (Jedis jedis = jPool.getResource()) {
            ibsymbols = jedis.hgetAll(shortlistedkey);
            for (Map.Entry<String, String> entry : ibsymbols.entrySet()) {
                String exchangeSymbol = entry.getKey().trim().toUpperCase();
                String brokerSymbol = entry.getValue().trim().toUpperCase();
                BeanSymbol tempContract = new BeanSymbol();
                tempContract.setExchange("NSE");
                tempContract.setCurrency("INR");
                tempContract.setType("STK");
                tempContract.setExchangeSymbol(exchangeSymbol);
                tempContract.setBrokerSymbol(brokerSymbol);
                tempContract.setSerialno(symbols.size()+1);
                symbols.add(tempContract);
            }

        }

    }

    public ArrayList<BeanSymbol> loadNifty50Stocks() {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String cursor = "";
            String shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("nifty50")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(Utilities.getNextExpiry(currentDay))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Set<String> niftySymbols = new HashSet<>();
            try (Jedis jedis = jPool.getResource()) {
                niftySymbols = jedis.smembers(shortlistedkey);
                Iterator iterator = niftySymbols.iterator();
                while (iterator.hasNext()) {
                    String exchangeSymbol = iterator.next().toString().toUpperCase();
                    int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = symbols.get(id);
                        BeanSymbol s1 = s.clone(s);
                        out.add(s1);
                    }else{
                        logger.log(Level.SEVERE,"500,NIFTY50 symbol not found in ibsymbols,{0}:{1}:{2}:{3}:{4},SymbolNotFound={5}",
                                new Object[]{"Unknown","Unknown","Unknown",-1,-1,exchangeSymbol});
                    }
                }
            }
            for (int i = 0; i < out.size(); i++) {
                out.get(i).setSerialno(i + 1);
            }

            //Capture Strike levels
            cursor = "";
            shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("strikedistance")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(Utilities.getNextExpiry(currentDay).substring(0, 6))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(out, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = out.get(id);
                        s.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;

    }

    public ArrayList<BeanSymbol> loadFutures(String expiry) {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        ArrayList<BeanSymbol> interimout = new ArrayList<>();
        try {
            String cursor = "";
            String shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("contractsize")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(expiry.substring(0, 6))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Map<String, String> contractSizes = new HashMap<>();
            try (Jedis jedis = jPool.getResource()) {
                contractSizes = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : contractSizes.entrySet()) {
                    String exchangeSymbol = entry.getKey().trim().toUpperCase();
                    int minsize = Utilities.getInt(entry.getValue().trim(), 0);
                    if (minsize > 0) {
                        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                        if (id >= 0) {
                            BeanSymbol s = symbols.get(id);
                            BeanSymbol s1 = s.clone(s);
                            s1.setType("FUT");
                            s1.setExpiry(expiry);
                            s1.setMinsize(minsize);
                            s1.setStrategy("DATA");
                            s1.setStreamingpriority(2);
                            s1.setSerialno(out.size() + 1);
                            interimout.add(s1);
                        } else {
                            logger.log(Level.SEVERE, "Exchange Symbol {} not found in IB database", new Object[]{exchangeSymbol});
                        }
                    }
                }
            }

            //Fix sequential serial numbers
            for (int i = 0; i < interimout.size(); i++) {
                interimout.get(i).setSerialno(i + 1);
            }

            //Capture Strike levels
            cursor = "";
            shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("strikedistance")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(Utilities.getNextExpiry(currentDay).substring(0, 6))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(interimout, exchangeSymbol, "FUT", expiry, "", "");
                    if (id >= 0) {
                        BeanSymbol s = interimout.get(id);
                        BeanSymbol s1 = s.clone(s);
                        s1.setType("FUT");
                        s1.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                        out.add(s1);
                    }
                }
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        for (int i = 0; i < out.size(); i++) {
            out.get(i).setSerialno(i + 1);
        }
        return out;

    }

    public ArrayList<BeanSymbol> loadCNX500Stocks() {
        ArrayList<BeanSymbol> out = new ArrayList<>();
        try {
            String cursor = "";
            String shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("cnx500")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(Utilities.getNextExpiry(currentDay))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Set<String> niftySymbols = new HashSet<>();
            try (Jedis jedis = jPool.getResource()) {
                niftySymbols = jedis.smembers(shortlistedkey);
                Iterator iterator = niftySymbols.iterator();
                while (iterator.hasNext()) {
                    String exchangeSymbol = iterator.next().toString().toUpperCase();
                    int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = symbols.get(id);
                        BeanSymbol s1 = s.clone(s);
                        out.add(s1);
                    }
                }
            }
            for (int i = 0; i < out.size(); i++) {
                out.get(i).setSerialno(i + 1);
            }

            //Capture Strike levels
            cursor = "";
            shortlistedkey = "";
            while (!cursor.equals("0")) {
                cursor = cursor.equals("") ? "0" : cursor;
                try (Jedis jedis = jPool.getResource()) {
                    ScanResult s = jedis.scan(cursor);
                    cursor = s.getStringCursor();
                    for (Object key : s.getResult()) {
                        if (key.toString().contains("strikedistance")) {
                            if (shortlistedkey.equals("")) {
                                shortlistedkey = key.toString();
                            } else {
                                int date = Integer.valueOf(shortlistedkey.split(":")[1]);
                                int newdate = Integer.valueOf(key.toString().split(":")[1]);
                                if (newdate > date && newdate <= Integer.valueOf(Utilities.getNextExpiry(currentDay).substring(0, 6))) {
                                    shortlistedkey = key.toString();//replace with latest nifty setup
                                }
                            }
                        }
                    }
                }
            }
            Map<String, String> strikeLevels = new HashMap<>();
            try (Jedis jedis = jPool.getResource()) {
                strikeLevels = jedis.hgetAll(shortlistedkey);
                for (Map.Entry<String, String> entry : strikeLevels.entrySet()) {
                    String exchangeSymbol = entry.getKey().toUpperCase();//2nd column of nse file                        
                    int id = Utilities.getIDFromExchangeSymbol(out, exchangeSymbol, "STK", "", "", "");
                    if (id >= 0) {
                        BeanSymbol s = out.get(id);
                        s.setStrikeDistance(Double.parseDouble(entry.getValue().trim()));
                    }
                }
            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return out;
    }
}
