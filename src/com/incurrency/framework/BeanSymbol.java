/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.incurrency.framework.fundamental.Fundamental;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import org.jblas.DoubleMatrix;
import static com.incurrency.framework.Algorithm.*;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author admin
 */
public class BeanSymbol implements Serializable, ReaderWriterInterface<BeanSymbol>, PropertyChangeListener {

    private final static Logger logger = Logger.getLogger(BeanSymbol.class.getName());
    private String longName;
    private int serialno;
    private String brokerSymbol;
    private String exchangeSymbol;
    private String displayName;
    private String happyName;
    private String type;
    private String exchange;
    private String primaryexchange;
    private String currency;
    private String expiry;
    private String option;
    private String right;
    private int contractID;
    private int minsize;
    private String barsstarttime;
    private String priorDayVolume;
    private String preopen;
    private int streamingpriority = 0; //0 is highest priority
    private String strategy;
    private double lastPrice = 0;
    private double bidPrice = 0;
    private double askPrice = 0;
    private double bidVol;
    private double askVol;
    private double lastVol;
    private int lastSize;
    private double bidSize;
    private double askSize;
    private double closePrice;
    private double yesterdayLastPrice;
    private int volume;
    private long lastPriceTime;
    private int reqID;
    private DataBars OneMinuteBarFromRealTimeBars;
    private DataBars intraDayBarsFromTick;
    private DataBars dailyBar = new DataBars(this, EnumBarSize.UNDEFINED);
    private DataBars supplementalBars1;
    private DataBars supplementalBars2;
    private double openPrice;
    private double lowPrice;
    private double highPrice;
    private double tickSize;
    private Boolean status;
    private double prevLastPrice;
    private double atmStrike;
    private int dataConnectionID;
    private long firstTimeStamp;
    private int connectionidUsedForMarketData;
    private boolean comboSetupFailed = false;
    private ConcurrentHashMap<EnumBarSize, DoubleMatrix> timeSeries = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<EnumBarSize, List<Long>> columnLabels = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<EnumBarSize, List<String>> rowLabels = new ConcurrentHashMap<>();
    private ConcurrentHashMap<EnumBarSize, BeanOHLC> databars = new ConcurrentHashMap<>();
    private boolean active;
    public TreeMap<String, String[]> initData = new TreeMap<>();
    //properties
    private PropertyChangeSupport propertySupport;
    public static final String PROP_LASTPRICE = "lastPrice";
    public static final String PROP_BIDPRICE = "bidPrice";
    public static final String PROP_ASKPRICE = "askPrice";
    public static final String PROP_VOLUME = "volume";
    //locks
    private final String delimiter = "_";
    private final Object lockLastPrice = new Object();
    private final Object lockBidPrice = new Object();
    private final Object lockAskPrice = new Object();
    private final Object lockVolume = new Object();
    private final Object lockHighPrice=new Object();
    private final Object lockLowPrice=new Object();
    private final Object lockLastPriceTime = new Object();
    private final Object lockLastSize = new Object();
    private final Object lockOpenPrice = new Object();
    private final Object lockClosePrice = new Object();
    private final Object lockTradedPrices = new Object();
    private final Object lockTradedVolumes = new Object();
    private final Object lockTradedTime = new Object();
    private final Object lockPrevLastPrice = new Object();
    private static final Object lockTimeSeries = new Object();
    private int openHour = Integer.valueOf(Algorithm.globalProperties.get("openhour").toString().trim());
    private int openMinute = Integer.valueOf(Algorithm.globalProperties.get("openminute").toString().trim());
    private int closeHour = Integer.valueOf(Algorithm.globalProperties.get("closehour").toString().trim());
    private int closeMinute = Integer.valueOf(Algorithm.globalProperties.get("closeminute").toString().trim());
    private String timeZone = Algorithm.globalProperties.get("timezone").toString().trim();
    private LimitedQueue<Double> tradedPrices;
    private LimitedQueue<Integer> tradedVolumes;
    private LimitedQueue<Long> tradedDateTime;
    private HashMap<BeanSymbol, Integer> combo = new HashMap<>(); //holds brokerSymbol and corresponding size
    private Fundamental fundamental = new Fundamental();

    public BeanSymbol() {
        tradedPrices = new LimitedQueue(10);
        tradedVolumes = new LimitedQueue(10);
        tradedDateTime = new LimitedQueue(10);
    }

    public BeanSymbol(String comboString, String happyName, String strategy) {//used for creating combo orders
        //Symbol1_type_expiry_right_option_signedsize:symbol2_type_expiry_right_option_signedsize
        //set mandatory fields
        this.type = "COMBO";
        this.setSerialno(Parameters.symbol.size() + 1);
        this.setBrokerSymbol(comboString);
        this.happyName=happyName;
        this.setDisplayname(happyName);
        tradedPrices = new LimitedQueue(10);
        tradedVolumes = new LimitedQueue(10);
        tradedDateTime = new LimitedQueue(10);

        //populate combo object
        String[] symbols = comboString.split(":");
        this.setStrategy(strategy);
        if (symbols.length == 1) {
            comboSetupFailed = true;
        }
        for (int i = 0; i < symbols.length; i++) {
            String[] parameters = symbols[i].split("_");
            int id = Utilities.getIDFromExchangeSymbol(Parameters.symbol,parameters[0], parameters[1], parameters[2], parameters[3], parameters[4]);
            if (id >= 0) {
                this.combo.put(Parameters.symbol.get(id), Integer.parseInt(parameters[5]));
            } else {
                //setup brokerSymbol for removal
                comboSetupFailed = true;
            }
        }
        //add listeners to each brokerSymbol
        for (Map.Entry<BeanSymbol, Integer> entry : combo.entrySet()) {//add listeners
            entry.getKey().addPropertyChangeListener(this);
        }
    }

    /**
     * Provided to create a brokerSymbol within the program.
     *
     * @param brokerSymbol
     * @param happyName
     * @param type
     * @param exchange
     * @param currency
     * @param expiry
     * @param option
     * @param right
     * @param minsize
     */
    public BeanSymbol(String symbol, String happyName, String type, String exchange, String currency, String expiry, String option, String right, int minsize) {
        tradedPrices = new LimitedQueue(10);
        tradedVolumes = new LimitedQueue(10);
        tradedDateTime = new LimitedQueue(10);
        this.brokerSymbol = symbol;
        this.happyName = happyName;
        this.displayName=happyName;
        this.type = type;
        this.exchange = exchange;
        this.currency = currency;
        this.expiry = expiry;
        this.option = option;
        this.right = right;
        this.minsize = minsize;
    }

    public BeanSymbol(String brokerSymbol,String exchangeSymbol, String type, String expiry, String right,String option) {
        tradedPrices = new LimitedQueue(10);
        tradedVolumes = new LimitedQueue(10);
        tradedDateTime = new LimitedQueue(10);
        this.brokerSymbol = brokerSymbol;
        this.exchangeSymbol=exchangeSymbol;
        this.type = type;
        this.expiry = expiry;
        this.option = option;
        this.right = right;
       // this.displayName=brokerSymbol+"_"+type+"_"+expiry+"_"+right+"_"+option;
    }

    public BeanSymbol(String[] input) {
        try {
            tradedPrices = new LimitedQueue(10);
            tradedVolumes = new LimitedQueue(10);
            tradedDateTime = new LimitedQueue(10);
            propertySupport = new PropertyChangeSupport(this);
//          this.serialno = Integer.parseInt(input[0]);
            this.brokerSymbol = input[1].equals("") ? null : input[1];
            this.exchangeSymbol = input[2].equals("") ? null : input[2];
            this.type = input[4].equals("") ? null : input[4].trim().toUpperCase();
            this.exchange = input[5].equals("") || type.equals("COMBO") ? null : input[5].trim().toUpperCase();
            this.primaryexchange = input[6].equals("") || type.equals("COMBO") ? null : input[6].trim().toUpperCase();
            this.currency = input[7].equals("") || type.equals("COMBO") ? null : input[7].trim().toUpperCase();
            this.expiry = input[8].equals("") || type.equals("COMBO") ? null : input[8].trim().toUpperCase();
            this.option = input[9].equals("") || type.equals("COMBO") ? null : input[9].trim().toUpperCase();
            this.right = input[10].equals("") || type.equals("COMBO") ? null : input[10].trim().toUpperCase();
            this.happyName = input[3].equals("") ?null: input[3].trim().toUpperCase();
            this.displayName=happyName==null?exchangeSymbol+"_"+type+"_"+(expiry==null?"":expiry)+"_"+(right==null?"":right)+"_"+(option==null?"":option):this.happyName ;
//            displayName=displayName.replaceAll("[^_A-Za-z0-9]", "").trim().toUpperCase();
            this.minsize = input[11].equals("") ? 1 : Integer.parseInt(input[11]);;
            this.barsstarttime = input[12].equals("") ? null : input[12].trim().toUpperCase();
            this.streamingpriority = input[13].equals("") ? 1 : Integer.parseInt(input[13].trim().toUpperCase());
            if (input.length <= 14) {
                this.strategy = "";
            } else {
                this.strategy = input[14].equals("") ? "" : input[14].trim().toUpperCase();
            }
            if (input.length <= 15) {
                this.active = true;
            } else {
                this.active = input[15].equals("") ? false : Boolean.parseBoolean(input[15].trim().toUpperCase());
            }
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
            JOptionPane.showMessageDialog(null, "The symbol file has invalid data. inStrat will close.");
            System.exit(0);
        }
    }

