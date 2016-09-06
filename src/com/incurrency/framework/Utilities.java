/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.cedarsoftware.util.io.JsonObject;
import com.cedarsoftware.util.io.JsonReader;
import com.cedarsoftware.util.io.JsonWriter;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.incurrency.RatesClient.RequestClient;
import static com.incurrency.framework.TradingUtil.logger;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jquantlib.Settings;
import org.jquantlib.daycounters.Actual360;
import org.jquantlib.daycounters.Actual365Fixed;
import org.jquantlib.exercise.EuropeanExercise;
import org.jquantlib.instruments.EuropeanOption;
import org.jquantlib.instruments.Option;
import org.jquantlib.instruments.PlainVanillaPayoff;
import org.jquantlib.processes.BlackScholesMertonProcess;
import org.jquantlib.quotes.Handle;
import org.jquantlib.quotes.Quote;
import org.jquantlib.quotes.SimpleQuote;
import org.jquantlib.termstructures.BlackVolTermStructure;
import org.jquantlib.termstructures.YieldTermStructure;
import org.jquantlib.termstructures.volatilities.BlackConstantVol;
import org.jquantlib.termstructures.yieldcurves.FlatForward;
import org.jquantlib.time.JDate;
import org.jquantlib.time.Period;
import org.jquantlib.time.TimeUnit;
import org.jquantlib.time.calendars.India;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.DataPoint;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.QueryMetric;
import org.kairosdb.client.response.QueryResponse;

/**
 *
 * @author Pankaj
 */
public class Utilities {

    private static final Logger logger = Logger.getLogger(Utilities.class.getName());
    public static String newline = System.getProperty("line.separator");

    public static double getOptionLimitPriceForRel(List<BeanSymbol> symbols,int id, int underlyingid, EnumOrderSide side, String right,double tickSize) {
        double price = symbols.get(id).getLastPrice();
        try {
            if (price == 0 ||price==-1) {
                price=getTheoreticalOptionPrice(symbols,id, underlyingid, side, right,tickSize);
            }
            double bidprice = symbols.get(id).getBidPrice();
            double askprice = symbols.get(id).getAskPrice();
            logger.log(Level.INFO, "Symbol:{0},price:{1},BidPrice:{2},AskPrice:{3}", new Object[]{symbols.get(id).getDisplayname(), price, bidprice, askprice});
            switch (side) {
                case BUY:
                case COVER:
                    if (bidprice > 0) {
                        price = Math.min(bidprice, price);

                    } else {
                        price = 0.80 * price;
                        logger.log(Level.INFO, "Calculated Price as bidprice is zero. Symbol {0}, BidPrice:{1}", new Object[]{Parameters.symbol.get(id).getDisplayname(), price});
                    }
                    break;
                case SHORT:
                case SELL:
                    if (askprice > 0) {
                        price = Math.max(askprice, price);

                    } else {
                        price = 1.2 * price;
                        logger.log(Level.INFO, "Calculated Price as askprice is zero. Symbol {0}, BidPrice:{1}", new Object[]{Parameters.symbol.get(id).getDisplayname(), price});
                    }
                    break;
                default:
                    break;

            }
            price = Utilities.roundTo(price, tickSize);

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return price;
    }

    public static double getTheoreticalOptionPrice(List<BeanSymbol> symbols, int id, int underlyingid, EnumOrderSide side, String right, double tickSize) {
        double price = -1;
        try {
            double optionlastprice = 0;
            Object[] optionlastpriceset = Utilities.getSettlePrice(symbols.get(id), new Date());
            Object[] underlyinglastpriceset = Utilities.getSettlePrice(symbols.get(underlyingid), new Date());
            double underlyingpriorclose = Utilities.getDouble(underlyinglastpriceset[1], 0);

            if (optionlastpriceset != null && optionlastpriceset.length == 2) {
                long settletime = Utilities.getLong(optionlastpriceset[0], 0);
                optionlastprice = Utilities.getDouble(optionlastpriceset[1], 0);
                double vol = Utilities.getImpliedVol(symbols.get(id), underlyingpriorclose, optionlastprice, new Date(settletime));
                if (vol == 0) {
                    if (symbols.get(id).getBidPrice() != 0 && symbols.get(id).getAskPrice() != 0 && symbols.get(underlyingid).getLastPrice() != 0) {
                        optionlastprice = (symbols.get(id).getBidPrice() + symbols.get(id).getAskPrice()) / 2;
                        underlyingpriorclose = symbols.get(underlyingid).getLastPrice();
                        vol = Utilities.getImpliedVol(symbols.get(id), underlyingpriorclose, optionlastprice, new Date());
                    }
                }
                if (vol == 0) {//if vol is still zero
                    if (side == EnumOrderSide.BUY || side == EnumOrderSide.SELL) {
                        vol = 0.05;
                    } else if (side == EnumOrderSide.SHORT || side == EnumOrderSide.COVER) {
                        vol = 0.50;
                    }
                }
                symbols.get(id).setCloseVol(vol);
            }
            price = symbols.get(id).getOptionProcess().NPV();
            price = Utilities.roundTo(price, tickSize);
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
        return price;
    }
    
    public static double getLimitPriceForOrder(List<BeanSymbol> symbols,int id, int underlyingid, EnumOrderSide side,double tickSize,EnumOrderType orderType){
        double price = symbols.get(id).getLastPrice();
        String type=symbols.get(id).getType();
        switch(type){
            case "OPT":
                switch(orderType){
                    case LMT:
                        price=symbols.get(id).getLastPrice();
                        if(price==-1 || price==0){
                            String right=symbols.get(id).getRight();
                            price=getTheoreticalOptionPrice(symbols,id, underlyingid, side, right,tickSize);
                        }
                        break;
                    case MKT:
                        price=0;
                    case CUSTOMREL:
                        String right=symbols.get(id).getRight();
                        price=getOptionLimitPriceForRel(symbols,id, underlyingid, side, right,tickSize);
                    default:
                        break;
                }
                break;
            default:
            switch(orderType){
                case MKT:
                    price=0;
                    break;
                case CUSTOMREL:
                    if (side.equals("BUY") || side.equals("COVER")) {
                        price = Parameters.symbol.get(id).getBidPrice();
                    } else {
                        price = Parameters.symbol.get(id).getAskPrice();
                    }
                    if (price == 0 || price == -1) {
                        price = Parameters.symbol.get(id).getLastPrice();
                    }
                    break;
                }
            break;

        }       
        return price;
    }
    
    public static double getImpliedVol(BeanSymbol s, double underlying, double price, Date evaluationDate) {
        new Settings().setEvaluationDate(new org.jquantlib.time.JDate(evaluationDate));
        String strike = s.getOption();
        String right = s.getRight();
        String expiry = s.getExpiry();
        Date expirationDate = DateUtil.getFormattedDate(expiry, "yyyyMMdd", Algorithm.timeZone);
        EuropeanExercise exercise = new EuropeanExercise(new org.jquantlib.time.JDate(expirationDate));
        PlainVanillaPayoff payoff;
        if (right.equals("PUT")) {
            payoff = new PlainVanillaPayoff(Option.Type.Put, Utilities.getDouble(strike, 0));
        } else {
            payoff = new PlainVanillaPayoff(Option.Type.Call, Utilities.getDouble(strike, 0));
        }
        EuropeanOption option = new EuropeanOption(payoff, exercise);
        Handle<Quote> S = new Handle<Quote>(new SimpleQuote(Utilities.getDouble(underlying, 0D)));
        org.jquantlib.time.Calendar india = new India();
        Handle<YieldTermStructure> rate = new Handle<YieldTermStructure>(new FlatForward(0, india, 0.07, new Actual365Fixed()));
        Handle<YieldTermStructure> yield = new Handle<YieldTermStructure>(new FlatForward(0, india, 0.015, new Actual365Fixed()));
        Handle<BlackVolTermStructure> sigma = new Handle<BlackVolTermStructure>(new BlackConstantVol(0, india, 0.20, new Actual365Fixed()));
        BlackScholesMertonProcess process = new BlackScholesMertonProcess(S, yield, rate, sigma);
        double vol=0;
        try{
            vol = option.impliedVolatility(price, process);
        }catch (Exception e){
            logger.log(Level.SEVERE,"Could not calculte vol for Symbol:{0}. OptionPrice:{1},Underlying:{2}",new Object[]{
            s.getDisplayname(),price,underlying});
        }
        new Settings().setEvaluationDate(new org.jquantlib.time.JDate(new Date()));
        return vol;

    }
   

    /**
     *
     * @param s
     * @param timeSeries - example {"open","close"}
     * @param metric - example "india.nse.index.s4.daily"
     * @param startTime format 20150101 00:00:00 or yyyyMMdd HH:mm:ss
     * @param endTime format 20150101 00:00:00 or yyyyMMdd HH:mm:ss
     * @param barSize
     * @param appendAtEnd data retrieved from the request is appended to the end
     * of specified output text file
     * @return
     */
    public static BeanSymbol requestHistoricalData(BeanSymbol s, String[] timeSeries, String metric, String datetimeFormat, String startTime, String endTime, EnumBarSize barSize, boolean appendAtEnd) {
        try {
            SimpleDateFormat sdfExtendedTimeFormat = new SimpleDateFormat(datetimeFormat);
            Date startDate = sdfExtendedTimeFormat.parse(startTime);
            Date endDate = sdfExtendedTimeFormat.parse(endTime);
            String path = Algorithm.globalProperties.getProperty("historicaldataserver").toString().toLowerCase();
            RequestClient rc = new RequestClient(path);
            String concatMetrics = null;
            for (String m : timeSeries) {
                if (concatMetrics == null) {
                    concatMetrics = metric + "." + m;
                } else {
                    concatMetrics = concatMetrics + "," + metric + "." + m;
                }
            }
            rc.sendRequest("backfill", s, new String[]{barSize.toString(), String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())}, concatMetrics, timeSeries, appendAtEnd);
            rc.run();
            rc.start.put("start");
            String finished = rc.end.take();


        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
        } finally {
            return s;
        }
    }

    public static Object[] getSettlePrice(BeanSymbol s, Date d) {
        Object[] obj = new Object[2];
        try {
            HttpClient client = new HttpClient("http://" + Algorithm.cassandraIP + ":8085");
            String metric;
            switch (s.getType()) {
                case "STK":
                    metric = "india.nse.equity.s4.daily.settle";
                    break;
                case "FUT":
                    metric = "india.nse.future.s4.daily.settle";
                    break;
                case "OPT":
                    metric = "india.nse.option.s4.daily.settle";
                    break;
                case "IND":
                    metric = "india.nse.index.s4.daily.settle";
                    break;
                default:
                    metric = null;
                    break;
            }
            String strike= Utilities.formatDouble(Utilities.getDouble(s.getOption(), 0), new DecimalFormat("#.##"));

            Date startDate = DateUtil.addDays(d, -10);
            Date endDate = d;
            QueryBuilder builder = QueryBuilder.getInstance();
            String symbol=null;
            if(s.getExchangeSymbol()!=null){
                symbol=s.getExchangeSymbol().replaceAll("[^A-Za-z0-9\\-]", "").toLowerCase();
            }else{
                symbol=s.getDisplayname().split("_",-1)[0];
                symbol=symbol.replaceAll("[^A-Za-z0-9\\-]", "").toLowerCase();
            }
            builder.setStart(startDate)
                    .setEnd(endDate)
                    .addMetric(metric)
                    .addTag("symbol", symbol);
            if (s.getExpiry() != null && !s.getExpiry().equals("")) {
                builder.getMetrics().get(0).addTag("expiry", s.getExpiry());
            }
            if (s.getRight() != null && !s.getRight().equals("")) {
                builder.getMetrics().get(0).addTag("option", s.getRight());
                builder.getMetrics().get(0).addTag("strike", strike);
            }

            builder.getMetrics().get(0).setLimit(1);
            builder.getMetrics().get(0).setOrder(QueryMetric.Order.DESCENDING);
            long time = new Date().getTime();
            QueryResponse response = client.query(builder);

            List<DataPoint> dataPoints = response.getQueries().get(0).getResults().get(0).getDataPoints();
            for (DataPoint dataPoint : dataPoints) {
                long lastTime = dataPoint.getTimestamp();
                obj[0] = lastTime;
                obj[1] = dataPoint.getValue();
            }
        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
        }
        return obj;
    }

    public static Object[] getLastSettlePriceOption(List<BeanSymbol> symbols, int id, long startTime, long endTime, String metric) {
        Object[] out = new Object[2];
        HashMap<String, Object> param = new HashMap();
        param.put("TYPE", Boolean.FALSE);
        BeanSymbol s = symbols.get(id);
        String strike = s.getOption();
        String symbol = s.getDisplayname().split("_", -1)[0].replaceAll("[^A-Za-z0-9]", "").trim().toLowerCase();
        String option = s.getRight();
        String expiry = s.getExpiry();
        HistoricalRequestJson request = new HistoricalRequestJson(metric,
                new String[]{"strike", "symbol", "option", "expiry"},
                new String[]{strike, symbol, option, expiry},
                "1",
                "days",
                "last",
                String.valueOf(startTime),
                String.valueOf(endTime));
        //http://stackoverflow.com/questions/7181534/http-post-using-json-in-java
        String json_string = JsonWriter.objectToJson(request, param);
        StringEntity requestEntity = new StringEntity(
                json_string,
                ContentType.APPLICATION_JSON);

        HttpPost postMethod = new HttpPost("http://91.121.165.108:8085/api/v1/datapoints/query");
        postMethod.setEntity(requestEntity);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpResponse rawResponse = httpClient.execute(postMethod);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader((rawResponse.getEntity().getContent())));

            String output;
            System.out.println("Output from Server .... \n");
            while ((output = br.readLine()) != null) {
                param.clear();
                param.put("USE_MAPS", "false");
                JsonObject obj = (JsonObject) JsonReader.jsonToJava(output, param);
                JsonObject t = (JsonObject) ((Object[]) obj.get("queries"))[0];
                JsonObject results = (JsonObject) ((Object[]) t.get("results"))[0];
                Object[] values = (Object[]) results.get("values");
                int length = values.length;
                Object[] outarray = (Object[]) values[length - 1];
                out = outarray; //0 is long time, 1 is value

            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }

        return out;
    }
    
