/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.google.common.base.Preconditions;
import com.ib.client.TickType;
import com.verhas.licensor.License;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.mail.internet.InternetAddress;
import javax.swing.JOptionPane;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;


/**
 *
 * @author psharma
 */
public class TradingUtil {

    public final static Logger logger = Logger.getLogger(TradingUtil.class.getName());
    public static String newline = System.getProperty("line.separator");
    public static License lic=null;
    public static byte[] digest;

    /**
 * Prints data in an ExtendedHashMap to a file.
 * @param h
 * @param filename
 * @param printOrder 
 */
    public static void print(ExtendedHashMap<String, String, Double> h, String filename, String[] printOrder) {

        if (h.store.size() > 0) {
            boolean headersWritten = false;
            String headers = "";
            for (String key : h.store.keySet()) {
                String output = key.toString();
                //for(Map.Entry<String,Double> values:h.store.get(key).entrySet()){
                for (int i = 0; i < printOrder.length; i++) {
                    Iterator it = h.store.get(key).entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, Double> values = (Map.Entry) it.next();

                        if (values.getKey().equals(printOrder[i])) {
                            if (!headersWritten) {
                                headers = headers + "," + values.getKey().toString();
                            }
                            output = output + "," + values.getValue().toString();
                            it.remove();
                        }
                    }
                }
                Iterator it = h.store.get(key).entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Double> values = (Map.Entry) it.next();
                    if (!headersWritten) {
                        headers = headers + "," + values.getKey().toString();
                    }
                    output = output + "," + values.getValue().toString();
                }
                //}
                if (!headersWritten) {
                    writeToFile(filename, headers);
                    headersWritten = true;
                }
                writeToFile(filename, output);
                output = "";
            }
        }
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
            while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1 || holidays.contains(exitCalString)) {
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
            while (exitCal.get(Calendar.DAY_OF_WEEK) == 7 || exitCal.get(Calendar.DAY_OF_WEEK) == 1 || holidays.contains(exitCalString)) {
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
     * Returns the first day of the next week after specified TIME.Date is not adjusted for holidays.
     * @param time
     * @param hour
     * @param minute
     * @param timeZone
     * @return 
     */
    public static long beginningOfWeek(long time, int hour, int minute,String timeZone, int jumpAhead){
        Calendar cal=Calendar.getInstance(TimeZone.getTimeZone(timeZone));
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
     * Returns the first day of the next month,using specified hour and minute.Dates are not adjusted for holidays.
     * @param time
     * @param hour
     * @param minute
     * @param timeZone
     * @return 
     */
        public static long beginningOfMonth(long time, int hour, int minute,String timeZone, int jumpAhead){
        Calendar cal=Calendar.getInstance(TimeZone.getTimeZone(timeZone));
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
         * @param time
         * @param hour
         * @param minute
         * @param timeZone
         * @return 
         */
        public static long beginningOfYear(long time, int hour, int minute,String timeZone,int jumpAhead){
        Calendar cal=Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        cal.setTimeInMillis(time);
        cal.add(Calendar.YEAR,jumpAhead);
        cal.set(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, minute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
        
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
     * Returns a native int[] of specified 'size' filled with values starting from 'value', 
     * incremented by 'increment'.
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
            Calendar iStartTime = Calendar.getInstance();
            iStartTime.setTimeInMillis(start);
            iStartTime.setTimeZone(timeZone);
            Calendar iEndTime = Calendar.getInstance();
            iEndTime.setTimeInMillis(end);
            iEndTime.setTimeZone(timeZone);
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
                    iStartTime=Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                   // System.out.println(iStartTime.getTimeInMillis()+","+iEndTime.getTimeInMillis());
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(beginningOfWeek(iStartTime.getTimeInMillis(), openHour, openMinute, zone,1));
                    }                    
                    break;
                case MONTHLY:
                    iStartTime=Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_MONTH, 1);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(beginningOfMonth(iStartTime.getTimeInMillis(), openHour, openMinute, zone,1));
                    }   
                    break;
                case ANNUAL:
                    iStartTime=Calendar.getInstance(timeZone);
                    iStartTime.setTimeInMillis(start);
                    iStartTime.set(Calendar.DAY_OF_YEAR, 1);
                    iStartTime.set(Calendar.HOUR_OF_DAY, openHour);
                    iStartTime.set(Calendar.MINUTE, openMinute);
                    iStartTime.set(Calendar.SECOND, 0);
                    iStartTime.set(Calendar.MILLISECOND, 0);
                    while (iStartTime.before(iEndTime) || iStartTime.equals(iEndTime)) {
                        out.add(iStartTime.getTimeInMillis());
                        iStartTime.setTimeInMillis(beginningOfMonth(iStartTime.getTimeInMillis(), openHour, openMinute, zone,1));
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


    
     public static Properties loadParameters(String parameterFile) {
        Properties p = new Properties();
        FileInputStream propFile;
        try {
            propFile = new FileInputStream(parameterFile);
            p.load(propFile);

        } catch (Exception ex) {
            logger.log(Level.INFO, "101", ex);
            if(!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless","true"))){
            JOptionPane.showMessageDialog(null, "Parameter file " + parameterFile + " not found. inStrat will close.");
        }
            System.exit(0);
        }
        return p;
    }
        
    static String readFile(String path, Charset encoding) {
        byte[] encoded;
        try {
            File f = new File(path);
            if (f.exists()){
            encoded = Files.readAllBytes(Paths.get(path));

            return new String(encoded, encoding);
            }else{
            JOptionPane.showMessageDialog(null, "The license file does not exist. inStrat will close.");
            System.exit(0);
                return null;
            }
            
        } catch (IOException ex) {
            logger.log(Level.INFO, "101", ex);
            return null;
        }
    }
    
    
    
        public static boolean checkLicense() {
        try {
            digest = new byte[] {
            (byte)0x42, 
            (byte)0x2B, (byte)0xB1, (byte)0xBE, (byte)0xD9, (byte)0x04, (byte)0xE1, (byte)0xD1, (byte)0x96, 
            (byte)0x2E, (byte)0xF1, (byte)0x14, (byte)0x18, (byte)0x5C, (byte)0x8F, (byte)0x19, (byte)0xFF, 
            (byte)0x6A, (byte)0xFA, (byte)0x98, (byte)0x7D, (byte)0x1E, (byte)0xE9, (byte)0xCF, (byte)0x4C, 
            (byte)0x49, (byte)0xFF, (byte)0x63, (byte)0x72, (byte)0xE8, (byte)0x38, (byte)0xCE, 
            };
            File f = new File("key");
            Charset encoding = Charset.forName("ISO-8859-1");
            String licenseFileName = TradingUtil.readFile("key", encoding);
            lic = new License();
            lic.loadKeyRingFromResource("pubring.gpg", digest);
            lic.setLicenseEncodedFromFile("key");

            return checkValidity();
        } catch (Exception ex) {
            logger.log(Level.INFO, "101", ex);
        }
        return false;
    }
        
        public static boolean checkValidity() {
        boolean check = true;
        ArrayList <Validity>licenses=new ArrayList<>();
        for(int i=1;i<100;i++){
            if(lic.getFeature(String.valueOf(i))!=null){
            String feature=lic.getFeature(String.valueOf(i));           
            String[] in=feature.split(",");
            licenses.add(new Validity(in[0],in[1],in[2],in[3]));
            
        }else{
                break;
            }
        }
        if(licenses.size()>0){            
            for (BeanConnection c : Parameters.connection) {//for each connection string, check if it conforms to license
                    String products=c.getStrategy();
                    String product[]=products.split(":");                    
                    for(String p:product){ //for each product specified in connection file
                    //create validity
                    Validity temp=new Validity(c.getAccountName().substring(0, 1).equals("D")?"TRIAL":"PAID",c.getAccountName().toUpperCase(),p.toUpperCase(),DateUtil.getFormatedDate("yyyy-MM-dd", new Date().getTime()));
                    //check if the product is licensed
                    boolean status=validate(temp,licenses);
                    check=check & status;
                    }                
            }
            return check;
            }else{
            check=check & false;
        }
        /*
        String expiration = lic.getFeature("Expiry");
        String licensedStrategies = lic.getFeature("Strategies");
        Boolean realAccount = Boolean.parseBoolean(lic.getFeature("RealAccount"));
        String realAccountNames = lic.getFeature("RealAccountNames");
        HashMap<String,String>realAccountLicenses=new HashMap<>();
        if(!(realAccountNames.equals("null") || realAccountNames.equals(""))){
        String[] accounts=realAccountNames.split(",");
        for(String account: accounts){
            String[] acc=account.split("-");
            realAccountLicenses.put(acc[0], acc[1]);
        }
        }

        //check for product license
        if(Pattern.compile(Pattern.quote("inStrat"), Pattern.CASE_INSENSITIVE).matcher(products).find()){
            check=check && true;
        }else{
            check=check && false;
        }
        
        //check for real account trading license
        if (realAccount) {
            for (BeanConnection c : Parameters.connection) {
                //if real account, Trading
                if (c.getAccountName().substring(0, 1).equals("U") && Pattern.compile(Pattern.quote("Trading"), Pattern.CASE_INSENSITIVE).matcher(c.getPurpose()).find()) {
                    //check if strategy and account combination exists
                    String allowedStrategies=realAccountLicenses.get(c.getAccountName())+":"+"NONE";
                    if(allowedStrategies==null){//if allowed strategies is null, which should not be the case. It should alteast be NONE
                        check=check && false; //no strategies allowed for the account
                    }else{
                        //if strategy is setup for real account
                        if(Pattern.compile(Pattern.quote(c.getStrategy()), Pattern.CASE_INSENSITIVE).matcher(allowedStrategies).find()){
                            check=check && true;
                        }else{
                            check=check && false;
                        }
                    }
                    check = check && true;
                    //paper account or real account with no trading permission
                } else if (c.getAccountName().substring(0, 1).equals("D")||(c.getAccountName().substring(0, 1).equals("U")&& !Pattern.compile(Pattern.quote("Trading"), Pattern.CASE_INSENSITIVE).matcher(c.getPurpose()).find())){
                    check = check && true;
                }else{
                    check=check & false;
                }
            }
        } else {
            for (BeanConnection c : Parameters.connection) {
                if (c.getAccountName().substring(0, 1).equals("U") && Pattern.compile(Pattern.quote("Trading"), Pattern.CASE_INSENSITIVE).matcher(c.getPurpose()).find()) {
                    check = check && false;
                }
            }
        }
        //check for strategy license
        for (BeanConnection c : Parameters.connection) {
            String[] licensedStrategy = c.getStrategy().split(":");
            for (int i = 0; i < licensedStrategy.length; i++) {
                if (Pattern.compile(Pattern.quote(licensedStrategy[i]), Pattern.CASE_INSENSITIVE).matcher(licensedStrategies).find()||Pattern.compile(Pattern.quote(licensedStrategy[i]), Pattern.CASE_INSENSITIVE).matcher("none").find()) {
                    check = check && true;
                } else {
                    check = check && false;
                }
            }
        }
        //check for expiration date
        Date expirationDate = DateUtil.parseDate("yyyy-MM-dd", expiration);
        if (new Date().after(expirationDate)) {
            check = check && false;
        } else {
            check = check && true;
        }
*/
        return check;
    }
        
        private static boolean validate(Validity v,ArrayList<Validity> licenses){
            for(Validity license:licenses){
                if((license.type.equals(v.type) && license.product.equals(v.product)||Pattern.compile(Pattern.quote(v.product),Pattern.CASE_INSENSITIVE).matcher("none").find()) && (license.account.equals("ALL")||license.account.equals(v.account)) && DateUtil.parseDate("yyyy-MM-dd", license.expiry).after(DateUtil.parseDate("yyyy-MM-dd", v.expiry))){
                    return true;
                }
            }
            
            return false;
        }
        
        public static Date getAlgoDate(){
            if(MainAlgorithm.isUseForTrading()){
                return new Date();
            }else{
                if(MainAlgorithm.getAlgoDate()!=null){
                return MainAlgorithm.getAlgoDate();
                }else{
                    return new Date();
                    
                }
            }
        }
        
    public static ArrayList<BeanOHLC> getDailyBarsFromOneMinCandle(int lookback, String s) {
        ArrayList<BeanOHLC> output = new ArrayList();
        Connection connect;
        Statement statement = null;
        PreparedStatement preparedStatement;
        ResultSet rs;
        try {
            connect = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/histdata", "root", "spark123");
            //statement = connect.createStatement();
            String name = s;
            preparedStatement = connect.prepareStatement("select * from dharasymb where name=? order by DATE(date) DESC,date ASC LIMIT ?");
            preparedStatement.setString(1, name);
            preparedStatement.setInt(2, lookback * 375);
            rs = preparedStatement.executeQuery();
            //parse and create one minute bars
            Date priorDate = null;
            Long volume = 0L;
            Double open = 0D;
            Double close = 0D;
            Double high = Double.MIN_VALUE;
            Double low = Double.MAX_VALUE;
            System.out.println("Creating Daily Bar for Symbol:" + name);

            while (rs.next()) {

                priorDate = priorDate == null ? rs.getDate("date") : priorDate;
                //String name = rs.getString("name");
                Date date = rs.getDate("date");
                Date datetime = rs.getTimestamp("date");
                if ((date.compareTo(priorDate) != 0) && date.compareTo(DateUtil.addDays(new Date(), -lookback)) > 0) {
                    //new bar has started
                    BeanOHLC tempOHLC = new BeanOHLC(priorDate.getTime(), open, high, low, close, volume, EnumBarSize.DAILY);
                    output.add(new BeanOHLC(tempOHLC));
                    priorDate = date;
                    //String formattedDate = DateUtil.getFormatedDate("yyyyMMdd hh:mm:ss", datetime.getTime());

                    volume = rs.getLong("volume");
                    open = rs.getDouble("tickopen");
                    volume = rs.getLong("volume");
                    close = rs.getDouble("tickclose");
                    high = rs.getDouble("high");
                    low = rs.getDouble("low");
                    tempOHLC.setClose(close);
                    tempOHLC.setHigh(high);
                    tempOHLC.setLow(low);
                    tempOHLC.setOpen(open);
                    tempOHLC.setVolume(volume);

                } else {
                    open = open == 0D ? rs.getDouble("tickopen") : open;
                    volume = volume + rs.getLong("volume");
                    close = rs.getDouble("tickclose");
                    high = rs.getDouble("high") > high ? rs.getDouble("high") : high;
                    low = rs.getDouble("low") < low ? rs.getDouble("low") : low;
                }
            }
            rs.close();

        } catch (Exception e) {
            logger.log(Level.INFO,"101",e);
            //System.out.println(e.getMessage());
        }
        return output;
    }

    public static ArrayList<Double> generateSwings(ArrayList<BeanOHLC> symbol) {
        ArrayList<ArrayList<Integer>> swingHigh = new <ArrayList<Integer>> ArrayList();  //algo parameter 
        ArrayList<BeanOHLC> symbolOHLC = symbol;
        BeanOHLC priorOHLC = new BeanOHLC();
        ArrayList<Integer> swingHighSymbol = new <Integer>ArrayList();
        Integer lastSwingSet = 0; //1=>upswing, -1=>downswing, 0=> no swing as yet
        for (BeanOHLC b : symbolOHLC) {
            priorOHLC = priorOHLC.getPeriodicity() == null ? b : priorOHLC; //for first loop for symbol, set priorOHLC as it will be null
            if (b.getHigh() > priorOHLC.getHigh() && b.getLow() >= priorOHLC.getLow()) {
                swingHighSymbol.add(1);
                lastSwingSet = 1;
            } else if (b.getHigh() <= priorOHLC.getHigh() && b.getLow() < priorOHLC.getLow()) {
                swingHighSymbol.add(-1);
                lastSwingSet = -1;
            } else {
                if (lastSwingSet == 1) {
                    swingHighSymbol.add(1);// outside bar created a high swing or inside bar
                } else if (lastSwingSet == -1) {
                    swingHighSymbol.add(-1);// outside bar created a low swing or inside bar
                } else {
                    swingHighSymbol.add(0);
                }

            }
            priorOHLC = b;
        }
        lastSwingSet = 0;
        swingHigh.add(swingHighSymbol);

        Integer swingBegin = 0;
        //loop through the symbol and set swing levels
        ArrayList<Double> swingLevels = new ArrayList();
        for (int j = 0; j < swingHighSymbol.size(); j++) {
            if (swingHighSymbol.get(j) == 1) { //in upswing
                if (swingHighSymbol.get(j - 1) == 1) { //in continued upswing
                    swingBegin = swingBegin == 0 ? j : swingBegin;//should this be 0?swingBegin:j
                } else if (swingHighSymbol.get(j - 1) != 1) { //start of upswing
                    //handle the prior downswing values
                    if (swingBegin != 0) { //swingBegin will be 0 if we are entering upswing or downswing for first time
                        double tempLow = Double.MAX_VALUE;
                        for (int k = swingBegin; k < j; k++) {
                            //find minimum value of low between k and j-1, both inclusive
                            tempLow = tempLow < symbolOHLC.get(k).getLow() ? tempLow : symbolOHLC.get(k).getLow();
                        }
                        //set size of swingLevels equal to the swing start.
                        int tempSize = swingLevels.size();
                        for (int addrows = tempSize; addrows < swingBegin; addrows++) { //Loop1
                            swingLevels.add(0D);
                            //logger.log(Level.INFO, "Loop1. bar:{0},value:{1}", new Object[]{addrows, 0});
                        }
                        for (int k = swingBegin; k < j; k++) {//Loop2
                            //update swinglevels
                            swingLevels.add(tempLow);
                            //logger.log(Level.INFO, "Loop2. bar:{0},value:{1}", new Object[]{k, tempLow});
                        }
                        swingBegin = j;
                    }

                }
            } else if (swingHighSymbol.get(j) == -1) { //in downswing
                if (swingHighSymbol.get(j - 1) == -1) { //in continued downswing
                    swingBegin = swingBegin == 0 ? j : swingBegin;
                } else if (swingHighSymbol.get(j - 1) != -1) { //start of downswing
                    //handle the prior upswing values
                    if (swingBegin != 0) { //swingBegin will be 0 if we are entering upswing or downswing for first time
                        double tempHigh = Double.MIN_VALUE;
                        for (int k = swingBegin; k < j; k++) {
                            //find max value of high between k and j-1, both inclusive
                            tempHigh = tempHigh > symbolOHLC.get(k).getHigh() ? tempHigh : symbolOHLC.get(k).getHigh();
                        }
                        //bring swingLevels equal to the swing start.
                        int tempSize = swingLevels.size();
                        for (int addrows = tempSize; addrows < swingBegin; addrows++) {//Loop3
                            swingLevels.add(0D);
                            //logger.log(Level.INFO, "Loop3. bar:{0},value:{1}", new Object[]{addrows, 0});
                        }
                        for (int k = swingBegin; k < j; k++) {//Loop4
                            //update swinglevels
                            swingLevels.add(tempHigh);
                            //logger.log(Level.INFO, "Loop4. bar:{0},value:{1}", new Object[]{k, tempHigh});
                        }
                    }
                    swingBegin = j;
                }

            } else if (swingHighSymbol.get(j) == 0) { //Loop5
                //no upswing or downswing defined as yet. Set SwingLevel to zero.
                swingLevels.add(0D);
                //logger.log(Level.INFO, "Loop5. bar:{0},value:{1}", new Object[]{j, 0});
            }
            if (j == (swingHighSymbol.size() - 1) && swingHighSymbol.get(j) != 0) {
                //flush last swinglevels
                if (swingBegin != 0) { //swingBegin will be 0 if we are entering upswing or downswing for first time
                    if (swingHighSymbol.get(j) == 1) {
                        double tempHigh = Double.MIN_VALUE;
                        for (int k = swingBegin; k <= j; k++) {
                            //find max value of high between k and j-1, both inclusive
                            tempHigh = tempHigh > symbolOHLC.get(k).getHigh() ? tempHigh : symbolOHLC.get(k).getHigh();
                        }
                        for (int k = swingBegin; k <= j; k++) {//Loop6
                            //update swinglevels
                            swingLevels.add(tempHigh);
                            //logger.log(Level.INFO, "Loop6. bar:{0},value:{1}", new Object[]{k, tempHigh});
                        }
                    } else if (swingHighSymbol.get(j) == -1) {
                        double tempLow = Double.MAX_VALUE;
                        for (int k = swingBegin; k <= j; k++) {
                            //find max value of high between k and j-1, both inclusive
                            tempLow = tempLow < symbolOHLC.get(k).getHigh() ? tempLow : symbolOHLC.get(k).getLow();
                        }
                        for (int k = swingBegin; k <= j; k++) {//Loop7
                            //update swinglevels
                            swingLevels.add(tempLow);
                            //logger.log(Level.INFO, "Loop7. bar:{0},value:{1}", new Object[]{k, tempLow});
                        }
                    }
                }
            }
        }
        return swingLevels;
    }

    public static ArrayList<Integer> generateTrend(ArrayList<Double> swingLevels) {
        ArrayList<Double> swingLevelSymbol = swingLevels;
        ArrayList<Integer> swingLevelTrend = new ArrayList();
        swingLevelTrend.add(0);
        LimitedQueue<Double> highqueue = new LimitedQueue(3);
        LimitedQueue<Double> lowqueue = new LimitedQueue(3);
        for (int j = 1; j < swingLevelSymbol.size(); j++) {
            int inittrend = -100;
            if (swingLevelSymbol.get(j) > swingLevelSymbol.get(j - 1) && swingLevelSymbol.get(j - 1) != 0) {
                highqueue.add(swingLevelSymbol.get(j));
            } else if (swingLevelSymbol.get(j) < swingLevelSymbol.get(j - 1) && swingLevelSymbol.get(j - 1) != 0) {
                lowqueue.add(swingLevelSymbol.get(j));
            }

            //check if uptrend started
            if (swingLevels.get(j) - swingLevels.get(j - 1) > 0 && highqueue.size() >= 2 && lowqueue.size() >= 2) {
                if (highqueue.get(highqueue.size() - 1) > highqueue.get(highqueue.size() - 2) && lowqueue.get(lowqueue.size() - 1) > lowqueue.get(lowqueue.size() - 2)) {
                    inittrend = 1;
                }
            }

            //check if downntrend started
            if (swingLevels.get(j) - swingLevels.get(j - 1) < 0 && highqueue.size() >= 2 && lowqueue.size() >= 2) {
                if (highqueue.get(highqueue.size() - 1) < highqueue.get(highqueue.size() - 2) && lowqueue.get(lowqueue.size() - 1) < lowqueue.get(lowqueue.size() - 2)) {
                    inittrend = -1;
                }
            }
            //check if no trend started
            if (highqueue.size() >= 2 && lowqueue.size() >= 2) {
                if (highqueue.get(highqueue.size() - 1) > highqueue.get(highqueue.size() - 2) && lowqueue.get(lowqueue.size() - 1) < lowqueue.get(lowqueue.size() - 2)) {
                    inittrend = 0;
                } else if (highqueue.get(highqueue.size() - 1) < highqueue.get(highqueue.size() - 2) && lowqueue.get(lowqueue.size() - 1) > lowqueue.get(lowqueue.size() - 2)) {
                    inittrend = 0;
                }
            }
            //update trend
            inittrend = inittrend == -100 ? swingLevelTrend.get(j - 1) : inittrend;
            swingLevelTrend.add(inittrend);
        }
        return swingLevelTrend;
    }

    public static void writeToFile(String filename, String content) {
        try {
            File dir = new File("logs");
            File file = new File(dir, filename);

            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }


            //true = append file
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");
            String dateString = dateFormatter.format(new java.util.Date());
            String timeString = timeFormatter.format(new java.util.Date());
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(dateString + "," + timeString + "," + content + newline);
            bufferWritter.close();
        } catch (IOException ex) {
        }
    }
    
    public static void writeToFile(String filename, String content, long time) {
        try {
            File dir = new File("logs");
            File file = new File(dir, filename);

            //if file doesnt exists, then create it
            if (!file.exists()) {
                file.createNewFile();
            }


            //true = append file
            SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyyMMdd");
            SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm:ss");
            String dateString = dateFormatter.format(time);
            String timeString = timeFormatter.format(time);
            FileWriter fileWritter = new FileWriter(file, true);
            BufferedWriter bufferWritter = new BufferedWriter(fileWritter);
            bufferWritter.write(dateString + "," + timeString + "," + content + newline);
            bufferWritter.close();
        } catch (IOException ex) {
        }
    }
    

    public static boolean isDouble(String value) {
        //String decimalPattern = "([0-9]*)\\.([0-9]*)";  
        //return Pattern.matches(decimalPattern, value)||Pattern.matches("\\d*", value);
        return value.matches("-?\\d+(\\.\\d+)?");
    }

    public static boolean isValidEmailAddress(String email) {
   boolean result = true;
   try {
      InternetAddress emailAddr = new InternetAddress(email);
      emailAddr.validate();
   } catch (Exception ex) {
      result = false;
   }
   return result;
}
    public static String populateMACID() {
        InetAddress ip;
        StringBuilder sb = new StringBuilder();
        try {
            System.out.println("Trying to get the local host address");
            ip = InetAddress.getLocalHost();
            System.out.println("Current IP address : " + ip.getHostAddress());
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            try {
                byte[] mac = network.getHardwareAddress();
                if (mac != null) {
                    for (int i = 0; i < mac.length; i++) {
                        sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
                    }
                    //System.out.println(sb.toString());
                }
            } catch (Exception e) {
                System.out.println("Error getting mac id");
            }

            System.out.print("Current MAC address : ");

        } catch (UnknownHostException | SocketException e) {
            logger.log(Level.INFO, "101", e);
        }
        return sb.toString();
    }
    /*
     public static String encrypt(String source, String password){
     String encryptedString="";
     try {
     final String utf8 = "utf-8";
     byte[] keyBytes;
     keyBytes = Arrays.copyOf(password.getBytes(utf8), 24);
     SecretKey key = new SecretKeySpec(keyBytes, "DESede");
     // Your vector must be 8 bytes long
     String vector = "@Spark13";
     IvParameterSpec iv = new IvParameterSpec(vector.getBytes(utf8));
     // Make an encrypter
     Cipher encrypt = Cipher.getInstance("DESede/CBC/PKCS5Padding");
     encrypt.init(Cipher.ENCRYPT_MODE, key, iv);
     byte[] sourceInBytes=source.getBytes(utf8);
     encryptedString=new String(encrypt.doFinal(sourceInBytes),utf8);
     } catch (UnsupportedEncodingException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (NoSuchAlgorithmException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (NoSuchPaddingException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (InvalidKeyException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (InvalidAlgorithmParameterException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (IllegalBlockSizeException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (BadPaddingException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     }
     return encryptedString;            
        
     }
    
     public static String decrypt(String source, String password){
     String decryptedString="";
     try {
     final String utf8 = "utf-8";
     byte[] keyBytes;
     keyBytes = Arrays.copyOf(password.getBytes(utf8), 24);
     SecretKey key = new SecretKeySpec(keyBytes, "DESede");
     // Your vector must be 8 bytes long
     String vector = "@Spark13";
     IvParameterSpec iv = new IvParameterSpec(vector.getBytes(utf8));
     // Make an encrypter
     Cipher decrypt = Cipher.getInstance("DESede/CBC/PKCS5Padding");
     decrypt.init(Cipher.DECRYPT_MODE, key, iv);
     byte[] sourceInBytes=source.getBytes(utf8);
     decryptedString = new String(decrypt.doFinal(sourceInBytes),utf8);
     } catch (UnsupportedEncodingException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (NoSuchAlgorithmException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (NoSuchPaddingException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (InvalidKeyException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (InvalidAlgorithmParameterException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (IllegalBlockSizeException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     } catch (BadPaddingException ex) {
     Logger.getLogger(TradingUtil.class.getName()).log(Level.SEVERE, null, ex);
     }
     return decryptedString;  
        
     }
     */

    public static String getPublicIPAddress() {
        String ip = "";
        try {
            URL whatismyip = new URL("http://checkip.amazonaws.com/");
            BufferedReader in = new BufferedReader(new InputStreamReader(whatismyip.openStream()));
            ip = in.readLine(); //you get the IP as a String}

        } catch (Exception e) {
        }
        return ip;
    }
/*
    public static Date getExpiryDate(String macID, String accounts, boolean realaccount) {

        Date expiryDate = new Date();
        expiryDate = DateUtil.addDays(expiryDate, -10);
        try {
            String testurl = String.format("http://www.incurrency.com:8888/license");
            Document doc = Jsoup.connect(testurl).timeout(0).data("macid", macID, "accounts", accounts).post();
            String input = URLDecoder.decode(doc.getElementsByTag("Body").get(0).text(), "UTF-8");
            String[] expiries = input.split(",");
            //String[] expiries=new String[namevalue.length];
            //for(int i=0;i<namevalue.length;i++){
            //    expiries=namevalue[i].split("=");
            //}
            if (macID.compareTo("") != 0 && !realaccount && expiries[0].compareTo("") != 0) {
                //macID is not empty. Registration date = expiries[0]
                expiryDate = new SimpleDateFormat("dd/MM/yyyy").parse(expiries[0]);;
            } else {
                //find minimum of expiries and set it to expiry date
                List<Date> list = new ArrayList<>();
                for (int i = 1; i < expiries.length; i++) {
                    Date temp = new SimpleDateFormat("dd/MM/yyyy").parse(expiries[i]);
                    list.add(temp);
                }
                Collections.sort(list);
                expiryDate = list.get(0);
            }

        } catch (IOException | ParseException ex) {
            logger.log(Level.SEVERE, null, ex);
        }

        return expiryDate;
    }
*/
    public static boolean isValidTime(String time) {
        //http://www.vogella.com/tutorials/JavaRegularExpressions/article.html
        //http://stackoverflow.com/questions/884848/regular-expression-to-validate-valid-time
        if (time == null) {
            return false;
        }
        String pattern = "([01]?[0-9]|2[0-3]):[0-5][0-9]:[0-5][0-9]";
        if (time.matches(pattern)) {
            return true;
        } else {
            return false;
        }
    }

    public static boolean isInteger(String str) {
	if (str == null) {
		return false;
	}
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
    
    public static boolean stringContainsItemFromList(String inputString, String[] items) {
        for (int i = 0; i < items.length; i++) {
            if (inputString.contains(items[i])) {
                return true;
            }
        }
        return false;
    }
    
    public static Double[] convertArrayToDouble(final String inputArray[]){
        Double[] out;
        out=new ArrayList<Double>() {{for (String s : inputArray) add(new Double(s.trim()));}}.toArray(new Double[inputArray.length]);
        return out;
    }
    
        public static Integer[] convertArrayToInteger(final String inputArray[]){
        Integer[] out;
        out=new ArrayList<Integer>() {{for (String s : inputArray) add(new Integer(s.trim()));}}.toArray(new Integer[inputArray.length]);
        return out;
    }

    public static int getIDFromSymbol(String symbol, String type, String expiry, String right, String option) {
        for (BeanSymbol symb : Parameters.symbol) {
            String s = symb.getSymbol() == null ? "" : symb.getSymbol();
            String t = symb.getType() == null ? "" : symb.getType();
            String e = symb.getExpiry() == null ? "" : symb.getExpiry();
            String r = symb.getRight() == null ? "" : symb.getRight();
            String o = symb.getOption() == null ? "" : symb.getOption();
            
            String si=symbol== null ? "" : symbol;
            String ti = type== null ? "" : type;
            String ei = expiry == null ? "" : expiry;
            String ri = right == null ? "" : right;
            String oi = option== null ? "" : option;
            if (s.compareTo(si) == 0 && t.compareTo(ti) == 0 && e.compareTo(ei)==0 
                    && r.compareTo(ri) == 0 && o.compareTo(oi) == 0) {
                return symb.getSerialno() - 1;
            }
        }
        return -1;
    }
    
        public static int getIDFromDisplayName(String symbol, String type, String expiry, String right, String option) {
        for (BeanSymbol symb : Parameters.symbol) {
            String s = symb.getDisplayname() == null ? "" : symb.getDisplayname();
            String t = symb.getType() == null ? "" : symb.getType();
            String e = symb.getExpiry() == null ? "" : symb.getExpiry();
            String r = symb.getRight() == null ? "" : symb.getRight();
            String o = symb.getOption() == null ? "" : symb.getOption();
            
            String si=symbol== null ? "" : symbol;
            String ti = type== null ? "" : type;
            String ei = expiry == null ? "" : expiry;
            String ri = right == null ? "" : right;
            String oi = option== null ? "" : option;
            if (s.compareTo(si) == 0 && t.compareTo(ti) == 0 && e.compareTo(ei)==0
                    && r.compareTo(ri) == 0 && o.compareTo(oi) == 0) {
                return symb.getSerialno() - 1;
            }
        }
        return -1;
    }
    
    public static int getIDFromDisplayName(String displayName) {
        for (BeanSymbol symb : Parameters.symbol) {
if(symb.getDisplayname().equals(displayName)){
            return symb.getSerialno() - 1;
            }
        }
        return -1;
    }
    
    public static int getIDFromComboLongName(String comboLongName) {
        for (BeanSymbol symb : Parameters.symbol) {
if(symb.getSymbol().equals(comboLongName) && symb.getType().equals("COMBO")){
            return symb.getSerialno() - 1;
            }
        }
        return -1;
    }
    
    public static int getEntryIDFromDisplayName(Trade tr,ArrayList<BeanSymbol> symbolList) {
        for (BeanSymbol symb : symbolList) {
            if(symb.getDisplayname().equals(tr.getEntrySymbol())){
                return symb.getSerialno() - 1;
            }            
        }
        return -1;
    }

        public static int getExitIDFromSymbol(Trade tr) {
        for (BeanSymbol symb : Parameters.symbol) {
            if(symb.getDisplayname().equals(tr.getExitSymbol())){
                return symb.getSerialno() - 1;
            }            
        }
        return -1;
    }
        
    public static int getFutureIDFromSymbol(int id, String expiry) {
        String s = Parameters.symbol.get(id).getSymbol();
        String t = "FUT";
        String e = expiry;
        String r = "";
        String o = "";
        return getIDFromSymbol(s, t, e, r, o);
    }

    public static int getIDFromFuture(int futureID) {
        String s = Parameters.symbol.get(futureID).getSymbol();
        String t = "STK";
        String e = "";
        String r = "";
        String o = "";
        return getIDFromSymbol(s, t, e, r, o);
    }

    public static String padRight(String s, int n) {
     return String.format("%1$-" + n + "s", s);  
}
    
    public static void updateMTM(Trade tr, String timeZone) {
        String today = DateUtil.getFormatedDate("yyyy-MM-dd", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        if (tr.getExitPrice() == 0D) { //set the MTM
            int id = TradingUtil.getEntryIDFromDisplayName(tr,Parameters.symbol);
            if (id >= 0) {
                if (tr.getMtmToday() != 0) {
                    //continuing position. move mtm to yesterday
                    tr.setMtmYesterday(tr.getMtmToday());
                    tr.setYesterdayDate(tr.getTodayDate());
                }
                
                    tr.setMtmToday(Parameters.symbol.get(id).getLastPrice());
                    tr.setTodayDate(today);
                
            }
            //change month end mtm.
            if (tr.getYesterdayDate().length() > 9 && !tr.getYesterdayDate().substring(5, 7).equals(tr.getTodayDate().substring(5, 7))) {
                //month has changed
                tr.setMtmPriorMonth(tr.getMtmYesterday());
            }

        }else{
            if (tr.getMtmToday() != 0) {
                    //continuing position. move mtm to yesterday
                    tr.setMtmYesterday(tr.getMtmToday());
                    tr.setYesterdayDate(tr.getTodayDate());
                }
                        //change month end mtm.
            if (tr.getYesterdayDate().length() > 9 && !tr.getYesterdayDate().substring(5, 7).equals(tr.getTodayDate().substring(5, 7))) {
                //month has changed
                tr.setMtmPriorMonth(tr.getMtmYesterday());
            }
        }
        }
    


    public static double[] applyBrokerage(HashMap<OrderLink, Trade> trades, ArrayList<BrokerageRate> brokerage, double pointValue, String fileName, String timeZone, double startingEquity, String accountName, String equityFileName) {
        double[] profitGrid = new double[10];
        ArrayList<Double> dailyEquity = new ArrayList();
        ArrayList<Date> tradeDate = new ArrayList();
        try {
            /*
             * 0 => gross profit for day
             * 1 => Brokerage for day
             * 2 => Net Profit for day
             * 3 => MTD profit
             * 4 => YTD profit
             */

            /* 5=> Max Drawdown
             * 6=> Max Drawdown Days
             * 7=> Avg Drawdown days
             * 8 => Sharpe ratio
             * 9 => Number of days in the sample
             */
            String today = DateUtil.getFormatedDate("yyyy-MM-dd", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
            int tradesToday = 0; //Holds the number of trades done today
            for (Map.Entry<OrderLink, Trade> trade : trades.entrySet()) {
                Trade tr = trade.getValue();
                TradingUtil.updateMTM(tr, timeZone);
                if ((tr.getEntryTime().contains(today) && tr.getAccountName().equals(accountName) && !Parameters.symbol.get(tr.getEntrySymbolID()).getType().equals("COMBO"))
                        ){
                    tradesToday = tradesToday + 1;
                }
                if ((!tr.getExitTime().equals("") && tr.getExitTime().contains(today) && !Parameters.symbol.get(tr.getEntrySymbolID()).getType().equals("COMBO"))
                    ){
                    tradesToday = tradesToday + 1;
                }
                if((tr.getEntryTime().contains(today) && tr.getAccountName().equals(accountName) && tr.getAccountName().equals("Order") && Parameters.symbol.get(tr.getEntrySymbolID()).getType().equals("COMBO"))
                        ){
                    tradesToday=tradesToday+Parameters.symbol.get(tr.getEntrySymbolID()).getCombo().size();
                }
                if( (!tr.getExitTime().equals("") && tr.getExitTime().contains(today) && tr.getAccountName().equals("Order") && Parameters.symbol.get(tr.getEntrySymbolID()).getType().equals("COMBO"))
                     ){
                    tradesToday=tradesToday+Parameters.symbol.get(tr.getEntrySymbolID()).getCombo().size();
                }
            }

            for (Map.Entry<OrderLink, Trade> trade : trades.entrySet()) {
                Trade t = trade.getValue();
                if (t.getAccountName().equals(accountName)) {
                    ArrayList<Double> tempBrokerage=calculateBrokerage(trades, t,brokerage, accountName,tradesToday);
                    t.setEntryBrokerage(tempBrokerage.get(0));
                    t.setExitBrokerage(tempBrokerage.get(1));
                }
            }
            //calculate today's profit
            for (Map.Entry<OrderLink, Trade> trade : trades.entrySet()) {
                Trade t = trade.getValue();
                if (t.getAccountName().equals(accountName) && !Parameters.symbol.get(t.getEntrySymbolID()).getType().equals("COMBO")||(t.getAccountName().equals(accountName) && t.getAccountName().equals("Order"))) {
                    double exitPrice = t.getExitPrice() == 0D ? t.getMtmToday() : t.getExitPrice();
                    double entryPrice = t.getMtmYesterday() == 0D ? t.getEntryPrice() : t.getMtmYesterday();
                    double pnl = 0;
                    boolean considerEntryBrokerage=false;
                    boolean considerExitBrokerage=false;
                    if (exitPrice != 0D) {
                        pnl = t.getEntrySide() == EnumOrderSide.BUY ? t.getEntrySize() * pointValue * (exitPrice - entryPrice) : -t.getEntrySize() * pointValue * (exitPrice - entryPrice);
                    }
                    profitGrid[0] = profitGrid[0] + pnl;
                    //Which brokerage amounts do we pass into today's pnl?
                    
                    //Same day trades
                    if(t.getEntryTime().contains(today)){
                        considerEntryBrokerage=true;
                        considerExitBrokerage=true;
                    }else if(t.getExitTime().contains(today)){
                        considerExitBrokerage=true;
                    }                   
                    profitGrid[1] = profitGrid[1] - (considerEntryBrokerage?t.getEntryBrokerage():0) - (considerExitBrokerage?t.getExitBrokerage():0);
                    } 
                }
            
            profitGrid[2] = profitGrid[0] + profitGrid[1];

            String monthBeginning = today.substring(0, 7);
            monthBeginning = monthBeginning + "-01";
            //calculate month p&l/ brokerage
            double monthpnl = 0;
            File dir = new File("logs");
            File file = new File(dir, fileName);
                ArrayList<Trade> allTrades = new ArrayList<>();
                if (file.exists()) {
                //logger.log(Level.INFO, "file loaded: {0}", new Object[]{file});
//                ArrayList<Trade> allTrades = Parameters.readTradesWithCsvBeanReader(file.getCanonicalPath());
                new Trade().reader(file.getCanonicalPath(), allTrades);
                }
                if(allTrades.size()>0||trades.size()>0){
                HashMap<OrderLink, Trade> combinedTrades = new HashMap<>();                
                for (Trade trade : allTrades) {//add earlier trades
                combinedTrades.put(new OrderLink(trade.getEntryID(),trade.getEntryOrderID(),trade.getAccountName()),trade);
            }
                combinedTrades.putAll(trades);
                allTrades.clear();
                for(Trade t:combinedTrades.values()){
                    allTrades.add(t);
                }
                Collections.sort(allTrades, new TradesCompare());
                for (Trade t : allTrades) { //get pnl of trades that have closed
                    if (t.getAccountName().equals(accountName) && !Parameters.symbol.get(t.getEntrySymbolID()).getType().equals("COMBO")||(t.getAccountName().equals(accountName) && t.getAccountName().equals("Order"))) {
                        //logger.log(Level.INFO, "Month Beginning: {0}, Exit Time: {1}", new Object[]{monthBeginning, t.getExitTime().substring(0, 10)});
                        String exitDate=t.getExitTime().equals("")?t.getTodayDate():t.getExitTime();
                        if (DateUtil.parseDate("yyyy-MM-dd", exitDate.substring(0, 10)).compareTo(DateUtil.parseDate("yyyy-MM-dd", monthBeginning)) >= 0) {
                            double exitPrice=t.getExitPrice()==0D?t.getMtmToday():t.getExitPrice();
                            double pnl = t.getEntrySide() == EnumOrderSide.BUY ? t.getEntrySize() * pointValue * (exitPrice - t.getEntryPrice()) : -t.getEntrySize() * pointValue * (exitPrice - t.getEntryPrice());
                            monthpnl = monthpnl + pnl - t.getEntryBrokerage() - t.getExitBrokerage();
                            //logger.log(Level.INFO, "month pnl: {0}, row pnl: {1}", new Object[]{monthpnl, pnl});
                        }
                    }
                }
                profitGrid[3]=monthpnl;
                //logger.log(Level.INFO, "Month P&L: {0}", profitGrid[3]);
                //calculate yearly pnl
                double ytdPNL = 0;
                double tradePNL = 0;
                double dayPNL = 0;
                Date entryDate = null;
                for (Trade t : allTrades) {//get ytd pnl for closed trades till yesterday
                    if (t.getAccountName().equals(accountName) && !Parameters.symbol.get(t.getEntrySymbolID()).getType().equals("COMBO")||(t.getAccountName().equals(accountName) && t.getAccountName().equals("Order"))){
                        if (entryDate == null) { //set entry date to the first date that trades/orders were executed
                            entryDate = DateUtil.parseDate("yyyy-MM-dd", t.getEntryTime().substring(0, 10));
                        }
                        if (DateUtil.parseDate("yyyy-MM-dd", t.getEntryTime().substring(0, 10)).compareTo(entryDate) != 0) {
                            //new date has started. Log values for last date
                            dailyEquity.add(ytdPNL + startingEquity); //take snapshot of ytdPNL and add to daily Equity
                            tradeDate.add(entryDate);
                            entryDate = DateUtil.parseDate("yyyy-MM-dd", t.getEntryTime().substring(0, 10));
                            dayPNL = 0; //reset dayPNL to zero for next day
                        }
                        double exitPrice=t.getExitPrice()==0D?t.getMtmToday():t.getExitPrice();
                        tradePNL = t.getEntrySide() == EnumOrderSide.BUY ? t.getEntrySize() * pointValue * (exitPrice - t.getEntryPrice()) : -t.getEntrySize() * pointValue * (exitPrice - t.getEntryPrice());
                        dayPNL = dayPNL + tradePNL;
                        //ytdPNL=0;
                        ytdPNL = ytdPNL + tradePNL - t.getEntryBrokerage() - t.getExitBrokerage();
                    }
                }
                //Add equity and date from the last itenration in loop above
                //dailyEquity.add(startingEquity + ytdPNL);
                //tradeDate.add(entryDate);
                profitGrid[4]=ytdPNL;

                 for (Trade t : trades.values()) {
                   if(t.getExitPrice()!=0){
                        t.setMtmPriorMonth(0D);
                        t.setMtmToday(0D);
                        t.setMtmYesterday(0D);
                        t.setTodayDate("");
                        t.setYesterdayDate("");                        
                   }  
                 }
                                   
                //Add equity and date for today's trades
                dailyEquity.add(startingEquity + profitGrid[4]);
                tradeDate.add(DateUtil.parseDate("yyyy-MM-dd", today));
                profitGrid[5] = maxDrawDownPercentage(dailyEquity);
                profitGrid[6] = drawdownDays(dailyEquity)[0];
                profitGrid[7] = drawdownDays(dailyEquity)[1];
                profitGrid[8] = sharpeRatio(dailyEquity);
                profitGrid[9] = dailyEquity.size();
                int i = 0;
                for (Double d : dailyEquity) {
                    if (tradeDate.get(i) != null) {
                        //logger.log(Level.INFO, "Writing equity.csv. TradeDate:{0},Equity:{1},fileName:{2},AccountName:{3}", new Object[]{DateUtil.getFormatedDate("yyyy-MM-dd", tradeDate.get(i).getTime()), d, fileName, accountName});
                        TradingUtil.writeToFile(equityFileName, DateUtil.getFormatedDate("yyyy-MM-dd", tradeDate.get(i).getTime()) + "," + d + "," + fileName + "," + accountName);
                        i = i + 1;
                    }else{
                        
                    }
                }
            }                 
                if(allTrades.isEmpty() && !file.exists()) {
                profitGrid[3] = 0;
                profitGrid[4] = 0;
                profitGrid[5] = 0;
                profitGrid[6] = 0;
                profitGrid[7] = 0;
                profitGrid[8] = 0;
                profitGrid[9] = 0;
            }
        } catch (Exception ex) {
            logger.log(Level.INFO, "101", ex);
        }
        return profitGrid;
    }

    public static ArrayList<Double> calculateBrokerage(HashMap<OrderLink, Trade> trades, Trade t,ArrayList<BrokerageRate> brokerage,String accountName,int tradesToday){
        ArrayList<Double> brokerageCost=new ArrayList<>();
        brokerageCost.add(0D);
        brokerageCost.add(0D);
        int internalorderid=t.getEntryID();
        if(Parameters.symbol.get(t.getEntrySymbolID()).getType().equals("COMBO")){
            if(!t.getAccountName().equals("Order")){
            for(Trade tr:trades.values()){
                if(tr.getEntryID()==internalorderid && !Parameters.symbol.get(tr.getEntrySymbolID()).getType().equals("COMBO")){//only calculate brokerage for child legs
                    ArrayList<Double> singleLegCost=calculateSingleLegBrokerage(tr,brokerage,tradesToday);
                    double earlierEntryCost=brokerageCost.get(0);
                    double earlierExitCost=brokerageCost.get(1);
                    brokerageCost.set(0, earlierEntryCost+singleLegCost.get(0));
                    brokerageCost.set(1, earlierExitCost+singleLegCost.get(1));
                }
            }                
            }else{
                //get legs for orders
                int parentid=TradingUtil.getEntryIDFromDisplayName(t,Parameters.symbol);
                for(Map.Entry<BeanSymbol,Integer>entry:Parameters.symbol.get(parentid).getCombo().entrySet()){
                    int childid=entry.getKey().getSerialno()-1;
                    EnumOrderSide childEntrySide=EnumOrderSide.UNDEFINED;
                    EnumOrderSide childExitSide=EnumOrderSide.UNDEFINED;
                    switch(t.getEntrySide()){
                        case BUY:
                            childEntrySide=entry.getValue()>0?EnumOrderSide.BUY:EnumOrderSide.SHORT;
                            childExitSide=entry.getValue()>0?EnumOrderSide.SELL:EnumOrderSide.COVER;
                            break;
                        case SELL:
                            childEntrySide=entry.getValue()>0?EnumOrderSide.SELL:EnumOrderSide.COVER;
                            childExitSide=entry.getValue()>0?EnumOrderSide.COVER:EnumOrderSide.SELL;
                            break;
                        case SHORT:
                            childEntrySide=entry.getValue()>0?EnumOrderSide.SHORT:EnumOrderSide.BUY;
                            childExitSide=entry.getValue()>0?EnumOrderSide.BUY:EnumOrderSide.SHORT;
                            break;
                        case COVER:
                            childEntrySide=entry.getValue()>0?EnumOrderSide.COVER:EnumOrderSide.SELL;
                            childExitSide=entry.getValue()>0?EnumOrderSide.SELL:EnumOrderSide.COVER;
                            break;
                        default:
                            break;                           
                    }
                    Trade entryTrade=new Trade(childid,parentid,EnumOrderReason.REGULARENTRY,childEntrySide,Parameters.symbol.get(childid).getLastPrice(),Math.abs(entry.getValue()*t.getEntrySize()),t.getEntryID(),0,"","Order");
                    if(!t.getExitSide().equals(EnumOrderSide.UNDEFINED)){
                    entryTrade.updateExit(childid,EnumOrderReason.REGULAREXIT, childExitSide, Parameters.symbol.get(childid).getLastPrice(), Math.abs(entry.getValue()*t.getExitSize()), t.getExitID(), 0, "", "Order");                        
                    }
                    ArrayList<Double> singleLegCost=calculateSingleLegBrokerage(entryTrade,brokerage,tradesToday);
                    double earlierEntryCost=brokerageCost.get(0);
                    double earlierExitCost=brokerageCost.get(1);
                    brokerageCost.set(0, earlierEntryCost+singleLegCost.get(0));
                    brokerageCost.set(1, earlierExitCost+singleLegCost.get(1)); 
                }
            }
            
        }else{
            brokerageCost=calculateSingleLegBrokerage(t,brokerage,tradesToday);                   
        } 
         TradingUtil.writeToFile("brokeragedetails.txt", t.getEntrySymbol()+","+brokerageCost.get(0)+","+brokerageCost.get(1));
        return brokerageCost;        
    }
    
    private static ArrayList<Double> calculateSingleLegBrokerage(Trade t, ArrayList<BrokerageRate> brokerage, int tradesToday) {
        ArrayList<Double> brokerageCost = new ArrayList<>();
        double entryCost = 0;
        double exitCost = 0;        
        //calculate entry costs
        for (BrokerageRate b : brokerage) {
            switch (b.primaryRule) {
                case VALUE:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getEntrySide() == EnumOrderSide.BUY || t.getEntrySide() == EnumOrderSide.COVER))) {
                        entryCost = entryCost + (t.getEntryPrice() * t.getEntrySize() * b.primaryRate / 100) + (t.getEntryPrice() * t.getEntrySize() * b.primaryRate * b.secondaryRate / 10000);
                    }
                    break;
                case SIZE:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getEntrySide() == EnumOrderSide.BUY || t.getEntrySide() == EnumOrderSide.COVER))) {
                        entryCost = entryCost + t.getEntrySize() * b.primaryRate + t.getEntrySize() * b.primaryRate * b.secondaryRate;
                    }
                    break;
                case FLAT:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getEntrySide() == EnumOrderSide.BUY || t.getEntrySide() == EnumOrderSide.COVER))) {
                        entryCost = entryCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                    }
                    break;
                case DISTRIBUTE:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getEntrySide() == EnumOrderSide.BUY || t.getEntrySide() == EnumOrderSide.COVER))) {
                        if (tradesToday > 0) {
                            entryCost = entryCost + b.primaryRate / tradesToday + (b.primaryRate / tradesToday) * b.secondaryRate;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        //calculate exit costs
        for (BrokerageRate b : brokerage) {
            switch (b.primaryRule) {
                case VALUE:
                    if (!t.getExitTime().equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getExitSide() == EnumOrderSide.BUY || t.getExitSide() == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && t.getExitTime().contains(t.getEntryTime().substring(0, 10))))) {
                        exitCost = exitCost + (t.getExitPrice() * t.getEntrySize() * b.primaryRate / 100) + (t.getEntryPrice() * t.getEntrySize() * b.primaryRate * b.secondaryRate / 10000);
                    }
                    break;
                case SIZE:
                    if (!t.getExitTime().equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getExitSide() == EnumOrderSide.BUY || t.getExitSide() == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && t.getExitTime().contains(t.getEntryTime().substring(0, 10))))) {
                        exitCost = exitCost + t.getEntrySize() * b.primaryRate + t.getEntrySize() * b.primaryRate * b.secondaryRate;
                    }
                    break;
                case FLAT:
                    if (!t.getExitTime().equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getExitSide() == EnumOrderSide.BUY || t.getExitSide() == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && t.getExitTime().contains(t.getEntryTime().substring(0, 10))))) {
                        exitCost = exitCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                    }
                    break;
                case DISTRIBUTE:
                    if (!t.getExitTime().equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (t.getExitSide() == EnumOrderSide.BUY || t.getExitSide() == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && t.getExitTime().contains(t.getEntryTime().substring(0, 10))))) {
                        exitCost = exitCost + b.primaryRate / tradesToday + (b.primaryRate / tradesToday) * b.secondaryRate;
                    }
                    break;
                default:
                    break;
            }
        }
        entryCost=t.getEntryBrokerage()==0D?entryCost:t.getEntryBrokerage();
        brokerageCost.add(entryCost);
        brokerageCost.add(exitCost);
        return brokerageCost;

    }
    
    public static double maxDrawDownPercentage(ArrayList<Double> dailyEquity) {
        double maxDrawDownAbsolute = 0;
        double maxDrawDownPercentage = 0;
        double maxEquity = Double.MIN_VALUE;
        for (Double equity : dailyEquity) {
            maxEquity = Math.max(maxEquity, equity); // this gives the high watermark
            maxDrawDownAbsolute = Math.max(maxDrawDownAbsolute, maxEquity - equity); //absolute amoutn
            double precentagedrawdown = 0;
            if (maxEquity != 0) {
                precentagedrawdown = Math.abs((maxEquity - equity) / equity);
            }
            maxDrawDownPercentage = Math.max(maxDrawDownPercentage, precentagedrawdown);
        }
        return maxDrawDownPercentage * 100;
    }

    public static double[] drawdownDays(ArrayList<Double> dailyEquity) {
        double[] days = new double[2];
        double maxEquity = Double.MIN_VALUE;
        int numDrawDownDays = 0;
        ArrayList<Integer> drawdownDays = new ArrayList();
        int rowcounter = -1;
        for (Double equity : dailyEquity) {
            rowcounter = rowcounter + 1;
            System.out.println("Equity:" + equity + ",MaxEquity:" + maxEquity);
            if (equity < Math.max(maxEquity, equity)) {
                numDrawDownDays = numDrawDownDays + 1;
            } else {
                maxEquity = Math.max(maxEquity, equity);
                drawdownDays.add(numDrawDownDays);
                numDrawDownDays = 0;
            }
            drawdownDays.add(numDrawDownDays); //add the last value of drawdown day from loop above
        }
        int maxDrawDownDays = 0;
        double avgDrawDownDays = 0;
        for (Integer i : drawdownDays) {
            System.out.println("drawdown days:" + i);
            maxDrawDownDays = maxDrawDownDays < i ? i : maxDrawDownDays;
            avgDrawDownDays = avgDrawDownDays + i;
        }
        days[0] = maxDrawDownDays;
        days[1] = avgDrawDownDays / drawdownDays.size();
        return days;
    }

    public static double sharpeRatio(ArrayList<Double> dailyEquity) {
        ArrayList<Double> returns = new ArrayList();
        int rowcounter = 0;
        for (Double daypnl : dailyEquity) {
            if (rowcounter >= 1) {
                returns.add(Math.log(dailyEquity.get(rowcounter) / dailyEquity.get(rowcounter - 1)));
            }
            rowcounter = rowcounter + 1;
        }
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (Double r : returns) {
            stats.addValue(r);
        }
        double mean = stats.getMean();
        double std = stats.getStandardDeviation();
        return (mean) / std * Math.sqrt(260);
    }

    public static BrokerageRate parseBrokerageString(String brokerage, String type) {

        BrokerageRate brokerageRate = new BrokerageRate();
        brokerageRate.type = type;
        String[] input = brokerage.split(",");
        switch (input.length) {
            case 2:
                brokerageRate.primaryRate = Double.parseDouble(input[0]);
                brokerageRate.primaryRule = EnumPrimaryApplication.valueOf(input[1].toUpperCase());
                break;
            case 3:
                brokerageRate.primaryRate = Double.parseDouble(input[0]);
                brokerageRate.primaryRule = EnumPrimaryApplication.valueOf(input[1].toUpperCase());
                brokerageRate.secondaryRate = Double.parseDouble(input[2]);
                break;
            case 4:
                brokerageRate.primaryRate = Double.parseDouble(input[0]);
                brokerageRate.primaryRule = EnumPrimaryApplication.valueOf(input[1].toUpperCase());
                brokerageRate.secondaryRate = Double.parseDouble(input[2]);
                brokerageRate.secondaryRule = EnumSecondaryApplication.valueOf(input[3].toUpperCase());
                break;
            default:
                break;

        }
        return brokerageRate;
    }

    //Testing routine
    public static void main(String args[]) {
        HashMap<OrderLink, Trade> trades = new HashMap<>();
        ArrayList<BrokerageRate> brokerage = new ArrayList();
        brokerage.add(new BrokerageRate());
        brokerage.get(0).primaryRate = 0.01;
        brokerage.get(0).primaryRule = EnumPrimaryApplication.VALUE;
        brokerage.get(0).type = "FUT";
        applyBrokerage(trades, brokerage, 50, "USDADROrders.csv", "", 100000, "DU67768","Equity.csv");
        //String out=DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss",new Date().getTime(),TimeZone.getTimeZone("GMT-4:00"));
        //System.out.println(out);

    }

    static double[] BeanOHLCToArray(ArrayList<BeanOHLC> prices, int tickType) {
        ArrayList<Double> inputValues = new ArrayList<>();
        switch (tickType) {
            case TickType.OPEN:
                for (BeanOHLC p : prices) {
                    inputValues.add(p.getOpen());
                }
                break;
            case TickType.HIGH:
                for (BeanOHLC p : prices) {
                    inputValues.add(p.getHigh());
                }
                break;
            case TickType.LOW:
                for (BeanOHLC p : prices) {
                    inputValues.add(p.getLow());
                }
                break;
            case TickType.CLOSE:
                for (BeanOHLC p : prices) {
                    inputValues.add(p.getClose());
                }
                break;
            case TickType.VOLUME:
                for (BeanOHLC p : prices) {
                    inputValues.add(Long.valueOf(p.getVolume()).doubleValue());
                }
                break;
            default:
                break;
        }

        double[] out = new double[inputValues.size()];
        for (int i = 0; i < inputValues.size(); i++) {
            out[i] = inputValues.get(i);
        }
        return out;
    }

    static double[] DoubleArrayListToArray(List<Double> input) {
        double[] out = new double[input.size()];
        for (int i = 0; i < input.size(); i++) {
            out[i] = input.get(i);
        }
        return out;
    }
    
