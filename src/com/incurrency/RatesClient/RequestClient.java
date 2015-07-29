/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.RatesClient;

import com.google.common.collect.ObjectArrays;
import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.Drop;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Request;
import com.incurrency.framework.RequestIDManager;
import com.incurrency.framework.SplitInformation;
import com.incurrency.framework.TradingUtil;
import com.incurrency.framework.Utilities;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import org.zeromq.ZMQ;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author pankaj
 */
public class RequestClient implements Runnable {

    private static RequestClient singleton = null;
    ZMQ.Context context = ZMQ.context(1);
    ZMQ.Socket requester;
    int requestid = -1;
    BeanSymbol s;
    private AtomicBoolean availableForNewRequest = new AtomicBoolean(true);
    private boolean savetofile = false;
    private boolean savetomemory = false;
    private boolean headerWritten = false;
    private double lastCloseValue = 0;
    private long lastVolume = 0;
    private int gapDownCount = 0;
    private int probableSplitRatio = 0;
    private String splitDate = "";
    HashMap<String, double[]> ind = new HashMap<>();
    private Thread t;
    private volatile boolean shouldStop = false;
    private boolean threadStarted = false;
    boolean intrarequestgap = false;
    public static ConcurrentHashMap<Integer,EnumBarSize> reqbarMapping=new ConcurrentHashMap<>();
    private static final Logger logger = Logger.getLogger(RequestClient.class.getName());
    private String[] timeSeries;
    public Drop start=new Drop();
    public Drop end =new Drop();
    private boolean appendAtEnd=true;
    private boolean scansplits=false;
    
    public static RequestClient singleton(String path) {
        if (singleton == null) {
            singleton = new RequestClient(path);
        }
        return singleton;
    }

    public RequestClient(String path) {
        t = new Thread(this, "RequestClient");
        t.setName("RequestClient");
        requester = context.socket(ZMQ.REQ);
        requester.connect("tcp://" + path);
        savetofile = Boolean.parseBoolean(Algorithm.globalProperties.getProperty("savetofile","false"));
        savetomemory = Boolean.parseBoolean(Algorithm.globalProperties.getProperty("savetomemory","true"));
        scansplits=Boolean.parseBoolean(Algorithm.globalProperties.getProperty("scansplits","false"));
    }

    public void start() {
        if (!threadStarted) {
            threadStarted = true;
            t.start();
        }
    }

    public void stop1() {
        end.put("end");
        shouldStop = true;
    }

    /*
    public void sendRequest(String requestType, BeanSymbol s, String[] parameters, String metric,String[]timeSeries,boolean appendAtEnd) {
        String concatParameters = null;
        String request = null;
        this.s=s;
        this.appendAtEnd=appendAtEnd;
        this.headerWritten=appendAtEnd;
        this.timeSeries=timeSeries;
        switch (requestType) {
            case "contractid":
                request = requestType + ":" + s.getDisplayname();
                break;
            case "backfill":
                String append = parameters[0] + "," + parameters[1] + "," + parameters[2];
                request = "requestid" + ":" + s.getDisplayname() + ":" + append + ":" + metric;
                //parameters[0]=barSize
                //parameters[1]=requestid
                break;
            case "historicaldata":
                for (String p : parameters) {
                    if (concatParameters == null) {
                        concatParameters = p;
                    } else {
                        concatParameters = concatParameters + "," + p;
                    }
                }
                request = requestType + ":" + s.getDisplayname() + ":" + concatParameters;
                break;
            default:
                break;
        }
        requester.send(request.getBytes(ZMQ.CHARSET), 0);
        //logger.log(Level.FINE,"Symbol: {0}, Request sent for {1}",new Object[]{symbol,requestType.toString()});
        //System.out.println("setting availability for new request as false as new requestid request sent");
        setAvailableForNewRequest(false);
    }
*/
    