    /**
     * synchronizes timeseries matrix with columnLabels, across all symbols for
     * all barsizes.
     *
     * @param time
     * @param symbols
     */
    private void addEmptyColumns(List<Long> time, List<BeanSymbol> symbols) {
        if (time.size() > 0) {
            for (EnumBarSize barSize : getColumnLabels().keySet()) {
                ArrayList<Long> incrementalTime = Utilities.getTimeArray(time.get(0), time.get(time.size() - 1), barSize, Algorithm.holidays, true, openHour, openMinute, closeHour, closeMinute, timeZone);
                int timeLength = incrementalTime.size();
                boolean current = timeLength > 0 && getColumnLabels().get(barSize) != null && getColumnLabels().get(barSize).size() > 0 ? incrementalTime.get(0) > getColumnLabels().get(barSize).get(getColumnLabels().get(barSize).size() - 1) : true;
                if (timeLength > 0 && getColumnLabels().get(barSize).indexOf(incrementalTime.get(0)) == -1 && getColumnLabels().get(barSize).indexOf(incrementalTime.get(timeLength - 1)) == -1
                        && current) {
                    getColumnLabels().get(barSize).addAll(incrementalTime);
                    Collections.sort(getColumnLabels().get(barSize), null);
                    for (BeanSymbol s : symbols) {
                        s.addEmptyMatrixValues(barSize, incrementalTime);
                    }
                } else if (timeLength > 0 && getColumnLabels().get(barSize).indexOf(incrementalTime.get(0)) >= 0 && getColumnLabels().get(barSize).indexOf(incrementalTime.get(timeLength - 1)) == -1) {
                    //a slice of incrementalTime needs to be added
                    int currentTimeSize = getColumnLabels().get(barSize).size();
                    long currentLastTime = getColumnLabels().get(barSize).get(currentTimeSize - 1);
                    int startIndex = incrementalTime.indexOf(Long.valueOf(currentLastTime));
                    if (startIndex >= 0) {
                        List<Long> subIncrementalTime = incrementalTime.subList(startIndex + 1, incrementalTime.size());
                        getColumnLabels().get(barSize).addAll(subIncrementalTime);
                        for (BeanSymbol s : symbols) {
                            s.addEmptyMatrixValues(barSize, subIncrementalTime);
                        }
                    }
                }

            }
        }
    }

    private void addEmptyMatrixValues(EnumBarSize barSize, List<Long> time) {
        int colCount = time.size();
        int[] range = new int[2];
        range[0] = this.getColumnLabels().get(barSize).indexOf(time.get(0));
        range[1] = this.getColumnLabels().get(barSize).indexOf(time.get(colCount - 1));
        int rowCount = this.getRowLabels().get(barSize).size();
        if (rowCount > 0) {
            double[] values = Utilities.range(ReservedValues.EMPTY, 0, colCount * rowCount);
            DoubleMatrix m = new DoubleMatrix(rowCount, colCount, values);
            if (getTimeSeries().get(barSize) == null) {
                getTimeSeries().put(barSize, m);
            } else {
                logger.log(Level.FINE, "Symbol:{0},Existing Matrix rows:{1},Rows being Added:{2}", new Object[]{this.getDisplayname(), getTimeSeries().get(barSize).rows, m.rows});
                getTimeSeries().put(barSize, MatrixMethods.insertColumn(getTimeSeries().get(barSize), values, range));
//                getTimeSeries().put(barSize, DoubleMatrix.concatHorizontally(getTimeSeries().get(barSize), m));
            }
        }
    }