    public static Object[] getOptionStrikesFromKDB(List<BeanSymbol> symbols, int id, long startTime, long endTime, String metric){
        Object[] out=new Object[1];
        HashMap<String, Object> param = new HashMap();
        param.put("TYPE", Boolean.FALSE);
        BeanSymbol s = symbols.get(id);
        String strike = s.getOption();
        String symbol = s.getDisplayname().split("_", -1)[0].replaceAll("[^A-Za-z0-9]", "").trim().toLowerCase();
        String option = s.getRight();
        String expiry = s.getExpiry();
        HistoricalRequestJson request = new HistoricalRequestJson(metric,
                new String[]{"strike", "symbol", "expiry"},
                new String[]{strike, symbol, expiry},
                null,
                null,
                null,
                String.valueOf(startTime),
                String.valueOf(endTime));
        //http://stackoverflow.com/questions/7181534/http-post-using-json-in-java
        String json_string = JsonWriter.objectToJson(request, param);
        StringEntity requestEntity = new StringEntity(
                json_string,
                ContentType.APPLICATION_JSON);

        HttpPost postMethod = new HttpPost("http://91.121.165.108:8085/api/v1/datapoints/query/tags");
        postMethod.setEntity(requestEntity);
        CloseableHttpClient httpClient = HttpClients.createDefault();
        try {
            HttpResponse rawResponse = httpClient.execute(postMethod);
            BufferedReader br = new BufferedReader(
                    new InputStreamReader((rawResponse.getEntity().getContent())));

            String output;
            System.out.println("Output from Server .... \n");
            while ((output = br.readLine()) != null) {
                param.clear();
                param.put("USE_MAPS", "false");
                JsonObject obj = (JsonObject) JsonReader.jsonToJava(output, param);
                JsonObject t = (JsonObject) ((Object[]) obj.get("queries"))[0];
                JsonObject results = (JsonObject) ((Object[]) t.get("results"))[0];
                Object[] values = (Object[]) results.get("values");
                int length = values.length;
                Object[] outarray = (Object[]) values[length - 1];
                out = values; //0 is long time, 1 is value

            }

        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }

       
        return out;
    }
    