      public void sendRequest(String requestType, BeanSymbol s, String[] parameters, String metric,String[]timeSeries,boolean appendAtEnd) {
        String concatParameters = null;
        String request = null;
        this.s=s;
        this.appendAtEnd=appendAtEnd;
        this.headerWritten=appendAtEnd;
        this.timeSeries=timeSeries;
        switch (requestType) {
            case "contractid":
                request = requestType + ":" + s.getDisplayname();
                break;
            case "backfill":
                String append = parameters[0] + "," + parameters[1] + "," + parameters[2];
                request = "requestid" + ":" + s.getDisplayname() + ":" + append + ":" + metric;
                //parameters[0]=barSize
                //parameters[1]=requestid
                break;
            case "historicaldata":
                for (String p : parameters) {
                    if (concatParameters == null) {
                        concatParameters = p;
                    } else {
                        concatParameters = concatParameters + "," + p;
                    }
                }
                request = requestType + ":" + s.getDisplayname() + ":" + concatParameters;
                break;
            default:
                break;
        }
        requester.send(request.getBytes(ZMQ.CHARSET), 0);
        //logger.log(Level.FINE,"Symbol: {0}, Request sent for {1}",new Object[]{symbol,requestType.toString()});
        //System.out.println("setting availability for new request as false as new requestid request sent");
        setAvailableForNewRequest(false);

    }

    
    @Override
    public void run() {
        try {
            while (!shouldStop) {
                if (!availableForNewRequest.get() && !intrarequestgap) {
                    Thread.yield();
                    byte[] responseBytes = requester.recv(0);
                    String response = new String(responseBytes, ZMQ.CHARSET);
                    if (response.contains("requestid")) {
                        intrarequestgap = true;
                        //System.out.println("intragap request set to true as requestid received");
                    }
                    processResponse(response);
                    if (response.contains("finished")) {
                        logger.log(Level.INFO,"Symbol:{0}, Status: Historical Data Retrieved",new Object[]{s.getDisplayname()});
                        stop1();
                        availableForNewRequest.set(true);
                    }
                }
            }
        } finally {
            close();
        }
    }