    /**
     * addEmptyRows will add new LABEL rows to Timeseries.Exiting columns in the
     * timeseries matrix will be populated with ReservedValues.EMPTY.
     *
     * @param barSize
     * @param labels
     */
    private void addEmptyRows(EnumBarSize barSize, String[] labels, List<BeanSymbol> symbols) {
        //Store labels that do not exist in rowLabels.
        List<String> labelsToBeAdded = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            if (getRowLabels().get(barSize).indexOf(labels[i]) == -1) {
                labelsToBeAdded.add(labels[i]);
            }
        }
        if (labelsToBeAdded.size() > 0) {
            if (!getRowLabels().get(barSize).contains("filter")) {
                labelsToBeAdded.add("filter");
            }
            getRowLabels().get(barSize).addAll(labelsToBeAdded);
            logger.log(Level.FINE, "New Label(s) Added: {0}", new Object[]{Joiner.on(",").join(labelsToBeAdded)});
            //update timeseries for each new matrix
            //int colCount = getColumnLabels().get(barSize).size();
            int colCount = BeanSymbol.columnLabels.get(barSize).size();

            if (colCount > 0) {//fill new rows with EMPTY values, if existing timestamps exists
                for (String label : labelsToBeAdded) {
                    double[] values;
                    //changed from concatHorizontally, during swing test
                    for (BeanSymbol s : symbols) {
                        if ((s.getTimeSeries().get(barSize) != null && s.getTimeSeries().get(barSize).length == 0) || s.getTimeSeries().get(barSize) == null) {
                            int rowCount = getRowLabels().get(barSize).size();
                            values = Utilities.range(ReservedValues.EMPTY, 0, colCount * rowCount);
                            s.getTimeSeries().put(barSize, new DoubleMatrix(rowCount, colCount, values));
                        } else {
                            values = Utilities.range(ReservedValues.EMPTY, 0, colCount);
                            DoubleMatrix m = new DoubleMatrix(1, colCount, values);
                            s.getTimeSeries().put(barSize, DoubleMatrix.concatVertically(s.getTimeSeries().get(barSize), m));
                        }
                    }
                }
            }
        }
    }

    private void addNewBarSize(EnumBarSize barSize) {
        if (getRowLabels().get(barSize) == null) {
            getRowLabels().put(barSize, new ArrayList<String>());
        }
        if (getColumnLabels().get(barSize) == null) {
            getColumnLabels().put(barSize, new ArrayList<Long>());
        }
        if (getTimeSeries().get(barSize) == null) {
            //getTimeSeries().put(barSize, new DoubleMatrix(0, 0));
        }

    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.addPropertyChangeListener(listener);
    }

    private boolean addTimeSeries(EnumBarSize size, String[] labels, long inittime, double[] values) {
        try {
            assert labels.length == values.length;
        } catch (AssertionError e) {
            logger.log(Level.SEVERE, "Labels:{0}, Values:{1}", new Object[]{Arrays.toString(labels), Arrays.toString(values)});
        }
        double[] rowValues;
        long time = inittime;
        switch (size) {
            case ONEMINUTE:
            case DAILY:
                time = Utilities.nextGoodDay(new Date(inittime), 0, timeZone, openHour, openMinute, closeHour, closeMinute, Algorithm.holidays, true).getTime();
                break;
            case WEEKLY:
                time = Utilities.beginningOfWeek(inittime, openHour, openMinute, timeZone, 0);
                break;
            case MONTHLY:
                time = Utilities.beginningOfMonth(inittime, openHour, openMinute, timeZone, 0);
                break;
            case ANNUAL:
                time = Utilities.beginningOfMonth(inittime, openHour, openMinute, timeZone, 0);
                break;
            default:
                break;
        }

        try {
            int colid = getColumnLabels().get(size).indexOf(Long.valueOf(time));
            // System.out.println("Symbol: "+this.getDisplayname()+", colid:"+colid);
            if (colid >= 0) {
                for (int i = 0; i < labels.length; i++) {
                    int rowid = getRowLabels().get(size).indexOf(labels[i]);
                    //System.out.println("Symbol:"+this.getDisplayname()+", Matrix Labels:"+getTimeSeries().get(size).rows+", Matrix Column:"+getTimeSeries().get(size).columns+", labels:"+labels.length+", rowid"+rowid+", colid:"+colid+ ", i: "+i+", Values:"+values.length+", "+Arrays.toString(values));
                    int j = 0;
                    while (getTimeSeries().get(size).length < (rowid + 1) * (colid * 1) - 1) {
                        Thread.yield();
                        j++;
                        System.out.println("Waiting for matrix size to be corrected. RowID=" + rowid + ", ColumnID=" + colid + ". Waited for " + j + " seconds");
                        Thread.sleep(1000);
                    }
                    //System.out.println("Matrix Length:"+getTimeSeries().get(size).length+", Data Length:"+(rowid+1)*(colid*1));
                    synchronized (lockTimeSeries) {
                        getTimeSeries().get(size).put(rowid, colid, values[i]);
                    }
                }
                return true;
            } else {
                logger.log(Level.FINE, "Unable to insert timeseries :{0} for time: {1},Symbol: {2}, BarSize: {3}, Long time:{4} as timestamp not available in columns", new Object[]{Arrays.toString(labels), new SimpleDateFormat("yyyyMMdd HH:mm:ss").format(new Date(time)), this.getDisplayname(), size.toString(), inittime});
                return false;
            }

        } catch (Exception e) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
            int colid = getColumnLabels().get(size).indexOf(Long.valueOf(time));
            logger.log(Level.SEVERE, "BarSize:{0}, Labels:{1}, Values:{2},Time:{3},ColumnID:{4},MatrixLength:{5}", new Object[]{size.toString(), Arrays.toString(labels), Arrays.toString(values), sdf.format(new Date(time)), colid, getTimeSeries().get(size).length});
            logger.log(Level.SEVERE, null, e);
        } finally {
            return true;
        }
    }

    private void addTimeSeries(EnumBarSize size, String[] labels, List<Long> time, double[][] values) {
        int valueCount = values.length;
        for (int i = 0; i < time.size(); i++) {
            double[] value = new double[valueCount];
            for (int j = 0; j < valueCount; j++) {
                value[j] = values[j][i];
            }
            addTimeSeries(size, labels, time.get(i), value);
        }
    }

    /**
     * Clears the dynamic values for BeanSymbol.
     */
    public void clear() {

        priorDayVolume = null;
        preopen = null;
        lastPrice = 0;
        bidPrice = 0;
        askPrice = 0;
        bidVol = 0D;
        askVol = 0;
        lastVol = 0;
        lastSize = 0;
        bidSize = 0;
        askSize = 0;
        closePrice = 0;
        yesterdayLastPrice = 0;
        volume = 0;
        lastPriceTime = 0;
        //private DataBars OneMinuteBarFromRealTimeBars;
        //private DataBars intraDayBarsFromTick;
        //private DataBars dailyBar = new DataBars(this, EnumBarSize.UNDEFINED);
        openPrice = 0;
        lowPrice = 0;
        highPrice = 0;
        status = false;
        prevLastPrice = 0;
        atmStrike = 0;
        firstTimeStamp = 0;

    }

    public BeanSymbol clone(BeanSymbol orig) {
        BeanSymbol b = new BeanSymbol();
        b.longName=orig.longName;
        b.brokerSymbol = orig.brokerSymbol;
        b.exchangeSymbol=orig.exchangeSymbol;
        b.displayName = orig.displayName;
        b.type = orig.type;
        b.exchange = orig.exchange;
        b.primaryexchange = orig.primaryexchange;
        b.currency = orig.currency;
        b.expiry = orig.expiry;
        b.option = orig.option;
        b.right = orig.right;
        b.contractID = orig.contractID;
        b.serialno = orig.serialno;
        b.priorDayVolume = orig.priorDayVolume;
        b.barsstarttime = orig.barsstarttime;
        b.minsize = orig.minsize;
        b.preopen = orig.preopen;
        b.streamingpriority = orig.streamingpriority;
        b.strategy = orig.strategy;
        return b;
    }

    public void completeReader(String inputfile, ArrayList target) {
        File inputFile = new File(inputfile);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> existingSymbolsLoad = Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
                existingSymbolsLoad.remove(0);
                for (String symbolLine : existingSymbolsLoad) {
                    if (!symbolLine.equals("")) {
                        String[] input = symbolLine.split(",");
                        target.add(new BeanSymbol(input));
                    }
                }
            } catch (IOException ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }

    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof BeanSymbol)) {
            return false;
        }
        BeanSymbol that = (BeanSymbol) other;

        // Custom equality check here.
        String symbol1 = this.brokerSymbol;
        String type1 = this.type;
        String expiry1 = this.expiry == null ? "" : this.expiry;
        String option1 = this.option == null ? "" : this.option;
        String right1 = this.right == null ? "" : this.right;

        String symbol2 = that.brokerSymbol;
        String type2 = that.type;
        String expiry2 = that.expiry == null ? "" : this.expiry;
        String option2 = that.option == null ? "" : this.option;
        String right2 = that.right == null ? "" : this.right;

        return symbol1.equals(symbol2) && type1.equals(type2) && expiry1.equals(expiry2) && option1.equals(option2) && right1.equals(right2);
    }

    public int getTargetIndex(EnumBarSize targetBarSize, long time) {
        NavigableSet<Long> target = new TreeSet<>(getColumnLabels().get(targetBarSize));
        Long targetTime = target.floor(time);
        if (targetTime != null) {
            return getColumnLabels().get(targetBarSize).indexOf(targetTime);
        } else {
            return -1;
        }

    }

    public int getTargetIndex(EnumBarSize sourceBarSize, EnumBarSize targetBarSize, int sourceIndex) {
        long sourceTime = getColumnLabels().get(sourceBarSize).get(sourceIndex);
        return getTargetIndex(targetBarSize, sourceTime);

    }

    public DoubleMatrix getTimeSeries(EnumBarSize size, String timeSeriesLabel) {
        DoubleMatrix out = new DoubleMatrix(0, 0);
        if (this.getRowLabels().get(size) == null) {
            return out;
        } else {
            int row = this.getRowLabels().get(size).indexOf(timeSeriesLabel);
            if (row >= 0) {
                out = this.getTimeSeries().get(size).getRow(row);
            }
        }
        return out;
    }

    /**
     * Returns the size of a timeseries.If timeseries is not initialized,returns
     * 1.
     *
     * @param size
     * @return
     */
    public int getTimeSeriesLength(EnumBarSize barSize) {
        if (getColumnLabels().get(barSize) == null) {
            return -1;
        } else {
            return getColumnLabels().get(barSize).size();
        }
    }
    
    public int getDataLength(EnumBarSize barSize,String timeSeriesLabel){
        int out=0;
        DoubleMatrix timeSeries=this.getTimeSeries(barSize, timeSeriesLabel);
        if(timeSeries!=null && timeSeries.length>0){
            out=timeSeries.ne(ReservedValues.EMPTY).findIndices().length;
        }
        return out;
    }

    public double getTimeSeriesValue(EnumBarSize size, long time, String timeSeriesLabel) {
        double out = ReservedValues.EMPTY;
        int row = this.getRowLabels().get(size).indexOf(timeSeriesLabel);
        int column = this.getColumnLabels().get(size).indexOf(Long.valueOf(time));
        if (row >= 0 && column>=0) {
            out = this.getTimeSeries().get(size).get(row, column);
        }
        return out;
    }

    /**
     * Returns the value of the label.If boolean is set to before,the value just before the time specified 
     * in the method is returned.
     * @param size
     * @param time
     * @param timeSeriesLabel
     * @param before
     * @return 
     */
    public double getTimeSeriesValue(EnumBarSize size, long time, String timeSeriesLabel, boolean before) {
        double out = ReservedValues.EMPTY;
        int row = this.getRowLabels().get(size).indexOf(timeSeriesLabel);
        int column = this.getColumnLabels().get(size).indexOf(Long.valueOf(time));
        if (row >= 0 && column>=0) {
            DoubleMatrix m = this.getTimeSeries(size, timeSeriesLabel);
            if (before) {
                m = m.get(Utilities.range(0, 1, column)).reshape(1, column);
                int[] indices = m.ne(ReservedValues.EMPTY).findIndices();
                int l = indices.length;
                if (l > 0) {
                    out = m.get(0, indices[l - 1]);
                }
            } else {
                m = m.get(Utilities.range(column, 1, m.columns - column)).reshape(1, m.columns - column);
                int[] indices = m.ne(ReservedValues.EMPTY).findIndices();
                int l = indices.length;
                if (l > 0) {
                    out = m.get(0, indices[0]);
                }
            }
        }
        return out;
    }

    /**
     * Returns value specified at the timeIndex
     * @param size
     * @param timeIndex
     * @param timeSeriesLabel
     * @return 
     */
    public double getTimeSeriesValue(EnumBarSize size, int timeIndex, String timeSeriesLabel) {
        double out = ReservedValues.EMPTY;
        int row = this.getRowLabels().get(size).indexOf(timeSeriesLabel);
        if (row >= 0) {
            out = this.getTimeSeries().get(size).get(row, timeIndex);
        }
        return out;
    }
    
    /**
     * Returns the floor from a timeSeriesLabel, given any time.If time is not present in the corresponding
     * columnlabels, returns the prior low value.
     * @param size
     * @param time
     * @param timeSeriesLabel
     * @return 
     */
   public double getTimeSeriesValueFloor(EnumBarSize size, long time, String timeSeriesLabel) {
        double out = ReservedValues.EMPTY;
        int row = this.getRowLabels().get(size).indexOf(timeSeriesLabel);
        TreeSet<Long> tempSet=new TreeSet<>(this.getColumnLabels().get(size));
        long priorTime=tempSet.floor(time);
        if (row >= 0) {
            out = this.getTimeSeriesValue(size, priorTime, timeSeriesLabel);
        }
        return out;
    }

   /**
    * Returns the floor of the time passed to the method.
    * @param size
    * @param time
    * @param timeSeriesLabel
    * @return 
    */
    public long getTimeFloor(EnumBarSize size, long time, String timeSeriesLabel) {
        double out = ReservedValues.EMPTY;
        int row = this.getRowLabels().get(size).indexOf(timeSeriesLabel);
        TreeSet<Long> tempSet = new TreeSet<>(this.getColumnLabels().get(size));
        long priorTime = tempSet.floor(time);
        return priorTime;
    }
   
   public double getTimeSeriesValue(EnumBarSize size, int timeIndex, String timeSeriesLabel, boolean ignoreEmptyValues) {
        double out = ReservedValues.EMPTY;
        int row = this.getRowLabels().get(size).indexOf(timeSeriesLabel);
        if (row >= 0) {
            int[] indices = this.getTimeSeries().get(size).getRow(row).ne(ReservedValues.EMPTY).findIndices();
            out = this.getTimeSeries().get(size).getRow(row).get(indices).get(timeIndex);
        }
        return out;
    }

    
    public int getTimeStampIndex(EnumBarSize size, long time) {
        if (this.getColumnLabels().get(size) != null) {
            return this.getColumnLabels().get(size).indexOf(time);
        } else {
            return -1;
        }
    }

    public List<Long> getTimeStampSeries(EnumBarSize size) {
        List<Long> out = new ArrayList<>();
        List<Long> row = this.getColumnLabels().get(size);
        if (row.size() >= 0) {
            out = row;
        }
        return out;
    }

    @Override
    public int hashCode() {
        int hashCode = 0;
        String symbol1 = this.brokerSymbol;
        String type1 = this.type;
        String expiry1 = this.expiry == null ? "" : this.expiry;
        String option1 = this.option == null ? "" : this.option;
        String right1 = this.right == null ? "" : this.right;
        hashCode = hashCode * 37 + symbol1.hashCode();
        hashCode = hashCode * 37 + type1.hashCode();
        hashCode = hashCode * 37 + expiry1.hashCode();
        hashCode = hashCode * 37 + option1.hashCode();
        hashCode = hashCode * 37 + right1.hashCode();
        return hashCode;
    }

    /**
     * returns the index of a ROWLABEL.If ROWLABEL does not exist for the SIZE,
     * a new ROWLABEL is added to the SIZE,and its index is returned.Else
     * returns -1.
     *
     * @param size
     * @param rowLabels
     * @param symbols
     * @return
     */
    public int indexOfRowLabel(EnumBarSize size, String rowLabel, List<BeanSymbol> symbols) {
        int out = -1;
        if (this.getRowLabels().get(size) != null) {
            if (getRowLabels().get(size).indexOf(rowLabel) == -1) {
                addEmptyRows(size, new String[]{rowLabel}, symbols);
            }
            out = getRowLabels().get(size).indexOf(rowLabel);
        } else {
            out = -1;
        }
        return out;
    }

    /**
     * Sets the SIZE timeseries to empty values (RESERVED.EMPTYVALUES) for the
     * specified LABELS and TIME.If any of LABELS, SIZE OR TIME is null, this
     * method does nothing.
     *
     * @param labels
     * @param size
     * @param time
     */
    public List<Long> initTimeSeries(EnumBarSize size, String[] labels, List<Long> time) {
        /*
         * CONTRACT FOR TIME SHOULD OBSERVE THE FOLLOWING RULES
         * C1: THERE ARE NO GAPS IN BUSINESS TIME
         * C2: TIME SHOULD BE SORTED IN ASCENDING ORDER
         * C3: TIME CAN ONLY BE ADDED TO AT END OF LIST
         */
        Preconditions.checkArgument(size != null);
        List<Long> out = new ArrayList<>();
        if (getRowLabels().get(size) == null) {//Barsize has not been seen as yet..
            addNewBarSize(size);
        }

        long startTime = 0;
        long endTime = 0;
        int shift = 0;
        switch (size) {
            case DAILY:
                shift = 1440;
                break;
            case ONEMINUTE:
                shift = 1;
                break;
            default:
                break;
        }
        if (time != null) {
            if (getColumnLabels().get(size).indexOf(time.get(0)) == -1) {//time(0) does not exist in database as yet
                int existingTimeSize = getColumnLabels().get(size).size();
                if (existingTimeSize > 0) {//time bar has some values
                    //startTime = getColumnLabels().get(size).get(existingTimeSize - 1); //To comply with C3, mak sure that there are no gaps in time    
                    startTime = time.get(0);
                    /*
                     switch (size) {
                     case DAILY:
                     case ONEMINUTE:
                     startTime = Utilities.nextGoodDay(new Date(startTime), shift, timeZone, openHour, openMinute, closeHour, closeMinute, Algorithm.holidays, true).getTime();
                     break;
                     case WEEKLY:
                     startTime = Utilities.beginningOfWeek(startTime, openHour, openMinute, timeZone, 1);
                     break;
                     case MONTHLY:
                     startTime = Utilities.beginningOfMonth(startTime, openHour, openMinute, timeZone, 1);
                     break;
                     case ANNUAL:
                     startTime = Utilities.beginningOfYear(startTime, openHour, openMinute, timeZone, 1);
                     break;
                     default:
                     break;

                     }
                     */
                } else {
                    startTime = time.get(time.size() - 1);
                    Calendar startCal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    startCal.setTimeInMillis(startTime);
                    startCal.set(Calendar.HOUR_OF_DAY, openHour);
                    startCal.set(Calendar.MINUTE, openMinute);
                    startCal.set(Calendar.SECOND, 0);
                    startCal.set(Calendar.MILLISECOND, 0);
                    startTime = startCal.getTimeInMillis();
                }
            }
            if (getColumnLabels().get(size).indexOf(time.get(time.size() - 1)) == -1) {
                endTime = time.get(time.size() - 1);
            }
        }
        synchronized (lockTimeSeries) {
            if (Parameters.symbol.size() > 0) {
                addEmptyRows(size, labels, Parameters.symbol);
            } else {
                Parameters.symbol.add(this);
                addEmptyRows(size, labels, Parameters.symbol);
            }
            if (startTime > 0 && endTime > 0 && startTime <= endTime) {
                out = Utilities.getTimeArray(startTime, endTime, size, Algorithm.holidays, true, openHour, openMinute, closeHour, closeMinute, timeZone);
                //if the last value of incremental time is not in columnLabels, 
                int length = out.size();
                if (length > 0) {
                    if (getColumnLabels().get(size).indexOf(Long.valueOf(out.get(length - 1))) == -1) {
                        if (Parameters.symbol.size() > 0) {
                            addEmptyColumns(out, Parameters.symbol);
                        } else {
                            Parameters.symbol.add(this);
                            addEmptyColumns(out, Parameters.symbol);
                        }

                    }
                } else {
                    logger.log(Level.INFO, "Could not identify correct timestamp for insertion. Symbol:{0},Timestamp attempted:{1}", new Object[]{this.getDisplayname(), (new SimpleDateFormat("yyyyMMdd HH:mm:ss")).format(new Date(time.get(0)))});
                }
            }
        }
        return out;
        //addEmptyMatrixValues(size, incrementalTime);
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (type.equals("COMBO") && getCombo().size() > 0) {
            BeanSymbol source = (BeanSymbol) evt.getSource();
            switch (evt.getPropertyName()) {
                case PROP_LASTPRICE:
                    double tempLastPrice = 0;
                    boolean pricesAvailable = true;
                    for (Map.Entry<BeanSymbol, Integer> entry : getCombo().entrySet()) {
                        int id = entry.getKey().getSerialno() - 1;
                        if (Parameters.symbol.get(id).getLastPrice() > 0) {
                            tempLastPrice = tempLastPrice + Parameters.symbol.get(id).getLastPrice() * entry.getValue();
                        } else {
                            pricesAvailable = pricesAvailable && false;
                        }
                    }
                    if (pricesAvailable) {
                        this.lastPrice = tempLastPrice;
                        getTradedPrices().push(getLastPrice());
                        if (Parameters.symbol.size() >= serialno) {
                            MainAlgorithm.tes.fireTradeEvent(serialno - 1, com.ib.client.TickType.LAST);
                        }
                        if (MainAlgorithm.getCollectTicks()) {
                            TradingUtil.writeToFile("tick_" + this.getDisplayname()+ ".csv", "Trade," + lastPrice);
                        }
                    }

                    break;
                case PROP_BIDPRICE:
                    double tempBidPrice = 0;
                    if (getCombo().get(source) > 0) {
                        //bidprice receieved and i am buying this brokerSymbol.
                        //change in bidprice will reflect change in bidprice of the combo
                        for (Map.Entry<BeanSymbol, Integer> entry : getCombo().entrySet()) {
                            int id = entry.getKey().getSerialno() - 1;
                            if (Parameters.symbol.get(id).getBidPrice() == 0 || Parameters.symbol.get(id).getAskPrice() == 0) {
                                tempBidPrice = 0;
                                setBidPrice(0D);
                                break;
                            }
                            tempBidPrice = tempBidPrice + (entry.getValue() > 0 ? Parameters.symbol.get(id).getBidPrice() * entry.getValue() : Parameters.symbol.get(id).getAskPrice() * entry.getValue());
                        }
                        setBidPrice(tempBidPrice);
                        if (Parameters.symbol.size() >= serialno) {
                            MainAlgorithm.tes.fireBidAskChange(serialno - 1);
                        }
                        if (MainAlgorithm.getCollectTicks()) {
                            TradingUtil.writeToFile("tick_" + this.getDisplayname() + ".csv", "Bid," + bidPrice);
                        }

                    } else {
                        //bidprice receieved and i am selling this brokerSymbol.
                        //change in bidprice will not affect price of combo                            
                    }
                    break;
                case PROP_ASKPRICE:
                    double tempAskPrice = 0;
                    if (getCombo().get(source) > 0) {
                        //askprice receieved and i am buying this brokerSymbol.
                        //change in askprice will not impact my market making price
                    } else {
                        //askprice receieved and i am selling this brokerSymbol.
                        //change in askprice will not affect price of combo
                        for (Map.Entry<BeanSymbol, Integer> entry : getCombo().entrySet()) {
                            int id = entry.getKey().getSerialno() - 1;
                            if (Parameters.symbol.get(id).getBidPrice() == 0 || Parameters.symbol.get(id).getAskPrice() == 0) {
                                tempAskPrice = 0;
                                setAskPrice(0D);
                                break;
                            }
                            tempAskPrice = tempAskPrice + (entry.getValue() > 0 ? Parameters.symbol.get(id).getAskPrice() * entry.getValue() : Parameters.symbol.get(id).getBidPrice() * entry.getValue());
                        }
                        setAskPrice(tempAskPrice);
                        if (Parameters.symbol.size() >= serialno) {
                            MainAlgorithm.tes.fireBidAskChange(serialno - 1);
                        }
                        if (MainAlgorithm.getCollectTicks()) {
                            TradingUtil.writeToFile("tick_" + this.getDisplayname() + ".csv", "Ask," + askPrice);
                        }

                    }
                    break;
                default:
                    break;

            }
        }
    }

    @Override
    public void reader(String inputfile, ArrayList<BeanSymbol> target) {
        File inputFile = new File(inputfile);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> existingSymbolsLoad = Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
                existingSymbolsLoad.remove(0);
                ArrayList<BeanSymbol> tempTarget = new ArrayList<>();
                for (String symbolLine : existingSymbolsLoad) {
                    if (!symbolLine.equals("")) {
                        String[] input = symbolLine.split(",");
                        BeanSymbol s = new BeanSymbol();
                        tempTarget.add(new BeanSymbol(input));
                    }
                }
                switch (target.size()) {
                    case 0: //first load of symbolfile
                        target.addAll(tempTarget);
                        break;
                    default:
                        for (BeanSymbol s : tempTarget) {
                            if (!target.contains(s)) {
                                target.add(s);
                            }
                        }
                        for (BeanSymbol s : target) {
                            if (tempTarget.contains(s)) {
                                s.setActive(true);
                            } else {
                                s.setActive(false);
                            }
                        }

                        break;
                }
                int i = 1;
                for (BeanSymbol s : target) {
                    s.setSerialno(i);
                    i = i + 1;
                }
            } catch (IOException ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }

    }

    public void readerAll(String inputfile, ArrayList target) {
        File inputFile = new File(inputfile);
        if (inputFile.exists() && !inputFile.isDirectory()) {
            try {
                List<String> existingSymbolsLoad = Files.readAllLines(Paths.get(inputfile), StandardCharsets.UTF_8);
                existingSymbolsLoad.remove(0);
                for (String symbolLine : existingSymbolsLoad) {
                    if (!symbolLine.equals("")) {
                        String[] input = symbolLine.split(",");
                        target.add(new BeanSymbol(input));

                    }
                }
            } catch (IOException ex) {
                logger.log(Level.INFO, "101", ex);
            }
        }

    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertySupport.removePropertyChangeListener(listener);
    }

    public void saveToExternalFile(EnumBarSize barSize,String filename) {
        //String filename = this.getDisplayname().toUpperCase() + "_" + barSize.toString() + ".csv";
        Utilities.deleteFile("logs",filename);
        String[] headerarray = initData.get("0");
        String header = Utilities.concatStringArray(headerarray);
        header = "," + "," + header;
        Utilities.writeToFile("logs",filename, header);
        initData.remove("0");
        for (String[] data : initData.values()) {
            Utilities.writeToFile(filename, data, timeZone, true);
        }
        initData.clear();
    }

    /**
     * Sets timeseries for specified labels and time value.Effectively sets
     * multiple rows for a specified time.
     *
     * @param size
     * @param time
     * @param labels
     * @param values
     */
    public void setTimeSeries(EnumBarSize size, long time, String[] labels, double[] values) {
        ArrayList<Long> timeList = new ArrayList<>();
        if (size.equals(EnumBarSize.DAILY)) {
            time = Utilities.getTimeArray(time, time, size, null, false, openHour, openMinute, closeHour, closeMinute, timeZone).get(0);
        }
        timeList.add(time);
        List<Long> timeArray = new ArrayList<>();
        timeArray = initTimeSeries(size, labels, timeList);
        addTimeSeries(size, labels, time, values);

    }

    /**
     * Sets timeseries for specified LABELS with STRINGVALUES.The first value in
     * STRINGVALUES is mapped to (long)time.
     *
     * @param size
     * @param labels
     * @param stringValues
     */
    public void setTimeSeries(EnumBarSize size, String[] labels, String[] stringValues) {
        try {
            assert stringValues.length > 1;
            assert labels.length == stringValues.length - 1;
        } catch (AssertionError e) {
            logger.log(Level.SEVERE, "Labels:{0}, Values:{1}", new Object[]{Arrays.toString(labels), Arrays.toString(stringValues)});

        }
        double[] values = new double[stringValues.length - 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = Double.parseDouble(stringValues[i + 1]);
        }
        long time = Long.valueOf(stringValues[0]);
        /*
         * SUBSEQUENT CODE IS EQUAL TO setTimeSeries(EnumBarSize size, long time, String[] labels, double[] values)
         */
        ArrayList<Long> timeList = new ArrayList<>();
        if (size.equals(EnumBarSize.DAILY)) {
            time = Utilities.getTimeArray(time, time, size, null, false, openHour, openMinute, closeHour, closeMinute, timeZone).get(0);
        }
        timeList.add(time);
        List<Long> timeArray = new ArrayList<>();
        timeArray = initTimeSeries(size, labels, timeList);
        addTimeSeries(size, labels, time, values);

    }

    public void setTimeSeries(EnumBarSize size, List<Long> time, String[] labels, double[][] values) {
        initTimeSeries(size, labels, time);
        addTimeSeries(size, labels, time, values);
    }

    @Override
    public void writer(String fileName) {
                File f = new File(fileName);
        try {
            if (!f.exists() || f.isDirectory()) {
                String header = "Long Name" + ",IB Symbol" + ",Exchange Symbol" + ",Currency" + ",Contract ID" + ",Exchange" + ",Type";
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
                out.println(header);
                out.close();
            } 
                String data = this.getLongName() + "," + this.getBrokerSymbol() + "," + this.getExchangeSymbol() + "," + this.getCurrency() + "," + this.contractID + "," + this.getExchange() + "," + this.getType();
                PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
                out.println(data);
                out.close();
            
        } catch (Exception e) {
            logger.log(Level.SEVERE, null, e);
        }
    }

    //*************************************************************************
    //***************** GETTER/ SETTER **************************************
    //***********************************************************************
    /**
     * @return the brokerSymbol
     */
    public String getBrokerSymbol() {
        return brokerSymbol;
    }

    /**
     * @param brokerSymbol the brokerSymbol to set
     */
    public void setBrokerSymbol(String brokerSymbol) {
        this.brokerSymbol = brokerSymbol;
    }

    /**
     * @return the type
     */
    public String getType() {
        return type;
    }

    /**
     * @param type the type to set
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * @return the exchange
     */
    public String getExchange() {
        return exchange;
    }

    /**
     * @param exchange the exchange to set
     */
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    /**
     * @return the currency
     */
    public String getCurrency() {
        return currency;
    }

    /**
     * @param currency the currency to set
     */
    public void setCurrency(String currency) {
        this.currency = currency;
    }

    /**
     * @return the expiry
     */
    public String getExpiry() {
        return expiry;
    }

    /**
     * @param expiry the expiry to set
     */
    public void setExpiry(String expiry) {
        this.expiry = expiry;
    }

    /**
     * @return the option
     */
    public String getOption() {
        return option;
    }

    /**
     * @param option the option to set
     */
    public void setOption(String option) {
        this.option = option;
    }

    /**
     * @return the right
     */
    public String getRight() {
        return right;
    }

    /**
     * @param right the right to set
     */
    public void setRight(String right) {
        this.right = right;
    }

    /**
     * @return the contractID
     */
    public int getContractID() {
        return contractID;
    }

    /**
     * @param contractID the contractID to set
     */
    public void setContractID(int contractID) {
        this.contractID = contractID;
    }

    /**
     * @return the outlastAction
     */
    /**
     * @return the lastPrice
     */
    public double getLastPrice() {
        synchronized (lockLastPrice) {
            return lastPrice;
        }
    }

    /**
     * @param lastPrice the lastPrice to set
     */
    public void setLastPrice(double lastPrice) {
        synchronized (lockLastPrice) {
            double oldValue = this.lastPrice;
            this.lastPrice = lastPrice;
            if (lastPrice > this.getHighPrice()) {
                this.setHighPrice(lastPrice,false);
            } else if (lastPrice < this.getLowPrice() || getLowPrice() == 0) {
                this.setLowPrice(lastPrice,false);
            }

            if (propertySupport != null) {
                propertySupport.firePropertyChange(PROP_LASTPRICE, oldValue, lastPrice);
            }
        }
    }

    /**
     * @return the bidPrice
     */
    public double getBidPrice() {
        synchronized (lockBidPrice) {
            return bidPrice;
        }
    }

    /**
     * @param bidPrice the bidPrice to set
     */
    public void setBidPrice(double bidPrice) {
        synchronized (lockBidPrice) {
            double oldValue = this.bidPrice;
            this.bidPrice = bidPrice;
            if (propertySupport != null) {
                propertySupport.firePropertyChange(PROP_BIDPRICE, oldValue, bidPrice);
            }
        }

    }

    /**
     * @return the askPrice
     */
    public double getAskPrice() {
        synchronized (lockAskPrice) {
            return askPrice;
        }
    }

    /**
     * @param askPrice the askPrice to set
     */
    public void setAskPrice(double askPrice) {
        synchronized (lockAskPrice) {
            //logger.log(Level.FINER, "401,OldAskPrice: {0}", new Object[]{this.askPrice});
            //logger.log(Level.FINER, "401,NewAskPrice: {0}", new Object[]{askPrice});
            double oldValue = this.askPrice;
            this.askPrice = askPrice;
            if (propertySupport != null) {
                propertySupport.firePropertyChange(PROP_ASKPRICE, oldValue, askPrice);
            }
        }
    }

    /**
     * @return the volume
     */
    public int getVolume() {
        synchronized (lockVolume) {
            return volume;
        }
    }

    /**
     * @param volume the volume to set
     */

    
    public void setVolume(int volume, boolean override) {
        synchronized (lockVolume) {
            if (override) {
                this.volume = volume;
            } else {
                double oldValue = this.volume;
                if (oldValue < volume) {
                    this.volume = volume;
                    if (propertySupport != null) {
                        propertySupport.firePropertyChange(PROP_VOLUME, oldValue, volume);
                    }
                }
            }
        }
    }

    /**
     * @return the lastPriceTime
     */
    public long getLastPriceTime() {
        synchronized (lockLastPriceTime) {
            return lastPriceTime;
        }
    }

    /**
     * @param lastPriceTime the lastPriceTime to set
     */
    public void setLastPriceTime(long lastPriceTime) {
        synchronized (lockLastPrice) {
            this.lastPriceTime = lastPriceTime;
        }
    }

    /**
     * @return the priorDayVolume
     */
    public String getPriorDayVolume() {
        return priorDayVolume;
    }

    /**
     * @param priorDayVolume the priorDayVolume to set
     */
    public void setPriorDayVolume(String priorDayVolume) {
        this.priorDayVolume = priorDayVolume;
    }

    /**
     * @return the serialno
     */
    public int getSerialno() {
        return serialno;
    }

    /**
     * @param serialno the serialno to set
     */
    public void setSerialno(int serialno) {
        this.serialno = serialno;
    }

    /**
     * @return the primaryexchange
     */
    public String getPrimaryexchange() {
        return primaryexchange;
    }

    /**
     * @param primaryexchange the primaryexchange to set
     */
    public void setPrimaryexchange(String primaryexchange) {
        this.primaryexchange = primaryexchange;
    }

    /**
     * @return the reqID
     */
    public int getReqID() {
        return reqID;
    }

    /**
     * @param reqID the reqID to set
     */
    public void setReqID(int reqID) {
        this.reqID = reqID;
    }

    /**
     * @return the lastSize
     */
    public int getLastSize() {
        synchronized (lockLastSize) {
            return lastSize;
        }
    }

    /**
     * @param lastSize the lastSize to set
     */
    public void setLastSize(int lastSize) {
        synchronized (lockLastSize) {
            this.lastSize = lastSize;
        }
    }

    /**
     * @return the bidSize
     */
    public double getBidSize() {
        return bidSize;
    }

    /**
     * @param bidSize the bidSize to set
     */
    public void setBidSize(double bidSize) {
        this.bidSize = bidSize;
    }

    /**
     * @return the askSize
     */
    public double getAskSize() {
        return askSize;
    }

    /**
     * @param askSize the askSize to set
     */
    public void setAskSize(double askSize) {
        this.askSize = askSize;
    }

    /**
     * @return the minsize
     */
    public int getMinsize() {
        return minsize;
    }

    /**
     * @param minsize the minsize to set
     */
    public void setMinsize(int minsize) {
        this.minsize = minsize;
    }

    /**
     * @return the openPrice
     */
    public double getOpenPrice() {
        synchronized (lockOpenPrice) {
            return openPrice;
        }
    }

    public double getOpenPrice(long time, EnumBarSize size) {
        double out = ReservedValues.EMPTY;
        if (this.getRowLabels().get(size) != null) {
            Calendar priorDateCal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
            priorDateCal.setTimeInMillis(time);
            switch (size) {
                case ONESECOND:
                    priorDateCal.set(Calendar.MILLISECOND, 0);
                    priorDateCal.set(Calendar.SECOND, 00);
                    priorDateCal.set(Calendar.MINUTE, openMinute);
                    priorDateCal.set(Calendar.HOUR, openHour);
                    break;
                case ONEMINUTE:
                    priorDateCal.set(Calendar.MILLISECOND, 0);
                    priorDateCal.set(Calendar.SECOND, 0);
                    priorDateCal.set(Calendar.MINUTE, openMinute);
                    priorDateCal.set(Calendar.HOUR, openHour);
                    break;
                case DAILY:
                    priorDateCal.set(Calendar.MILLISECOND, 0);
                    priorDateCal.set(Calendar.SECOND, 0);
                    priorDateCal.set(Calendar.MINUTE, openMinute);
                    priorDateCal.set(Calendar.HOUR, openHour);
                    break;
                default:
                    break;
            }
            int timeIndex = this.getColumnLabels().get(size).indexOf(priorDateCal.getTimeInMillis());
            if (timeIndex >= 0) {
                out = this.getTimeSeriesValue(size, time, "close", false);
            }
            return out;
        } else {
            return ReservedValues.EMPTY;
        }

    }

    /**
     * @param openPrice the openPrice to set
     */
    public void setOpenPrice(double openPrice) {
        synchronized (lockOpenPrice) {
            if(openPrice!=0){
            this.openPrice = openPrice;
            }
        }
    }

    /**
     * @return the preopen
     */
    public String getPreopen() {
        return preopen;
    }

    /**
     * @param preopen the preopen to set
     */
    public void setPreopen(String preopen) {
        this.preopen = preopen;
    }

    /**
     * @return the closePrice
     */
    public double getClosePrice() {
        synchronized (lockClosePrice) {
            return closePrice;
        }
    }

    /**
     * returns the closePrice for the time value provided.
     *
     * @param time
     * @return
     */
    public double getClosePrice(long time, EnumBarSize size) {
        double out = 0D;
        if (this.getRowLabels().get(size) != null) {
            int closeIndex = this.getRowLabels().get(size).indexOf("close");
            Calendar priorDateCal = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
            priorDateCal.setTimeInMillis(time);
            switch (size) {
                case ONESECOND:
                    Calendar now = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    now.set(Calendar.HOUR_OF_DAY, closeHour);
                    now.set(Calendar.MINUTE, closeMinute);
                    now.set(Calendar.SECOND, 0);
                    now.set(Calendar.MILLISECOND, 0);
                    now.add(Calendar.SECOND, -1);

                    priorDateCal.set(Calendar.MILLISECOND, 0);
                    priorDateCal.set(Calendar.SECOND, now.get(Calendar.SECOND));
                    priorDateCal.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
                    priorDateCal.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));
                    break;
                case ONEMINUTE:
                    now = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
                    now.set(Calendar.HOUR_OF_DAY, closeHour);
                    now.set(Calendar.MINUTE, closeMinute);
                    now.set(Calendar.SECOND, 0);
                    now.set(Calendar.MILLISECOND, 0);
                    now.add(Calendar.MINUTE, -1);

                    priorDateCal.set(Calendar.MILLISECOND, 0);
                    priorDateCal.set(Calendar.SECOND, now.get(Calendar.SECOND));
                    priorDateCal.set(Calendar.MINUTE, now.get(Calendar.MINUTE));
                    priorDateCal.set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY));

                    break;
                case DAILY:
                    priorDateCal.set(Calendar.MILLISECOND, 0);
                    priorDateCal.set(Calendar.SECOND, 0);
                    priorDateCal.set(Calendar.MINUTE, openMinute);
                    priorDateCal.set(Calendar.HOUR, openHour);
                    break;
                default:
                    break;
            }
            int timeIndex = this.getColumnLabels().get(size).indexOf(priorDateCal.getTimeInMillis());
            if (timeIndex >= 0) {
                out = this.getTimeSeries().get(size).get(closeIndex, timeIndex);
            } else {
                out = 0;
            }
            return out;
        } else {
            return ReservedValues.EMPTY;
        }
    }

    /**
     * @param closePrice the closePrice to set
     */
    public void setClosePrice(double closePrice) {
        synchronized (lockClosePrice) {
            this.closePrice = closePrice;
        }
    }

    /**
     * @return the yesterdayLastPrice
     */
    public double getYesterdayLastPrice() {
        return yesterdayLastPrice;
    }

    /**
     * @param yesterdayLastPrice the yesterdayLastPrice to set
     */
    public void setYesterdayLastPrice(double yesterdayLastPrice) {
        this.yesterdayLastPrice = yesterdayLastPrice;
    }

    /**
     * @return the lowPrice
     */
    public double getLowPrice() {
        return lowPrice;
    }

    /**
     * @param lowPrice the lowPrice to set
     */

    public void setLowPrice(double lowPrice, boolean override) {
        synchronized (lockLowPrice) {
            if (override) {
                this.lowPrice = lowPrice;
            } else {
                if (this.lowPrice == 0 || (lowPrice < this.lowPrice && lowPrice != 0)) {
                    this.lowPrice = lowPrice;
                }
            }
        }
    }
    /**
     * @return the highPrice
     */
    public double getHighPrice() {
        return highPrice;
    }

    /**
     * @param highPrice the highPrice to set
     */
  
    public void setHighPrice(double highPrice, boolean override) {
        synchronized (lockHighPrice) {
            if (override) {
                this.highPrice = highPrice;
            } else {
                if (highPrice > this.highPrice) {
                    this.highPrice = highPrice;
                }
            }
        }
    }

    /**
     * @return the status
     */
    public synchronized Boolean isStatus() {
        return status;
    }

    /**
     * @param status the status to set
     */
    public synchronized void setStatus(Boolean status) {
        this.status = status;
    }

    /**
     * @return the streamingpriority
     */
    public int getStreamingpriority() {
        return streamingpriority;
    }

    /**
     * @param streamingpriority the streamingpriority to set
     */
    public void setStreamingpriority(int streamingpriority) {
        this.streamingpriority = streamingpriority;
    }

    /**
     * @return the strategy
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * @param strategy the strategy to set
     */
    public void setStrategy(String strategy) {
        this.strategy = strategy;
    }

    /**
     * @return the prevLastPrice
     */
    public double getPrevLastPrice() {
        synchronized (lockPrevLastPrice) {
            return prevLastPrice;
        }
    }

    /**
     * @param prevLastPrice the prevLastPrice to set
     */
    public void setPrevLastPrice(double prevLastPrice) {
        synchronized (lockPrevLastPrice) {
            this.prevLastPrice = prevLastPrice;
        }
    }

    /**
     * @return the servicename
     */
    public String getDisplayname() {
        return displayName;
    }

    /**
     * @param servicename the servicename to set
     */
    public void setDisplayname(String displayName) {
        this.displayName = displayName;
    }

    /**
     * @return the barsstarttime
     */
    public String getBarsstarttime() {
        return barsstarttime;
    }

    /**
     * @param barsstarttime the barsstarttime to set
     */
    public void setBarsstarttime(String barsstarttime) {
        this.barsstarttime = barsstarttime;
    }

    /**
     * @return the bidVol
     */
    public double getBidVol() {
        return bidVol;
    }

    /**
     * @param bidVol the bidVol to set
     */
    public void setBidVol(double bidVol) {
        this.bidVol = bidVol;
    }

    /**
     * @return the askVol
     */
    public double getAskVol() {
        return askVol;
    }

    /**
     * @param askVol the askVol to set
     */
    public void setAskVol(double askVol) {
        this.askVol = askVol;
    }

    /**
     * @return the lastVol
     */
    public double getLastVol() {
        return lastVol;
    }

    /**
     * @param lastVol the lastVol to set
     */
    public void setLastVol(double lastVol) {
        this.lastVol = lastVol;
    }

    /**
     * @return the atmStrike
     */
    public double getAtmStrike() {
        return atmStrike;
    }

    /**
     * @param atmStrike the atmStrike to set
     */
    public void setAtmStrike(double atmStrike) {
        this.atmStrike = atmStrike;
    }

    /**
     * @return the dataConnectionID
     */
    public int getDataConnectionID() {
        return dataConnectionID;
    }

    /**
     * @param dataConnectionID the dataConnectionID to set
     */
    public void setDataConnectionID(int dataConnectionID) {
        this.dataConnectionID = dataConnectionID;
    }

    /**
     * @return the firstTimeStamp
     */
    public long getFirstTimeStamp() {
        return firstTimeStamp;
    }

    /**
     * @param firstTimeStamp the firstTimeStamp to set
     */
    public void setFirstTimeStamp(long firstTimeStamp) {
        this.firstTimeStamp = firstTimeStamp;
    }

    /**
     * @return the connectionidUsedForMarketData
     */
    public int getConnectionidUsedForMarketData() {
        return connectionidUsedForMarketData;
    }

    /**
     * @param connectionidUsedForMarketData the connectionidUsedForMarketData to
     * set
     */
    public void setConnectionidUsedForMarketData(int connectionidUsedForMarketData) {
        this.connectionidUsedForMarketData = connectionidUsedForMarketData;
    }

    /**
     * @return the tickSize
     */
    public double getTickSize() {
        return tickSize;
    }

    /**
     * @param tickSize the tickSize to set
     */
    public void setTickSize(double tickSize) {
        this.tickSize = tickSize;
    }

    /**
     * @return the comboSetupFailed
     */
    public boolean isComboSetupFailed() {
        return comboSetupFailed;
    }

    /**
     * @param comboSetupFailed the comboSetupFailed to set
     */
    public void setComboSetupFailed(boolean comboSetupFailed) {
        this.comboSetupFailed = comboSetupFailed;
    }

    /**
     * @return the timeSeries
     */
    public ConcurrentHashMap<EnumBarSize, DoubleMatrix> getTimeSeries() {
        return timeSeries;
    }

    /**
     * @param timeSeries the timeSeries to set
     */
    public void setTimeSeries(ConcurrentHashMap<EnumBarSize, DoubleMatrix> timeSeries) {
        this.timeSeries = timeSeries;
    }

    /**
     * @return the columnLabels
     */
    public ConcurrentHashMap<EnumBarSize, List<Long>> getColumnLabels() {
        return BeanSymbol.columnLabels;
    }

    /**
     * @param columnLabels the columnLabels to set
     */
    public void setColumnLabels(ConcurrentHashMap<EnumBarSize, List<Long>> columnLabels) {
        BeanSymbol.columnLabels = columnLabels;
    }

    /**
     * @return the rowLabels
     */
    public ConcurrentHashMap<EnumBarSize, List<String>> getRowLabels() {
        return BeanSymbol.rowLabels;
    }

    /**
     * @param rowLabels the rowLabels to set
     */
    public void setRowLabels(ConcurrentHashMap<EnumBarSize, List<String>> rowLabels) {
        BeanSymbol.rowLabels = rowLabels;
    }

    /**
     * @return the active
     */
    public boolean isActive() {
        return active;
    }

    /**
     * @param active the active to set
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * @return the combo
     */
    public HashMap<BeanSymbol, Integer> getCombo() {
        return combo;
    }

    /**
     * @param combo the combo to set
     */
    public void setCombo(HashMap<BeanSymbol, Integer> combo) {
        this.combo = combo;
    }

    /**
     * @return the fundamental
     */
    public Fundamental getFundamental() {
        return fundamental;
    }

    /**
     * @param fundamental the fundamental to set
     */
    public void setFundamental(Fundamental fundamental) {
        this.fundamental = fundamental;
    }

    /**
     * @return the tradedPrices
     */
    public LimitedQueue<Double> getTradedPrices() {
        return tradedPrices;
    }

    /**
     * @param tradedPrices the tradedPrices to set
     */
    public void setTradedPrices(LimitedQueue<Double> tradedPrices) {
        this.tradedPrices = tradedPrices;
    }

    /**
     * @return the tradedVolumes
     */
    public LimitedQueue<Integer> getTradedVolumes() {
        return tradedVolumes;
    }

    /**
     * @param tradedVolumes the tradedVolumes to set
     */
    public void setTradedVolumes(LimitedQueue<Integer> tradedVolumes) {
        this.tradedVolumes = tradedVolumes;
    }

    /**
     * @return the tradedDateTime
     */
    public LimitedQueue<Long> getTradedDateTime() {
        return tradedDateTime;
    }

    /**
     * @param tradedDateTime the tradedDateTime to set
     */
    public void setTradedDateTime(LimitedQueue<Long> tradedDateTime) {
        this.tradedDateTime = tradedDateTime;
    }

    /**
     * @return the OneMinuteBarFromRealTimeBars
     */
    public DataBars getOneMinuteBarFromRealTimeBars() {
        return OneMinuteBarFromRealTimeBars;
    }

    /**
     * @param OneMinuteBarFromRealTimeBars the OneMinuteBarFromRealTimeBars to
     * set
     */
    public void setOneMinuteBarFromRealTimeBars(DataBars OneMinuteBarFromRealTimeBars) {
        this.OneMinuteBarFromRealTimeBars = OneMinuteBarFromRealTimeBars;
    }

    /**
     * @return the dailyBar
     */
    public DataBars getDailyBar() {
        return dailyBar;
    }

    /**
     * @param dailyBar the dailyBar to set
     */
    public void setDailyBar(DataBars dailyBar) {
        this.dailyBar = dailyBar;
    }

    /**
     * @return the intraDayBarsFromTick
     */
    public DataBars getIntraDayBarsFromTick() {
        return intraDayBarsFromTick;
    }

    /**
     * @param intraDayBarsFromTick the intraDayBarsFromTick to set
     */
    public void setIntraDayBarsFromTick(DataBars intraDayBarsFromTick) {
        this.intraDayBarsFromTick = intraDayBarsFromTick;
    }

    /**
     * @return the databars
     */
    public ConcurrentHashMap<EnumBarSize, BeanOHLC> getDatabars() {
        return databars;
    }

    /**
     * @param databars the databars to set
     */
    public void setDatabars(EnumBarSize barSize, int duration) {
        final EnumBarSize barSizeLocal = barSize;
        databars.put(barSize, new BeanOHLC(this.getSerialno() - 1, duration));
        if (Algorithm.databarSetup.get(barSize) == null) {
            Algorithm.databarSetup.put(barSize, DateUtil.getNextPeriodStartTime(barSize));
            ScheduledExecutorService ex = Executors.newSingleThreadScheduledExecutor();
            ScheduledFuture scheduledFuture =
                    ex.schedule(new Callable() {
                public Object call() throws Exception {
                    for (BeanSymbol s : Parameters.symbol) {
                        BeanOHLC ohlc = s.getDatabars().get(barSizeLocal);
                        long time = Algorithm.databarSetup.get(barSizeLocal);
                        if (ohlc.getOpen() != 0) {
                            s.addTimeSeries(barSizeLocal, new String[]{"open", "high", "low", "close", "volume"}, time, new double[]{ohlc.getOpen(), ohlc.getHigh(), ohlc.getLow(), ohlc.getClose(), ohlc.getVolume()});
                            ohlc.setVolume(0);
                        }
                    }
                    Algorithm.databarSetup.put(barSizeLocal, DateUtil.getNextPeriodStartTime(barSizeLocal));
                    return true;
                }
            },
                    duration,
                    TimeUnit.MINUTES);
        }

        this.databars = databars;
    }

    /**
     * @return the exchangeSymbol
     */
    public String getExchangeSymbol() {
        return exchangeSymbol;
    }

    /**
     * @param exchangeSymbol the exchangeSymbol to set
     */
    public void setExchangeSymbol(String exchangeSymbol) {
        this.exchangeSymbol = exchangeSymbol;
    }

    /**
     * @return the longName
     */
    public String getLongName() {
        return longName;
    }

    /**
     * @param longName the longName to set
     */
    public void setLongName(String longName) {
        this.longName = longName;
    }
}