/**
 * Returns linked external broker orders. 
 * The integer argument must specify the reference to a broker order.  
 * <p>
 * If the argument is not found as an open order, an empty ArrayList
 * will be returned. If there are no other linked orders, and the 
 * argument is the only order, * the size of the list will be 1 
 * and will contain the argument value
 *
 * @param  orderId  reference to an order for which linked orders are 
 *                  needed
 */
    
    static ArrayList<Integer> getLinkedOrderIds(int orderid, BeanConnection c){
        ArrayList<Integer> out=new ArrayList<>();
        HashMap<Index,ArrayList<Integer>> orderMapping;
        synchronized(c.lockOrderMapping){
            orderMapping=c.getOrderMapping();
        }
        for(Map.Entry<Index,ArrayList<Integer>> arr:orderMapping.entrySet()){
            if(arr.getValue().contains(Integer.valueOf(orderid))){
                for(int i:arr.getValue()){
                    out.add(i);
                    //logger.log(Level.FINE, "Debug: GetLinkedOrderIds, Requested Order id: {0},orderid added:{1}, Strategy:{2},InternalOrderID:{3}", new Object[]{orderid,i,arr.getKey().getStrategy(),arr.getKey().getSymbolID()});
                }
            }
        }        
        return out;        
    }
    
    
        public static void logProperties() {
        Properties p = System.getProperties();
        Enumeration<Object> i = p.keys();
        logger.log(Level.INFO,"System Properties");
        logger.log(Level.INFO,"------------------------------------------------------------");
        while (i.hasMoreElements()) {
            String props = (String) i.nextElement();
            logger.log(Level.INFO, ",,Startup,Host System Variables,{0} = {1}", new Object[]{props, (String) p.get(props)});
        }
        logger.log(Level.INFO,"------------------------------------------------------------");
    }
}