     public static boolean rolloverDay(int daysBeforeExpiry,Date startDate,String expiryDate) {
        boolean rollover = false;
        try {
            SimpleDateFormat sdf_yyyyMMdd = new SimpleDateFormat("yyyyMMdd");
            String currentDay = sdf_yyyyMMdd.format(startDate);
            Date today = sdf_yyyyMMdd.parse(currentDay);
            JDate jToday=new JDate(today);
            Calendar expiry = Calendar.getInstance();
            expiry.setTime(sdf_yyyyMMdd.parse(expiryDate));
            JDate jExpiry=new JDate(expiry.getTime());
            JDate jAdjExpiry=Algorithm.ind.advance(jExpiry, -daysBeforeExpiry, TimeUnit.Days);
            //expiry.set(Calendar.DATE, expiry.get(Calendar.DATE) - daysBeforeExpiry);
            if (jAdjExpiry.le(jToday)) {
                rollover = true;
            }
        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
        }
        return rollover;
    }
    
    
    public static int openPositionCount(Database<String, String> db, List<BeanSymbol> symbols, String strategy, double pointValue, boolean longPositionOnly) {
        int out = 0;
        HashSet<String> temp = new HashSet<>();;
        HashMap<Integer, BeanPosition> position = new HashMap<>();
        for (BeanSymbol s : symbols) {
            position.put(s.getSerialno() - 1, new BeanPosition(s.getSerialno() - 1, strategy));
        }
        for (String key : db.getKeys("opentrades_"+strategy)) {
            if (key.contains("_" + strategy)) {
                String childdisplayname = Trade.getEntrySymbol(db, key);
                String parentdisplayname = Trade.getParentSymbol(db, key);
                int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
                int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
                if (longPositionOnly) {
                    if (childid == parentid && Trade.getEntrySide(db, key).equals(EnumOrderSide.BUY)) {//not a combo child leg
                        temp.add(parentdisplayname);
                    }
                } else if (!longPositionOnly) {
                    if (childid == parentid && Trade.getEntrySide(db, key).equals(EnumOrderSide.SHORT)) {//not a combo child leg
                        temp.add(parentdisplayname);
                    }
                }
            }
        }
        return temp.size();
    }

