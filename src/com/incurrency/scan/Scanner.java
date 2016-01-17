/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.scan;

import com.incurrency.framework.Algorithm;
import com.incurrency.framework.BeanSymbol;
import com.incurrency.framework.Drop;
import com.incurrency.framework.EnumBarSize;
import com.incurrency.framework.Parameters;
import com.incurrency.framework.Request;
import com.incurrency.framework.Utilities;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class Scanner implements Runnable{

    /**
     * @param args the command line arguments
     */
    public static ConcurrentHashMap<Integer, Request> requestDetails = new ConcurrentHashMap<>();
    public static boolean scansplits = false;
    public static boolean dayCompleted = false;
    public static Drop dateProcessing = new Drop();
    public static Drop syncSymbols = new Drop();
    public static String type;
    public static boolean finished = false;
    public static ArrayList<Thread> t = new ArrayList<>();
    public static ExtendedHashMap<String, String, Double> output = new ExtendedHashMap<>();
    private static final Logger logger = Logger.getLogger(Scanner.class.getName());
    public static HashMap<String,String>args;
                
public Scanner(HashMap<String,String>args){
    this.args=args;
}
    
    public void start()  {
        try{
        finished=false;
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
        Date startDate = args.get("startdate")==null?new Date():dateFormatter.parse(args.get("startdate"));
        Date endDate =args.get("enddate")==null?new Date():dateFormatter.parse(args.get("enddate")) ;
        boolean datesProvidedInParamFile=args.get("startdate")!=null ||args.get("enddate")!=null;
        if(!datesProvidedInParamFile){
        switch (args.size()) {
            case 2:
                startDate = dateFormatter.parse(args.get("1"));
                endDate = new Date();
                break;
            case 3:
                startDate = dateFormatter.parse(args.get("1"));
                endDate = dateFormatter.parse(args.get("2"));
                break;
            case 1:
                Calendar now = Calendar.getInstance();
                now.setTime(new Date());
                now.set(Calendar.HOUR_OF_DAY, 0);
                now.set(Calendar.MINUTE, 0);
                now.set(Calendar.SECOND, 0);
                now.set(Calendar.MILLISECOND, 0);
                startDate = now.getTime();
                now.add(Calendar.DATE, 1);
                endDate = now.getTime();
                break;
            default:
                break;
        }
        }
        if (Boolean.parseBoolean(args.get("scanforsplits"))) {
            scansplits = true;
        }
        boolean incrementalData = Boolean.parseBoolean(args.get("incrementaldata"));
        type = args.get("datatype").toString();            
                    Date endDate1=new Date();
                //loop through dates
                for (Date startDate1 = startDate; startDate1.compareTo(endDate) < 0; startDate1 = endDate1) {
                    //System.out.println("Start Date:" + startDate1 + " ,End Date:" + nextGoodDay(startDate1));
                    while (!dateProcessing.empty()) {
                        Thread.yield();
                        Thread.sleep(10000);
                    }
                    Scanner.finished=false;
                    if (incrementalData) {
                        endDate1 = nextGoodDay(startDate1);
                    } else {
                        endDate1 = endDate;
                    }
                    System.out.println("Start Date:" + startDate1 + " ,End Date:" + endDate1);
                     SimpleDateFormat datetimeCleanFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
                     String strStartDate=datetimeCleanFormat.format(startDate);
                     String strEndDate=datetimeCleanFormat.format(endDate1);
                    for (BeanSymbol s : Parameters.symbol) {
                        String metric = null;
                        switch (s.getType()) {
                            case "STK":
                                metric = args.get("stk").toString().trim();
                                break;
                            case "IND":
                                metric = args.get("ind").toString().trim();
                                break;
                            case "FUT":
                                metric = args.get("fut").toString().trim();
                                break;
                            case "OPT":
                                metric = args.get("opt").toString().trim();
                                break;
                            default:
                                break;
                        }
                        Utilities.requestHistoricalData(s, args.get("timeseries").split(","), metric, "yyyyMMdd HH:mm:ss", strStartDate, strEndDate, EnumBarSize.valueOf(args.get("barsize").toUpperCase().toString().trim()), true);
                    }
                dateProcessing.put("finished");
                }
            
        }catch(Exception e){
            logger.log(Level.SEVERE,null,e);
        }   
    }
    
    public static Date nextGoodDay(Date startDate) {
        Calendar entryCal = Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        entryCal.setTime(startDate);
        int entryDayOfWeek = entryCal.get(Calendar.DAY_OF_WEEK);
        if (entryCal.get(Calendar.MILLISECOND) > 0) {
            entryCal.set(Calendar.MILLISECOND, 0);
        }        
        Calendar exitCal = (Calendar) entryCal.clone();
        exitCal.setTimeZone(TimeZone.getTimeZone(Algorithm.timeZone));
        exitCal.set(Calendar.DAY_OF_YEAR, exitCal.get(Calendar.DAY_OF_YEAR) + 1);
        
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        boolean holiday = true;
        while (holiday) {
            if (exitCal.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY) {//Saturday
                exitCal.set(Calendar.DAY_OF_YEAR, exitCal.get(Calendar.DAY_OF_YEAR) + 2);
            } else if (exitCal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) {//Sunday
                exitCal.set(Calendar.DAY_OF_YEAR, exitCal.get(Calendar.DAY_OF_YEAR) + 1);
            }
            if (Algorithm.holidays != null && Algorithm.holidays.contains(sdf.format(exitCal.getTime()))) {
                exitCal.set(Calendar.DAY_OF_YEAR, exitCal.get(Calendar.DAY_OF_YEAR) + 1);
            } else {
                holiday = false;
            }
        }
        return exitCal.getTime();
    }

    @Override
    public void run() {
        start();
    }
}