    private void processResponse(String response) {
        String requestType = response.split(":")[0];
        switch (requestType) {
            case "contractid":
            System.out.println(response);
            String identifier = response.split(":")[1];
            String[] symbol = identifier.split("_");
                if (symbol.length >= 5) {
                    int id = Utilities.getIDFromExchangeSymbol(Parameters.symbol,symbol[0],symbol[1],symbol[2],symbol[3],symbol[4]);
                    if (id >= 0) {
                        int length = symbol.length;
                        switch (length) {
                            case 6:
                                Parameters.symbol.get(id).setContractID(Integer.parseInt(symbol[5]));
                                break;
                            case 7:
                                Parameters.symbol.get(id).setContractID(Integer.parseInt(symbol[5]));
                                Parameters.symbol.get(id).setTickSize(Double.parseDouble(symbol[6]));
                                break;
                            default:
                                break;
                        }
                    }
                }
                this.setAvailableForNewRequest(true);
                break;
                case "requestid":
                requestid = Math.max(Integer.valueOf(response.split(":")[4]), RequestIDManager.singleton().getNextRequestId());
                String sBarSize = response.split(":")[2].split(",")[0];
                EnumBarSize barSize = EnumBarSize.valueOf(sBarSize.toUpperCase());
                RequestClient.reqbarMapping.put(requestid, barSize);
                RequestIDManager.singleton().setRequestId(new AtomicInteger(requestid));
                symbol = response.split(":")[1].split("_");
 //               BeanSymbol s = Scanner.symbol.get(Utilities.getIDFromSymbol(symbol));
                Request r = new Request(requestid, s, barSize,"na");
                //Scanner.requestDetails.put(requestid, r);
                //Scanner.requestDetails.put(requestid,new Request(requestid, s, EnumRequestType.HISTORICAL,EnumBarSize.DAILY, EnumRequestStatus.PENDING, new Date().getTime()));
                String request = "backfill" + ":" + response.split(":")[1] + ":" + response.split(":")[2] + ":" + response.split(":")[3] + ":" + requestid;
                requester.send(request.getBytes(ZMQ.CHARSET), 0);
                intrarequestgap = false;
                //System.out.println("intragap request set to false as backfill request sent");
                break;
            case "backfill":
                //requesttype_stock_""_""_requestid_value
                String reqid = response.split(":")[4];
                barSize = RequestClient.reqbarMapping.get(Integer.valueOf(reqid));
                identifier = response.split(":")[1];
                String value = response.split(":")[5];
                //System.out.println(value);
                if (requestid == Integer.valueOf(reqid)) {
                    if (response.contains("finished")) {
                        //Scanner.syncSymbols.put(response.split(":")[1]);
                        //Thread.yield();
                       this.headerWritten = false;
                        if (gapDownCount > 0) {
                            //this is a probable split. Print split ratio.
                            int roundedSplitRatio = (int) (Utilities.round(((double) probableSplitRatio) / 100, 0.01) * 100);
                            SplitInformation si = new SplitInformation(identifier.toUpperCase(), splitDate, 100, roundedSplitRatio);
                            gapDownCount = 0;
                            lastCloseValue = 0;
                            lastVolume = 0;
                            probableSplitRatio = 0;
                            splitDate = "";
                            Utilities.writeSplits(si);
                        }
                        if(savetofile){
                           // s.saveToExternalFile(barSize);
                        }
                        this.setAvailableForNewRequest(true);
                    } else {
                        if (savetomemory) {
                            String[] values = value.split("_"); 
                            EnumBarSize tempBarSize=RequestClient.reqbarMapping.get(Integer.valueOf(reqid));
                            logger.log(Level.FINER,"Time:{0}, Symbol:{1}, Price:{2}, Values:{3}",new Object[]{new SimpleDateFormat("yyyyMMdd HH:mm:ss").format(new Date(Long.valueOf(values[0]))),s.getDisplayname(),values[1],values.length});
                            s.setTimeSeries(tempBarSize, timeSeries, values);
                        }
                        if (savetofile) {                            
                            String filename = s.getDisplayname().toUpperCase()+"_"+barSize.toString() + ".csv";
                            String[] values = value.split("_");
                            if (!headerWritten) {
                                if (values.length <= 6) {
                                    String[] valueArray=ObjectArrays.concat("", timeSeries);
                                    //String[] valueArray = new String[]{null, "Open", "High", "Low", "Close", "Volume"};
                                    Utilities.writeToFile(filename, valueArray, Algorithm.timeZone,true);
                                } else {    
                                    String[] valueArray=ObjectArrays.concat("", timeSeries);
                                    //String[] valueArray = new String[]{null, "Open", "High", "Low", "Close", "Volume", "OI"};
                                    Utilities.writeToFile(filename, valueArray, Algorithm.timeZone,true);
                                }
                                headerWritten = true;
                            }
                            String[] valueArray = value.split("_");
                            Utilities.writeToFile(filename, valueArray, Algorithm.timeZone,true);
                        
                        }
                        if (scansplits) {
                            String[] valueArray = value.split("_");
                            //write data to suggested splits
                            if (gapDownCount > 0 && gapDownCount < 30) {
                                double currentHighValue = Double.parseDouble(valueArray[2]);
                                if (currentHighValue < lastCloseValue || probableSplitRatio < 120) {
                                    gapDownCount = gapDownCount + 1;
                                } else {
                                    gapDownCount = 0; //reset the gap as a false signal
                                    lastCloseValue = 0;
                                    lastVolume = 0;
                                    probableSplitRatio = 0;
                                    splitDate = "";
                                }
                            } else if (gapDownCount >= 30) {
                                //this is a probable split. Print split ratio.
                                int roundedSplitRatio = (int) (Utilities.round(((double) probableSplitRatio) / 100, 0.01) * 100);
                                SplitInformation si = new SplitInformation(identifier.toUpperCase(), splitDate, 100, roundedSplitRatio);

                                gapDownCount = 0;
                                lastCloseValue = 0;
                                lastVolume = 0;
                                probableSplitRatio = 0;
                                splitDate = "";
                                Utilities.writeSplits(si);
                            } else {
                                double currentOpenValue = Double.parseDouble(value.split("_")[1]);
                                long currentVolume = Long.parseLong(value.split("_")[5]);
                                if (currentOpenValue < 0.9 * lastCloseValue && currentVolume > 0.5 * lastVolume) {
                                    gapDownCount = 1;
                                    splitDate = valueArray[0];
                                    probableSplitRatio = Integer.valueOf((int) Math.round(lastCloseValue * 100 / currentOpenValue));
                                } else {
                                    lastCloseValue = Double.parseDouble(value.split("_")[4]);
                                    lastVolume = Long.parseLong(value.split("_")[5]);
                                }
                            }
                        }
                    }

                }
                break;
            default:
                break;
        }
    }

    public void indicators() {
        //All indicators are run in the main thread by virtue of "run()"

    }

    public void close() {
        requester.close();
        context.term();
    }

    /**
     * @return the availableForNewRequest
     */
    public  boolean isAvailableForNewRequest() {
        return availableForNewRequest.get();
    }

    /**
     * @param availableForNewRequest the availableForNewRequest to set
     */
    public void setAvailableForNewRequest(boolean availableForNewRequest) {
        this.availableForNewRequest.set(availableForNewRequest);
    }
}