    public static void loadMarketData(String filePath, String displayName, List<BeanSymbol> symbols) {
        int id = Utilities.getIDFromDisplayName(symbols, displayName);
        EnumBarSize barSize = EnumBarSize.valueOf(filePath.split("_")[1].split("\\.")[0]);
        if (id >= 0) {
            File dir = new File("logs");
            File inputFile = new File(dir, filePath);
            if (inputFile.exists() && !inputFile.isDirectory()) {
                try {
                    List<String> existingDataLoad = Files.readAllLines(inputFile.toPath(), StandardCharsets.UTF_8);
                    String[] labels = existingDataLoad.get(0).toLowerCase().split(",");
                    String[] formattedLabels = new String[labels.length - 2];
                    for (int i = 0; i < formattedLabels.length; i++) {
                        formattedLabels[i] = labels[i + 2];
                    }
                    existingDataLoad.remove(0);
                    BeanSymbol s = symbols.get(id);
                    for (String symbolLine : existingDataLoad) {
                        if (!symbolLine.equals("")) {
                            String[] input = symbolLine.split(",");
                            //format date
                            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
                            SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
                            TimeZone timeZone = TimeZone.getTimeZone(Algorithm.globalProperties.getProperty("timezone").toString().trim());
                            Calendar c = Calendar.getInstance(timeZone);
                            Date d = sdfDate.parse(input[0]);
                            c.setTime(d);
                            String[] timeOfDay = input[1].split(":");
                            c.set(Calendar.HOUR_OF_DAY, Integer.valueOf(timeOfDay[0]));
                            c.set(Calendar.MINUTE, Integer.valueOf(timeOfDay[1]));
                            c.set(Calendar.SECOND, Integer.valueOf(timeOfDay[2]));
                            c.set(Calendar.MILLISECOND, 0);
                            long time = c.getTimeInMillis();
                            String s_time = String.valueOf(time);
                            String[] formattedInput = new String[input.length - 1];
                            formattedInput[0] = s_time;
                            for (int i = 1; i < formattedInput.length; i++) {
                                formattedInput[i] = input[i + 1];
                            }
                            logger.log(Level.FINER, "Time:{0}, Symbol:{1}, Price:{2}, Values:{3}", new Object[]{new SimpleDateFormat("yyyyMMdd HH:mm:ss").format(new Date(Long.valueOf(formattedInput[0]))), s.getDisplayname(), formattedInput[1], formattedInput.length});
                            s.setTimeSeries(barSize, formattedLabels, formattedInput);
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    /**
     * Returns the long value of starting and ending dates, in a file.Assumes
     * that the first two fields in each row contain date and time.
     *
     * @param filePath
     */
    public static long[] getDateRange(String filePath) {
        long[] dateRange = new long[2];
        File dir = new File("logs");
        File inputFile = new File(dir, filePath);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> existingDataLoad = Files.readAllLines(inputFile.toPath(), StandardCharsets.UTF_8);
                existingDataLoad.remove(0);
                String beginningLine = existingDataLoad.get(0);
                String endLine = existingDataLoad.get(existingDataLoad.size() - 1);
                dateRange[0] = getDateTime(beginningLine);
                dateRange[1] = getDateTime(endLine);
            } catch (Exception e) {
            }
        }
        return dateRange;
    }

    private static long getDateTime(String line) {
        long time = 0;
        if (!line.equals("")) {
            String[] input = line.split(",");
            //format date
            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat sdfTime = new SimpleDateFormat("HH:mm:ss");
            TimeZone timeZone = TimeZone.getTimeZone(Algorithm.globalProperties.getProperty("timezone").toString().trim());
            Calendar c = Calendar.getInstance(timeZone);
            try {
                Date d = sdfDate.parse(input[0]);
                c.setTime(d);
                String[] timeOfDay = input[1].split(":");
                c.set(Calendar.HOUR_OF_DAY, Integer.valueOf(timeOfDay[0]));
                c.set(Calendar.MINUTE, Integer.valueOf(timeOfDay[1]));
                c.set(Calendar.SECOND, Integer.valueOf(timeOfDay[2]));
                c.set(Calendar.MILLISECOND, 0);
                time = c.getTimeInMillis();

            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }

        }
        return time;
    }

    public static <K> String concatStringArray(K[] input) {
        if (input.length > 0) {
            StringBuilder nameBuilder = new StringBuilder();

            for (K n : input) {
                nameBuilder.append(n.toString()).append(",");
            }
            nameBuilder.deleteCharAt(nameBuilder.length() - 1);
            return nameBuilder.toString();
        } else {
            return "";
        }
    }

    public static String concatStringArray(double[] input) {
        if (input.length > 0) {
            StringBuilder nameBuilder = new StringBuilder();

            for (double n : input) {
                nameBuilder.append(n).append(",");
            }
            nameBuilder.deleteCharAt(nameBuilder.length() - 1);
            return nameBuilder.toString();
        } else {
            return "";
        }
    }

    public static <T> String concatArrayList(ArrayList<T> input) {
        if (input.size() > 0) {
            StringBuilder nameBuilder = new StringBuilder();

            for (T n : input) {
                nameBuilder.append(n).append(",");
            }
            nameBuilder.deleteCharAt(nameBuilder.length() - 1);
            return nameBuilder.toString();
        } else {
            return "";
        }
    }

    public static double boxRange(double[] range, double input, int segments) {
        double min = Doubles.min(range);
        double max = Doubles.max(range);
        double increment = (max - min) / segments;
        double[] a_ranges = Utilities.range(min, increment, segments);
        for (int i = 0; i < segments; i++) {
            if (input < a_ranges[i]) {
                return 100 * i / segments;
            }
        }
        return 100;
    }

    /**
     * Returns the next good business day using FB day convention.If
     * weekendHolidays is set to false, weekends are considered as working days
     *
     * @param startDate
     * @param minuteAdjust
     * @param timeZone
     * @param tradeOpenHour
     * @param tradeOpenMinute
     * @param tradeCloseHour
     * @param tradeCloseMinute
     * @param holidays
     * @param weekendHolidays
     * @return
     */
    public static Date nextGoodDay(Date startDate, int minuteAdjust, String timeZone, int tradeOpenHour, int tradeOpenMinute, int tradeCloseHour, int tradeCloseMinute, List<String> holidays, boolean weekendHolidays) {
        Calendar entryCal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        entryCal.setTime(startDate);
        int entryMinute = entryCal.get(Calendar.MINUTE);
        int entryHour = entryCal.get(Calendar.HOUR_OF_DAY);
        //round down entryMinute
        if (entryCal.get(Calendar.MILLISECOND) > 0) {
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        if (entryCal.get(Calendar.HOUR_OF_DAY) > tradeCloseHour || (entryCal.get(Calendar.HOUR_OF_DAY) == tradeCloseHour && entryCal.get(Calendar.MINUTE) > tradeCloseMinute)) {
            entryCal.set(Calendar.HOUR_OF_DAY, tradeCloseHour);
            entryCal.set(Calendar.MINUTE, tradeCloseMinute);
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        Calendar exitCal = (Calendar) entryCal.clone();
        exitCal.setTimeZone(TimeZone.getTimeZone(timeZone));
        exitCal.add(Calendar.MINUTE, minuteAdjust);
        int exitMinute = exitCal.get(Calendar.MINUTE);
        int exitHour = exitCal.get(Calendar.HOUR_OF_DAY);

        //If the exitTime is after market, move to eixtCal to next day BOD.
        if (exitHour > tradeCloseHour || (exitHour == tradeCloseHour && exitMinute >= tradeCloseMinute)) {
            //1.get minutes from close
            int minutesFromClose = (tradeCloseHour - entryHour) > 0 ? (tradeCloseHour - entryHour) * 60 : 0 + tradeCloseMinute - entryMinute;
            int minutesCarriedForward = minuteAdjust - minutesFromClose;
            exitCal.add(Calendar.DATE, 1);
            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.MILLISECOND, 0);
            exitCal.add(Calendar.MINUTE, minutesCarriedForward);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String exitCalString = sdf.format(exitCal.getTime());
        if (weekendHolidays) {
            while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1 || (holidays != null && holidays.contains(exitCalString))) {
                exitCal.add(Calendar.DATE, 1);
                exitCalString = sdf.format(exitCal.getTime());
            }
        }
        if (exitHour < tradeOpenHour || (exitHour == tradeOpenHour && exitMinute < tradeOpenMinute)) {
            exitCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            exitCal.set(Calendar.MINUTE, tradeOpenMinute);
            exitCal.set(Calendar.MILLISECOND, 0);
        }
        return exitCal.getTime();
    }

    /**
     * Returns the previous good business day using PB day convention.If
     * weekendHolidays is set to false, weekends are considered as working days
     *
     * @param startDate
     * @param minuteAdjust
     * @param timeZone
     * @param tradeOpenHour
     * @param tradeOpenMinute
     * @param tradeCloseHour
     * @param tradeCloseMinute
     * @param holidays
     * @param weekendHolidays
     * @return
     */
    public static Date previousGoodDay(Date startDate, int minuteAdjust, String timeZone, int tradeOpenHour, int tradeOpenMinute, int tradeCloseHour, int tradeCloseMinute, List<String> holidays, boolean weekendHolidays) {
        Calendar entryCal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        entryCal.setTime(startDate);
        int entryMinute = entryCal.get(Calendar.MINUTE);
        int entryHour = entryCal.get(Calendar.HOUR_OF_DAY);
        //round down entryMinute
        if (entryCal.get(Calendar.MILLISECOND) > 0) {
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        if (entryCal.get(Calendar.HOUR_OF_DAY) < tradeOpenHour || (entryCal.get(Calendar.HOUR_OF_DAY) == tradeOpenHour && entryCal.get(Calendar.MINUTE) < tradeOpenMinute)) {
            entryCal.set(Calendar.HOUR_OF_DAY, tradeOpenHour);
            entryCal.set(Calendar.MINUTE, tradeOpenMinute);
            entryCal.set(Calendar.MILLISECOND, 0);
        }

        Calendar exitCal = (Calendar) entryCal.clone();
        exitCal.setTimeZone(TimeZone.getTimeZone(timeZone));
        exitCal.add(Calendar.MINUTE, minuteAdjust);
        int exitMinute = exitCal.get(Calendar.MINUTE);
        int exitHour = exitCal.get(Calendar.HOUR_OF_DAY);

        if (exitHour < tradeOpenHour || (exitHour == tradeOpenHour && exitMinute < tradeOpenMinute)) {
            //1.get minutes from close
            int minutesFromOpen = (entryHour - tradeOpenHour) > 0 ? (entryHour - tradeOpenHour) * 60 : 0 + entryMinute - tradeOpenMinute;
            int minutesCarriedForward = minuteAdjust - minutesFromOpen;
            exitCal.add(Calendar.DATE, -1);
            exitCal.set(Calendar.HOUR_OF_DAY, tradeCloseHour);
            exitCal.set(Calendar.MINUTE, tradeCloseMinute);
            exitCal.add(Calendar.MINUTE, -1);
            exitCal.set(Calendar.MILLISECOND, 0);
            exitCal.add(Calendar.MINUTE, -minutesCarriedForward);
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String exitCalString = sdf.format(exitCal.getTime());
        if (weekendHolidays) {
            while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1 || (holidays != null && holidays.contains(exitCalString))) {
                exitCal.add(Calendar.DATE, -1);
                exitCalString = sdf.format(exitCal.getTime());
            }
        }
        if (exitHour > tradeCloseHour || (exitHour == tradeCloseHour && exitMinute >= tradeCloseMinute)) {
            exitCal.set(Calendar.HOUR_OF_DAY, tradeCloseHour);
            exitCal.set(Calendar.MINUTE, tradeCloseMinute);
            exitCal.add(Calendar.MINUTE, -1);
            exitCal.set(Calendar.MILLISECOND, 0);
        }
        return exitCal.getTime();
    }

    /**
     * Returns the first day of the next week after specified TIME.Date is not
     * adjusted for holidays.
     *
     * @param time
     * @param hour
     * @param minute
     * @param timeZone
     * @return
     */
    public static long beginningOfWeek(long time, int hour, int minute, String timeZone, int jumpAhead) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(time);
        cal.add(Calendar.WEEK_OF_YEAR, jumpAhead);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();

    }

    /**
     * Returns the first day of the next month,using specified hour and
     * minute.Dates are not adjusted for holidays.
     *
     * @param time
     * @param hour
     * @param minute
     * @param timeZone
     * @return
     */
    public static long beginningOfMonth(long time, int hour, int minute, String timeZone, int jumpAhead) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(time);
        cal.add(Calendar.MONTH, jumpAhead);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();

    }

    /**
     * Returns the next year.Returned value is not adjusted for holidays.
     *
     * @param time
     * @param hour
     * @param minute
     * @param timeZone
     * @return
     */
    public static long beginningOfYear(long time, int hour, int minute, String timeZone, int jumpAhead) {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(time);
        cal.add(Calendar.YEAR, jumpAhead);
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();

    }

    /**
     * Rounds to the specified step as
     * http://bytes.com/topic/visual-basic-net/answers/553549-how-round-number-custom-step-0-25-20-100-a
     *
     * @param input
     * @param step
     * @return
     */
    public static double roundTo(double input, double step) {
        if (step == 0) {
            return input;
        } else {
            double floor = ((long) (input / step)) * step;
            double round = floor;
            double remainder = input - floor;
            if (remainder >= step / 2) {
                round += step;
            }
            return round(round, 2);
        }
    }

    public static String roundToDecimal(String input) {
        if (!input.equals("")) {
            Float inputvalue = Float.parseFloat(input);
            DecimalFormat df = new DecimalFormat("0.00");
            df.setMaximumFractionDigits(2);
            return df.format(inputvalue);
        } else {
            return input;
        }
    }

    /**
     * Returns a native array of specified 'size', filled with values starting
     * from 'value', incremented by 'increment'.
     *
     * @param value
     * @param increment
     * @param size
     * @return
     */
    public static double[] range(double startValue, double increment, int size) {
        double[] out = new double[size];
        if (size > 0) {
            out[0] = startValue;
            for (int i = 1; i < size; i = i + 1) {
                out[i] = out[i - 1] + increment;
            }
        }
        return out;
    }

    /**
     * Returns a native int[] of specified 'size' filled with values starting
     * from 'value', incremented by 'increment'.
     *
     * @param startValue
     * @param increment
     * @param size
     * @return
     */
    public static int[] range(int startValue, int increment, int size) {
        int[] out = new int[size];
        if (size > 0) {
            out[0] = startValue;
            for (int i = 1; i < size; i = i + 1) {
                out[i] = out[i - 1] + increment;
            }
        }
        return out;
    }

    /**
     * Returns a list of indices matching a true condition specified by value.
     *
     * @param <T>
     * @param a
     * @param value
     * @return
     */
    public static <T> ArrayList<Integer> findIndices(ArrayList<T> a, T value) {
        ArrayList<Integer> out = new ArrayList<Integer>();
        int index = a.indexOf(value);
        while (index >= 0) {
            out.add(index);
            index = a.subList(index, a.size()).indexOf(value);
        }
        return out;
    }

    /**
     * Returns an arraylist containing the elements specified in INDICES array.
     *
     * @param <E>
     * @param indices
     * @param target
     * @return
     */
    public static <E> List<E> subList(int[] indices, List<E> target) {
        List<E> out = new ArrayList<>();
        for (int i = 0; i < indices.length; i++) {
            out.add(target.get(indices[i]));
        }

        return out;
    }

    /**
     * Returns a copy of the target list, with indices removed.
     *
     * @param <E>
     * @param target
     * @param indices
     * @param adjustment
     * @return
     */
    public static <E> List<E> removeList(List<E> target, int[] indices, int adjustment) {
        //adjustment is to handle scenarios where indices are generated from packages that start indexing at 1, eg.R
        List<Integer> l_indices = Ints.asList(indices);
        Collections.sort(l_indices, Collections.reverseOrder());
        List<E> copy = new ArrayList<E>(target.size());

        for (E element : target) {
            copy.add((E) element);
        }

        for (Integer i : l_indices) {
            copy.remove(i.intValue() + adjustment);
        }
        return copy;
    }

    public static int[] addArraysNoDuplicates(int[] input1, int[] input2) {
        /*
         TreeSet t1 = new <Integer>TreeSet(Arrays.asList(input1));
         TreeSet t2 = new <Integer>TreeSet(Arrays.asList(input2));
         t1.add(t2);
         Integer[] out = new Integer[t1.size()];
         t1.toArray(out);
         int[] out2 = new int[t1.size()];
         for (int i = 0; i < out.length; i++) {
         out2[i] = out[i];
         }
         //    String[] countries1 = t1.toArray(new String[t1.size()]);
         */
        int[] arraycopy = com.google.common.primitives.Ints.concat(input1, input2);
        List<Integer> copy = com.google.common.primitives.Ints.asList(arraycopy);
        TreeSet<Integer> t = new TreeSet<>(copy);
        return com.google.common.primitives.Ints.toArray(t);


    }

    /**
     * Returns the sum of an arraylist for specified indices.
     *
     * @param list
     * @param startIndex
     * @param endIndex
     * @return
     */
    public static double sumArrayList(ArrayList<Double> list, int startIndex, int endIndex) {
        if (list == null || list.size() < 1) {
            return 0;
        }

        double sum = 0;
        for (int i = startIndex; i <= endIndex; i++) {
            sum = sum + list.get(i);
        }
        return sum;
    }

    /**
     * Returns an ArrayList of time values in millisecond, corresponding to the
     * input start and end time.Holidays are adjusted (if provided).Timezone is
     * a mandatory input
     *
     * @param start
     * @param end
     * @param size
     * @param holidays
     * @param openHour
     * @param openMinute
     * @param closeHour
     * @param closeMinute
     * @param zone
     * @return
     */
    public static ArrayList<Long> getTimeArray(long start, long end, EnumBarSize size, List<String> holidays, boolean weekendHolidays, int openHour, int openMinute, int closeHour, int closeMinute, String zone) {
        ArrayList<Long> out = new ArrayList<>();
        try {
            Preconditions.checkArgument(start <= end, "Start=%s,End=%s", start, end);
            Preconditions.checkArgument(openHour < closeHour);
            Preconditions.checkNotNull(size);
            Preconditions.checkNotNull(zone);

            TimeZone timeZone = TimeZone.getTimeZone(zone);
            Calendar iStartTime = Calendar.getInstance(timeZone);
            //start=1436332571000L;
            iStartTime.setTimeInMillis(start);
            Calendar iEndTime = Calendar.getInstance(timeZone);
            iEndTime.setTimeInMillis(end);
            /*
             * VALIDATE THAT start AND end ARE CORRECT TIME VALUES. SPECIFICALLY, SET start TO openHour and end to closeHour, if needed
             */
            int iHour = iStartTime.get(Calendar.HOUR_OF_DAY);
            int iMinute = iStartTime.get(Calendar.MINUTE);
            if ((iHour * 60 + iMinute) < (openHour * 60 + openMinute)) {
                iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                iStartTime.set(Calendar.MINUTE, openMinute);
            } else if ((iHour * 60 + iMinute) >= (closeHour * 60 + closeMinute)) {
                iStartTime.setTime(nextGoodDay(iStartTime.getTime(), 0, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
            }

            iHour = iEndTime.get(Calendar.HOUR_OF_DAY);
            iMinute = iEndTime.get(Calendar.MINUTE);
            if ((iHour * 60 + iMinute) >= (closeHour * 60 + closeMinute)) {
                iEndTime.set(Calendar.HOUR_OF_DAY, closeHour);
                iEndTime.set(Calendar.MINUTE, closeMinute);
                iEndTime.add(Calendar.SECOND, -1);
            } else if ((iHour * 60 + iMinute) < (openHour * 60 + openMinute)) {
                iEndTime.setTime(previousGoodDay(iEndTime.getTime(), 0, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
            }

            switch (size) {
                case ONESECOND:
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.add(Calendar.SECOND, 1);
                        if (iStartTime.get(Calendar.HOUR_OF_DAY) * 60 + iStartTime.get(Calendar.MINUTE) >= (closeHour * 60 + closeMinute)) {
                            iStartTime.setTime(nextGoodDay(iStartTime.getTime(), 1, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
                        }
                    }
                    break;
                case ONEMINUTE:
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.add(Calendar.MINUTE, 1);
                        if (iStartTime.get(Calendar.HOUR_OF_DAY) * 60 + iStartTime.get(Calendar.MINUTE) >= (closeHour * 60 + closeMinute)) {
                            iStartTime.setTime(nextGoodDay(iStartTime.getTime(), 1, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
                        }
                    }
                    break;
                case DAILY:
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.add(Calendar.DATE, 1);
                        iStartTime.setTime(nextGoodDay(iStartTime.getTime(), 0, zone, openHour, openMinute, closeHour, closeMinute, holidays, weekendHolidays));
                    }
                    break;
                case WEEKLY:
                    iStartTime = Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    // System.out.println(iStartTime.getTimeInMillis()+","+iEndTime.getTimeInMillis());
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(Utilities.beginningOfWeek(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
                    }
                    break;
                case MONTHLY:
                    iStartTime = Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_MONTH, 1);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(Utilities.beginningOfMonth(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
                    }
                    break;
                case ANNUAL:
                    iStartTime = Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_YEAR, 1);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(Utilities.beginningOfMonth(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
                    }
                    break;
                default:
                    break;
            }

        } catch (NullPointerException | IllegalArgumentException e) {
        } finally {
            return out;
        }
    }

    public static boolean isDouble(String value) {
        //String decimalPattern = "([0-9]*)\\.([0-9]*)";  
        //return Pattern.matches(decimalPattern, value)||Pattern.matches("\\d*", value);
        if (value != null) {
            value = value.trim();
            return value.matches("-?\\d+(\\.\\d+)?");
        } else {
            return false;
        }
    }

    public static boolean isInteger(String str) {
        if (str == null) {
            return false;
        }
        str = str.trim();
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if (c <= '/' || c >= ':') {
                return false;
            }
        }
        return true;
    }

    public static boolean isLong(String str) {
        if (str == null) {
            return false;
        }
        str = str.trim();
        int length = str.length();
        if (length == 0) {
            return false;
        }
        int i = 0;
        if (str.charAt(0) == '-') {
            if (length == 1) {
                return false;
            }
            i = 1;
        }
        for (; i < length; i++) {
            char c = str.charAt(i);
            if (c <= '/' || c >= ':') {
                return false;
            }
        }
        return true;
    }

    public static boolean isDate(String dateString, SimpleDateFormat sdf) {

        sdf.setLenient(false);
        try {
            sdf.parse(dateString.trim());
        } catch (ParseException pe) {
            return false;
        }
        return true;
    }

    public static double getDouble(Object input, double defvalue) {
        try {
            if (isDouble(input.toString())) {
                return Double.parseDouble(input.toString().trim());
            } else {
                return defvalue;
            }
        } catch (Exception e) {
            return defvalue;
        }
    }

    public static int getInt(Object input, int defvalue) {
        try {
            if (isInteger(input.toString())) {
                return Integer.parseInt(input.toString().trim());
            } else {
                return defvalue;
            }
        } catch (Exception e) {
            return defvalue;
        }
    }

    public static long getLong(Object input, long defvalue) {
        try {
            if (isLong(input.toString())) {
                return Long.parseLong(input.toString().trim());
            } else {
                return defvalue;
            }
        } catch (Exception e) {
            return defvalue;
        }
    }

    public static double[] convertStringListToDouble(String[] input) {
        double[] out = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = getDouble(input[i], 0);
        }
        return out;
    }

    public static int[] convertStringListToInt(String[] input) {
        int[] out = new int[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = getInt(input[i], 0);
        }
        return out;
    }

    /**
     * Utility function for loading a parameter file.
     *
     * @param parameterFile
     * @return
     */
    public static Properties loadParameters(String parameterFile) {
        Properties p = new Properties();
        FileInputStream propFile;
        File f = new File(parameterFile);
        if (f.exists()) {
            try {
                propFile = new FileInputStream(parameterFile);
                p.load(propFile);

            } catch (Exception ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }

        return p;
    }

    public static HashMap<String, String> loadParameters(String parameterFile, boolean side) {
        HashMap<String, String> out = new HashMap<>();
        Properties properties = loadParameters(parameterFile);
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            out.put(key, value);
        }
        return out;
    }

    /**
     * Rounds a double value to a range. So, if range=0.05, values are rounded
     * to multiples of 0.05
     *
     * @param value
     * @param range
     * @return
     */
    public static double round(double value, double range) {
        int factor = (int) Math.round(value / range); // 10530 - will round to correct value
        double result = factor * range; // 421.20
        return result;
    }

    /**
     * Rounds a number to specified decimals.
     *
     * @param value
     * @param places
     * @return
     */
    public static double round(double value, int places) {
        if (places < 0) {
            throw new IllegalArgumentException();
        }
        if (!Double.isInfinite(value) && !Double.isNaN(value)) {
            BigDecimal bd = new BigDecimal(value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
            return bd.doubleValue();
        }
        return value;
    }

    public static double round(double value, double range, int places) {
        double out = round(value, range);
        out = round(out, places);
        return out;
    }

    /**
     * Converts a List<Doubles> to double[]
     *
     * @param doubles
     * @return
     */
    public static double[] convertDoubleListToArray(List<Double> doubles) {
        double[] ret = new double[doubles.size()];
        Iterator<Double> iterator = doubles.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            ret[i] = iterator.next().doubleValue();
            i++;
        }
        return ret;
    }

    /**
     * Converts a List<Integers> to int[]
     *
     * @param integers
     * @return
     */
    public static int[] convertIntegerListToArray(List<Integer> integers) {
        int[] ret = new int[integers.size()];
        Iterator<Integer> iterator = integers.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            ret[i] = iterator.next().intValue();
            i++;
        }
        return ret;
    }

    public static String[] convertLongListToArray(List<Long> integers) {
        String[] ret = new String[integers.size()];
        Iterator<Long> iterator = integers.iterator();
        int i = 0;
        while (iterator.hasNext()) {
            ret[i] = iterator.next().toString();
            i++;
        }
        return ret;
    }

    public static boolean timeStampsWithinDay(long ts1, long ts2, String timeZone) {
        Calendar cl1 = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        Calendar cl2 = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cl1.setTimeInMillis(ts1);
        cl1.setTimeInMillis(ts2);
        if (cl1.get(Calendar.DATE) == cl2.get(Calendar.DATE)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns a symbolid given parameters for the symbol
     *
     * @param symbol
     * @param type
     * @param expiry
     * @param right
     * @param option
     * @return
     */
    public static int getIDFromBrokerSymbol(List<BeanSymbol> symbols, String symbol, String type, String expiry, String right, String option) {
        for (BeanSymbol symb : symbols) {
            String s = symb.getBrokerSymbol() == null ? "" : symb.getBrokerSymbol();
            String t = symb.getType() == null ? "" : symb.getType();
            String e = symb.getExpiry() == null ? "" : symb.getExpiry();
            String r = symb.getRight() == null ? "" : symb.getRight();
            String o = symb.getOption() == null ? "" : symb.getOption();

            String si = symbol == null ? "" : symbol;
            String ti = type == null ? "" : type;
            String ei = expiry == null ? "" : expiry;
            String ri = right == null ? "" : right;
            String oi = option == null ? "" : option;
            if (s.compareTo(si) == 0 && t.compareTo(ti) == 0 && e.compareTo(ei) == 0
                    && r.compareTo(ri) == 0 && o.compareTo(oi) == 0) {
                return symb.getSerialno() - 1;
            }
        }
        return -1;
    }

    public static int getIDFromExchangeSymbol(List<BeanSymbol> symbols, String symbol, String type, String expiry, String right, String option) {
        for (BeanSymbol symb : symbols) {
            String s = symb.getExchangeSymbol() == null ? "" : symb.getExchangeSymbol();
            String t = symb.getType() == null ? "" : symb.getType();
            String e = symb.getExpiry() == null ? "" : symb.getExpiry();
            String r = symb.getRight() == null ? "" : symb.getRight();
            String o = symb.getOption() == null ? "" : symb.getOption();

            String si = symbol == null ? "" : symbol;
            String ti = type == null ? "" : type;
            String ei = expiry == null ? "" : expiry;
            String ri = right == null ? "" : right;
            String oi = option == null ? "" : option;
            if (s.compareTo(si) == 0 && t.compareTo(ti) == 0 && e.compareTo(ei) == 0
                    && r.compareTo(ri) == 0 && o.compareTo(oi) == 0) {
                return symb.getSerialno() - 1;
            }
        }
        return -1;
    }
    
    /**
     * Returns symbol id from a String[] containing values as
     * symbol,type,expiry,right,optionstrike.Order is important.
     *
     * @param symbol
     * @return
     */
    public static int getIDFromBrokerSymbol(List<BeanSymbol> symbols, String[] symbol) {

        String si = symbol[0] == null || symbol[0].equalsIgnoreCase("null") ? "" : symbol[0];
        String ti = symbol[1] == null || symbol[1].equalsIgnoreCase("null") ? "" : symbol[1];
        String ei = symbol[2] == null || symbol[2].equalsIgnoreCase("null") ? "" : symbol[2];
        String ri = symbol[3] == null || symbol[3].equalsIgnoreCase("null") ? "" : symbol[3];
        String oi = symbol[4] == null || symbol[4].equalsIgnoreCase("null") ? "" : symbol[4];

        for (BeanSymbol symb : symbols) {
            String s = symb.getBrokerSymbol() == null ? "" : symb.getDisplayname().replace("&", "");
            String t = symb.getType() == null ? "" : symb.getType();
            String e = symb.getExpiry() == null ? "" : symb.getExpiry();
            String r = symb.getRight() == null ? "" : symb.getRight();
            String o = symb.getOption() == null ? "" : symb.getOption();
            if (s.compareToIgnoreCase(si) == 0 && t.compareToIgnoreCase(ti) == 0 && e.compareToIgnoreCase(ei) == 0
                    && r.compareToIgnoreCase(ri) == 0 && o.compareToIgnoreCase(oi) == 0) {
                return symb.getSerialno() - 1;
            }
        }
        return -1;
    }

    /**
     * Returns id from display name.It assumes displayName is unique in the
     * symbol list
     *
     * @param displayName
     * @return
     */
    public static int getIDFromDisplayName(List<BeanSymbol> symbols, String displayName) {
        if (displayName != null) {
            synchronized(symbols){
            for (BeanSymbol symb : symbols) {
                if (symb.getDisplayname().equals(displayName) || symb.getDisplayname().replaceAll("[^A-Za-z0-9\\-\\_]","").equals(displayName)) {
                    return symb.getSerialno() - 1;
                }
            }
            }
        }
        return -1;
    }

    /**
     * Returns id from using a substring of displayname.It returns the first
     * match
     *
     * @param displayName
     * @return
     */
    public static int getIDFromDisplaySubString(List<BeanSymbol> symbols, String subStringDisplay, String type) {
        if (subStringDisplay != null) {
            for (BeanSymbol symb : symbols) {
                if (symb.getDisplayname().toLowerCase().contains(subStringDisplay.toLowerCase()) && symb.getType().equalsIgnoreCase(type)) {
                    return symb.getSerialno() - 1;
                }
            }
        }
        return -1;
    }

    public String incrementString(String value, double increment) {
        double doubleValue = Utilities.getDouble(value, -1);
        doubleValue = doubleValue + increment;
        return String.format("%.1f", doubleValue);

    }

    public static int getReferenceID(List<BeanSymbol> symbols, int id, String referenceType) {
        String symbol = symbols.get(id).getBrokerSymbol();
        String type = referenceType;
        return getIDFromBrokerSymbol(symbols, symbol, type, "", "", "");
    }

    public static int getNextExpiryID(List<BeanSymbol> symbols, int id, String expiry) {
        String symbol = symbols.get(id).getBrokerSymbol();
        String type = symbols.get(id).getType();
        String option = symbols.get(id).getOption();
        String right = symbols.get(id).getRight();
        return getIDFromBrokerSymbol(symbols, symbol, type, expiry, right, option);
    }

    public static int getFutureIDFromBrokerSymbol(List<BeanSymbol> symbols, int id, String expiry) {
        String s = Parameters.symbol.get(id).getBrokerSymbol();
        String t = "FUT";
        String e = expiry;
        String r = "";
        String o = "";
        return getIDFromBrokerSymbol(symbols, s, t, e, r, o);
    }

    public static int getFutureIDFromExchangeSymbol(List<BeanSymbol> symbols, int id, String expiry) {
        String s = Parameters.symbol.get(id).getExchangeSymbol();
        String t = "FUT";
        String e = expiry;
        String r = "";
        String o = "";
        return getIDFromExchangeSymbol(symbols, s, t, e, r, o);
    }

    public static int getIDFromFuture(List<BeanSymbol> symbols, int futureID) {
        String s = Parameters.symbol.get(futureID).getBrokerSymbol();
        String t = "STK";
        String e = "";
        String r = "";
        String o = "";
        return getIDFromBrokerSymbol(symbols, s, t, e, r, o);
    }

    /**
     * Returns an optionid for a system that is longonly for options
     *
     * @param symbols
     * @param positions
     * @param underlyingid is the id of the underlying stock or future for which
     * we need an option
     * @param side
     * @param expiry
     * @return
     */
    public static ArrayList<Integer> getOrInsertOptionIDForPaySystem(List<BeanSymbol> symbols, ConcurrentHashMap<Integer, BeanPosition> positions, int symbolid, EnumOrderSide side, String expiry) {
        int id = -1;
        ArrayList<Integer> out = new ArrayList<>();
        String displayname = symbols.get(symbolid).getDisplayname();
        String underlying = displayname.split("_")[0];
        double strikeDistance = 0;
        switch (side) {
            case BUY:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    symbolid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                }
                if (id > 0) {
                    out.add(id);
                }
                break;
            case SELL:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("CALL")) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            case SHORT:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    int futureid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(futureid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case COVER:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("PUT")) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }

    public static ArrayList<Integer> getOrInsertOptionIDForReceiveSystem(List<BeanSymbol> symbols, ConcurrentHashMap<Integer, BeanPosition> positions, int symbolid, EnumOrderSide side, String expiry) {
        int id = -1;
        ArrayList<Integer> out = new ArrayList<>();
        String displayname = symbols.get(symbolid).getDisplayname();
        String underlying = displayname.split("_")[0];
        double strikeDistance = 0;
        switch (side) {
            case BUY:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    symbolid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                }
                if (id > 0) {
                    out.add(id);
                }
                break;
            case SELL:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("PUT")) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            case SHORT:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    int futureid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(futureid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case COVER:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("CALL")) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }

    
      public static ArrayList<Integer> getOrInsertATMOptionIDForShortSystem(List<BeanSymbol> symbols, ConcurrentHashMap<Integer, BeanPosition> positions, int symbolid, EnumOrderSide side, String expiry) {
        int id = -1;
        ArrayList<Integer> out = new ArrayList<>();
        String displayname = symbols.get(symbolid).getDisplayname();
        String underlying = displayname.split("_")[0];
        double strikeDistance = 0;
        switch (side) {
            case BUY:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    symbolid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "PUT");
                }
                if (id > 0) {
                    out.add(id);
                }
                break;
            case SELL:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("PUT")) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            case SHORT:
                if (!Parameters.symbol.get(symbolid).getType().equals("FUT")) {
                    int futureid = Utilities.getFutureIDFromBrokerSymbol(symbols, symbolid, expiry);
                    strikeDistance = Parameters.symbol.get(futureid).getStrikeDistance();
                } else {
                    strikeDistance = Parameters.symbol.get(symbolid).getStrikeDistance();
                }
                id = Utilities.getATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                if (id == -1) {
                    id = Utilities.insertATMStrike(symbols, symbolid, strikeDistance, expiry, "CALL");
                }
                if (id >= 0) {
                    out.add(id);
                }
                break;
            case COVER:
                for (BeanPosition p : positions.values()) {
                    if (p.getPosition() != 0) {
                        int tradeid = p.getSymbolid();
                        String tradedisplayname = Parameters.symbol.get(tradeid).getDisplayname();
                        if (tradedisplayname.contains(underlying) && tradedisplayname.contains("CALL")) {
                            id = tradeid;
                            out.add(id);
                        }
                    }
                }
                break;
            default:
                break;
        }
        return out;
    }
   
    public static int getATMStrike(List<BeanSymbol> symbols, int id, double increment, String expiry, String right) {
        double price = Parameters.symbol.get(id).getLastPrice();
        price = Utilities.roundTo(price, increment);
        String strikePrice = Utilities.formatDouble(price, new DecimalFormat("#.##"));
        String underlying = symbols.get(id).getDisplayname().split("_")[0];
        for (BeanSymbol s : Parameters.symbol) {
            if (s.getDisplayname().contains(underlying) && s.getType().equals("OPT") && s.getRight().equals(right) && s.getOption().equals(strikePrice)) {
                return s.getSerialno() - 1;
            }
        }
        return -1;
    }
    
    public static int insertATMStrike(List<BeanSymbol> symbols, int id, double increment, String expiry, String right) {
        double price = Parameters.symbol.get(id).getLastPrice();
        if (price == 0) {
            price = Parameters.symbol.get(id).getClosePrice();
        }
        if (price == 0) {
            Object[] pricedetails = Utilities.getSettlePrice(Parameters.symbol.get(id), new Date());
            if (pricedetails.length == 2) {
                price = Utilities.getDouble(pricedetails[1], 0);
            }
        }
        if (price > 0) {
            price = Utilities.roundTo(price, increment);
            String strikePrice = Utilities.formatDouble(price, new DecimalFormat("#.##"));
            BeanSymbol ul = symbols.get(id);
            BeanSymbol s = new BeanSymbol(ul.getBrokerSymbol(), ul.getExchangeSymbol(), "OPT", expiry, right, strikePrice);
            s.setCurrency("INR");
            s.setExchange("NSE");
            s.setPrimaryexchange("NSE");
            s.setStreamingpriority(1);
            s.setStrategy("");
            s.setDisplayname(ul.getExchangeSymbol() + "_" + "OPT" + "_" + expiry + "_" + right + "_" + strikePrice);
            s.setSerialno(Parameters.symbol.size() + 1);
            s.setAddedToSymbols(true);
            synchronized (symbols){ 
            symbols.add(s);
            }
            return s.getSerialno() - 1;
        } else {
            return -1; //no strike inserted
        }
    }

    public static int insertStrike(List<BeanSymbol> symbols, int underlyingid, String expiry, String right, String strike) {
        String exchangeSymbol = symbols.get(underlyingid).getExchangeSymbol();
        int id = Utilities.getIDFromExchangeSymbol(symbols, exchangeSymbol, "OPT", expiry, right, strike);
        if (id >= 0) {
            return id;
        } else {
            int futureid = Utilities.getFutureIDFromBrokerSymbol(symbols, underlyingid, expiry);
            if (futureid >= 0) {
                String brokerSymbol = exchangeSymbol.replaceAll("[^A-Za-z0-9\\-]", "");
                if(brokerSymbol.equals("NSENIFTY")){
                    brokerSymbol="NIFTY50";
                }
                BeanSymbol s = new BeanSymbol(brokerSymbol, exchangeSymbol, "OPT", expiry, right, strike);
                s.setCurrency("INR");
                s.setExchange("NSE");
                s.setPrimaryexchange("NSE");
                s.setStreamingpriority(1);
                s.setStrategy("");
                s.setUnderlyingID(futureid);
                s.setDisplayname(exchangeSymbol + "_OPT_" + expiry + "_" + right + "_" + strike);
                s.setSerialno(Parameters.symbol.size() + 1);
                s.setAddedToSymbols(true);
                synchronized (symbols){ 
                symbols.add(s);
                }
                return s.getSerialno() - 1;
            }
        }
        return -1;
    }

    
    public static int getNetPosition(List<BeanSymbol> symbols, ConcurrentHashMap<Integer, BeanPosition> position, int id, String type) {
        int out = 0;
        ArrayList<Integer> tempSymbols = new ArrayList<>();
        BeanSymbol ref = symbols.get(id);
        for (BeanSymbol s : symbols) {
            if (s.getBrokerSymbol().equals(ref.getBrokerSymbol()) && s.getType().equals(type)) {
                tempSymbols.add(s.getSerialno() - 1);
            }
        }

        for (Integer p : position.keySet()) {
            if (tempSymbols.contains(p)) {
                out = out + position.get(p).getPosition();
            }
        }
        return out;
    }

    public static String formatDouble(double d, DecimalFormat df) {
        return df.format(d);
    }

    /**
     * Write split information to file
     *
     * @param si
     */
    public static void writeSplits(SplitInformation si) {
        try {
            File dir = new File("logs");
            File file = new File(dir, "suggestedsplits.csv");
            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWritter = new FileWriter(file, true);
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(dateFormatter.format(new Date(Long.parseLong(si.expectedDate))) + "," + si.symbol + "," + si.oldShares + "," + si.newShares + newline);
            bufferWritter.close();
        } catch (IOException ex) {
        }
    }

    public static String getNextFileName(String directory, String fileName) {
        int increase = 0;
        String name = fileName + "." + increase;
        if (Utilities.fileExists(directory, name)) {
            increase++;
        }
        return name;
    }

    /**
     * Writes content in String[] to a file.The first column in the file has the
     * timestamp,used to format content[0] to correct time.The first two columns
     * in the FILENAME will be written with date and time respectively.
     *
     * @param filename
     * @param content
     * @param timeZone
     */
    public static void writeToFile(String filename, String[] content, String timeZone, boolean appendAtEnd) {
        try {
            File dir = new File("logs");
            File file = new File(dir, filename);

            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }

            String dateString = "";
            String timeString = "";
            if (content[0] != null && !content[0].equals("")) {
                SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
                SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");
                dateFormatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                timeFormatter.setTimeZone(TimeZone.getTimeZone(timeZone));
                dateString = dateFormatter.format(new java.util.Date(Long.parseLong(content[0])));
                timeString = timeFormatter.format(new java.util.Date(Long.parseLong(content[0])));
            }
            if (!appendAtEnd) {
                if (!file.exists()) {
                    file.createNewFile();
                }
                File newfile = new File(dir, filename + ".old");
                file.renameTo(newfile);
                file = new File(dir, filename);
                if (!file.exists()) {
                    file.createNewFile();
                }
            }

            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            String result = "";
            for (int i = 1; i < content.length; i++) {
                if (i > 1) {
                    result = result + ",";
                }
                result = result + content[i];
            }
            bufferWritter.write(dateString + "," + timeString + "," + result + newline);
            bufferWritter.close();
            if (!appendAtEnd) {
                File newfile = new File(dir, filename + ".old");
                copyFileUsingFileStreams(newfile, file);
                newfile.delete();
            }
        } catch (IOException ex) {
        }
    }

    private static void copyFileUsingFileStreams(File source, File dest) throws IOException {
        InputStream input = null;
        OutputStream output = null;
        try {
            input = new FileInputStream(source);
            output = new FileOutputStream(dest);
            byte[] buf = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buf)) > 0) {
                output.write(buf, 0, bytesRead);
            }
        } finally {
            input.close();
            output.close();
        }
    }

    public static <T> T convertInstanceOfObject(Object o, Class<T> clazz) {
        try {
            return clazz.cast(o);
        } catch (ClassCastException e) {
            return null;
        }
    }
    /*
     * Serialize an object to json
     */

    public static void writeJson(String fileName, Object o) {
        Class clazz = o.getClass();
        clazz.cast(o);
        String out = JsonWriter.objectToJson(clazz.cast(o));
        Utilities.writeToFile(new File(fileName), out);

    }

    /**
     * Writes to filename, the values in String[].
     *
     * @param filename
     * @param content
     */
    public static void writeToFile(String relativePath, String filename, String content) {
        try {
            File dir = new File(relativePath);
            File file = new File(dir, filename);
            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            //true = append file
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(content + newline);
            bufferWritter.close();
        } catch (IOException e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    public static void writeToFile(File file, String content) {
        try {
            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }
            //true = append file
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(content + newline);
            bufferWritter.close();
        } catch (IOException ex) {
        }
    }

    public static void deleteFile(String filename) {
        File file = new File(filename);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void deleteFile(String directory, String filename) {
        File dir = new File(directory);
        File file = new File(dir, filename);
        if (file.exists()) {
            file.delete();
        }
    }

    public static void deleteFile(File file) {
        if (file.exists()) {
            file.delete();
        }
    }

    public static boolean fileExists(String directory, String filename) {
        File dir = new File(directory);
        File file = new File(dir, filename);
        if (file.exists() && !file.isDirectory()) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Returns the next expiration date, given today's date.It assumes that the
     * program is run EOD, so the next expiration date is calculated after the
     * completion of today.
     *
     * @param currentDay
     * @return
     */
    public static String getNextExpiry(String currentDay) {
        String out = null;
        try {
            SimpleDateFormat sdf_yyyMMdd = new SimpleDateFormat("yyyyMMdd");
            Date today = sdf_yyyMMdd.parse(currentDay);
            Calendar cal_today = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            cal_today.setTime(today);
            int year = Utilities.getInt(currentDay.substring(0, 4), 0);
            int month = Utilities.getInt(currentDay.substring(4, 6), 0) - 1;//calendar month starts at 0
            Date expiry = getLastThursday(month, year);
            expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, Algorithm.holidays, true);
            Calendar cal_expiry = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
            cal_expiry.setTime(expiry);
            if (cal_expiry.get(Calendar.DAY_OF_MONTH) > cal_today.get(Calendar.DAY_OF_MONTH)) {
                out = sdf_yyyMMdd.format(expiry);
                return out;
            } else {
                if (cal_today.get(Calendar.MONTH) == 11) {//we are in decemeber
                    expiry = getLastThursday(month, year + 1);
                    expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                    out = sdf_yyyMMdd.format(expiry);
                    return out;
                } else {
                    expiry = getLastThursday(month + 1, year);
                    expiry = Utilities.nextGoodDay(expiry, 0, Algorithm.timeZone, Algorithm.openHour, Algorithm.openMinute, Algorithm.closeHour, Algorithm.closeMinute, null, true);
                    out = sdf_yyyMMdd.format(expiry);
                    return out;
                }
            }
        } catch (Exception ex) {
            logger.log(Level.SEVERE, null, ex);
        } finally {
            return out;
        }
    }

    public static Date getLastThursday(int month, int year) {
        //http://stackoverflow.com/questions/76223/get-last-friday-of-month-in-java
        Calendar cal = Calendar.getInstance();
        cal.set(year, month, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.set(GregorianCalendar.DAY_OF_WEEK, Calendar.THURSDAY);
        cal.set(GregorianCalendar.DAY_OF_WEEK_IN_MONTH, -1);
        return cal.getTime();
    }
}
