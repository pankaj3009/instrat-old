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
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
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
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import javax.mail.internet.InternetAddress;
import javax.swing.JOptionPane;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.DataPoint;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.QueryMetric;
import org.kairosdb.client.response.QueryResponse;

/**
 *
 * @author psharma
 */
public class TradingUtil {

    public final static Logger logger = Logger.getLogger(TradingUtil.class.getName());
    public static String newline = System.getProperty("line.separator");
    public static License lic = null;
    public static byte[] digest;
    public final static String delimiter = "_";

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
                        iStartTime.setTimeInMillis(beginningOfWeek(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
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
                        iStartTime.setTimeInMillis(beginningOfMonth(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
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
                        iStartTime.setTimeInMillis(beginningOfMonth(iStartTime.getTimeInMillis(), openHour, openMinute, zone, 1));
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
            if (!Boolean.parseBoolean(Algorithm.globalProperties.getProperty("headless", "true"))) {
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
            if (f.exists()) {
                encoded = Files.readAllBytes(Paths.get(path));

                return new String(encoded, encoding);
            } else {
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
            digest = new byte[]{
                (byte) 0x42,
                (byte) 0x2B, (byte) 0xB1, (byte) 0xBE, (byte) 0xD9, (byte) 0x04, (byte) 0xE1, (byte) 0xD1, (byte) 0x96,
                (byte) 0x2E, (byte) 0xF1, (byte) 0x14, (byte) 0x18, (byte) 0x5C, (byte) 0x8F, (byte) 0x19, (byte) 0xFF,
                (byte) 0x6A, (byte) 0xFA, (byte) 0x98, (byte) 0x7D, (byte) 0x1E, (byte) 0xE9, (byte) 0xCF, (byte) 0x4C,
                (byte) 0x49, (byte) 0xFF, (byte) 0x63, (byte) 0x72, (byte) 0xE8, (byte) 0x38, (byte) 0xCE,};
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
        ArrayList<Validity> licenses = new ArrayList<>();
        for (int i = 1; i < 100; i++) {
            if (lic.getFeature(String.valueOf(i)) != null) {
                String feature = lic.getFeature(String.valueOf(i));
                String[] in = feature.split(",");
                licenses.add(new Validity(in[0], in[1], in[2], in[3]));

            } else {
                break;
            }
        }
        if (licenses.size() > 0) {
            for (BeanConnection c : Parameters.connection) {//for each connection string, check if it conforms to license
                String products = c.getStrategy();
                String product[] = products.split(":");
                for (String p : product) { //for each product specified in connection file
                    //create validity
                    Validity temp = new Validity(c.getAccountName().substring(0, 1).equals("D") ? "TRIAL" : "PAID", c.getAccountName().toUpperCase(), p.toUpperCase(), DateUtil.getFormattedDate("yyyy-MM-dd", new Date().getTime()));
                    //check if the product is licensed
                    boolean status = validate(temp, licenses);
                    check = check & status;
                }
            }
            return check;
        } else {
            check = check & false;
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

    private static boolean validate(Validity v, ArrayList<Validity> licenses) {
        for (Validity license : licenses) {
            if ((license.type.equals(v.type) && license.product.equals(v.product) || Pattern.compile(Pattern.quote(v.product), Pattern.CASE_INSENSITIVE).matcher("none").find()) && (license.account.equals("ALL") || license.account.equals(v.account)) && DateUtil.parseDate("yyyy-MM-dd", license.expiry).after(DateUtil.parseDate("yyyy-MM-dd", v.expiry))) {
                return true;
            }
        }

        return false;
    }

    public static Date getAlgoDate() {
        if (MainAlgorithm.isUseForTrading()) {
            return new Date();
        } else {
            if (MainAlgorithm.getAlgoDate() != null) {
                return MainAlgorithm.getAlgoDate();
            } else {
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
                    //String formattedDate = DateUtil.getFormattedDate("yyyyMMdd hh:mm:ss", datetime.getTime());

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
            logger.log(Level.INFO, "101", e);
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
            bufferWritter.flush();
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

    public static Double[] convertArrayToDouble(final String inputArray[]) {
        Double[] out;
        out = new ArrayList<Double>() {
            {
                for (String s : inputArray) {
                    add(new Double(s.trim()));
                }
            }
        }.toArray(new Double[inputArray.length]);
        return out;
    }

    public static Integer[] convertArrayToInteger(final String inputArray[]) {
        Integer[] out;
        out = new ArrayList<Integer>() {
            {
                for (String s : inputArray) {
                    add(new Integer(s.trim()));
                }
            }
        }.toArray(new Integer[inputArray.length]);
        return out;
    }
    /*
     public static int getIDFromSymbol(String symbol, String type, String expiry, String right, String option) {
     for (BeanSymbol symb : Parameters.symbol) {
     String s = symb.getBrokerSymbol() == null ? "" : symb.getBrokerSymbol();
     String t = symb.getType() == null ? "" : symb.getType();
     String e = symb.getExpiry() == null ? "" : symb.getExpiry();
     String r = symb.getRight() == null ? "" : symb.getRight();
     String o = symb.getOption() == null ? "" : symb.getOption();
            
     String si=symbol== null ||symbol.equalsIgnoreCase("null")? "" : symbol;
     String ti = type== null||type.equalsIgnoreCase("null") ? "" : type;
     String ei = expiry == null ||expiry.equalsIgnoreCase("null")? "" : expiry;
     String ri = right == null||right.equalsIgnoreCase("null") ? "" : right;
     String oi = option== null||option.equalsIgnoreCase("null") ? "" : option;
     if (s.compareTo(si) == 0 && t.compareTo(ti) == 0 && e.compareTo(ei)==0 
     && r.compareTo(ri) == 0 && o.compareTo(oi) == 0) {
     return symb.getSerialno() - 1;
     }
     }
     return -1;
     }
     */
    /*
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
     */

    /*
     public static int getIDFromDisplayName(String displayName) {
     for (BeanSymbol symb : Parameters.symbol) {
     if (symb.getDisplayname().equals(displayName)) {
     return symb.getSerialno() - 1;
     }
     }
     return -1;
     }
     */
    public static int getIDFromComboLongName(String comboLongName) {
        for (BeanSymbol symb : Parameters.symbol) {
            if (symb.getBrokerSymbol().equals(comboLongName) && symb.getType().equals("COMBO")) {
                return symb.getSerialno() - 1;
            }
        }
        return -1;
    }

    public static String padRight(String s, int n) {
        if (s.substring(0, 1).equals("-")) {
            return String.format("%1$-" + (n - 1) + "s", s);

        } else {
            return String.format("%1$-" + n + "s", s);
        }
    }

    public static void updateMTM(Database<String, String> db, String key, String timeZone) {
        String today = DateUtil.getFormatedDate("yyyy-MM-dd", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
        double mtmToday = Trade.getMtmToday(db, key);
        String todayDate = Trade.getTodayDate(db, key);
        double mtmYesterday = Trade.getMtmYesterday(db, key);
        if (Trade.getExitPrice(db, key) == 0D || Trade.getEntrySize(db, key) > Trade.getExitSize(db, key)) { //set the MTM
            String parentdisplayname = Trade.getParentSymbol(db, key);
            int id = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
            if (id >= 0) {
                if (!today.equals(todayDate) && mtmToday != 0) {
                    //continuing position. move mtm to yesterday
                    Trade.setMtmYesterday(db, key, "opentrades", mtmToday);
                    Trade.setYesterdayDate(db, key, "opentrades", todayDate);
                }
                Trade.setMtmToday(db, key, "opentrades", Parameters.symbol.get(id).getLastPrice());
                Trade.setTodayDate(db, key, "opentrades", today);
                if (Trade.getMtmToday(db, key) == 0) {
                    SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
                    double alternativemtm = 0;
                    try {
                        alternativemtm = getSettlePrice(new BeanSymbol(Trade.getEntrySymbol(db, key)), sdfDate.parse(today));
                    } catch (Exception e) {
                    }
                    if (alternativemtm == -1) {
                        Trade.setMtmToday(db, key, "opentrades", mtmToday);

                    } else {
                        Trade.setMtmToday(db, key, "opentrades", alternativemtm);
                    }
                }
            }
            //change month end mtm.
            if (Trade.getYesterdayDate(db, key).length() > 9 && !Trade.getYesterdayDate(db, key).substring(5, 7).equals(Trade.getTodayDate(db, key).substring(5, 7))) {
                //month has changed
                mtmYesterday = Trade.getMtmYesterday(db, key);
                Trade.setMtmPriorMonth(db, key, "opentrades", mtmYesterday);
            }
        } /*else {
         if (Trade.getMtmToday(db, key) != 0) {
         //continuing position. move mtm to yesterday
         Trade.setMtmYesterday(db, key, "opentrades", mtmToday);
         Trade.setYesterdayDate(db, key, "opentrades", todayDate);
         }
         }*/
        //change month end mtm.
        if (Trade.getYesterdayDate(db, key).length() > 9 && !Trade.getYesterdayDate(db, key).substring(5, 7).equals(Trade.getTodayDate(db, key).substring(5, 7))) {
            //month has changed
            mtmYesterday = Trade.getMtmYesterday(db, key);
            Trade.setMtmPriorMonth(db, key, "opentrades", mtmYesterday);
        }
    }

    public static int tradesToday(Database<String, String> db, String strategyName, String timeZone, String accountName, String today) {
        int tradesToday = 0; //Holds the number of trades done today
        for (String key : db.getKeys("opentrades_" + strategyName)) {
            if (key.contains("_" + strategyName)) {
                TradingUtil.updateMTM(db, key, timeZone);
                String entryTime = Trade.getEntryTime(db, key);
                String exitTime = Trade.getExitTime(db, key);
                String account = Trade.getAccountName(db, key);
                String childdisplayname = Trade.getEntrySymbol(db, key);
                int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
                if ((entryTime.contains(today) && account.equals(accountName) && !childdisplayname.contains(":"))) {
                    tradesToday = tradesToday + 1;
                }
                if ((!exitTime.equals("") && exitTime.contains(today) && !childdisplayname.contains(":"))) {
                    tradesToday = tradesToday + 1;
                }
                if ((entryTime.contains(today) && account.equals(accountName) && account.equals("Order") && childdisplayname.contains(":"))) {
                    tradesToday = tradesToday + Parameters.symbol.get(childid).getCombo().size();
                }
                if ((!exitTime.equals("") && exitTime.contains(today) && account.equals("Order") && childdisplayname.contains(":"))) {
                    tradesToday = tradesToday + Parameters.symbol.get(childid).getCombo().size();
                }
            }
        }
        for (String key : db.getKeys("closedtrades")) {
            if (key.contains("_" + strategyName)) {
                String entryTime = Trade.getEntryTime(db, key);
                String exitTime = Trade.getExitTime(db, key);
                String account = Trade.getAccountName(db, key);
                String childdisplayname = Trade.getEntrySymbol(db, key);
                int childid = Utilities.getIDFromDisplayName(Parameters.symbol, childdisplayname);
                if (entryTime.contains(today) && account.equals(accountName) && !childdisplayname.contains(":")) { //not a combo
                    tradesToday = tradesToday + 1;
                }
                logger.log(Level.INFO, "DEBUG:{0}", new Object[]{key});
                if ((!exitTime.equals("") && exitTime.contains(today) && !childdisplayname.contains(":"))) {
                    tradesToday = tradesToday + 1;
                }
                if ((entryTime.contains(today) && account.equals(accountName) && account.equals("Order") && childdisplayname.contains(":"))) {
                    tradesToday = tradesToday + Parameters.symbol.get(childid).getCombo().size();
                }
                if ((!exitTime.equals("") && exitTime.contains(today) && account.equals("Order") && childdisplayname.contains(":"))) {
                    tradesToday = tradesToday + Parameters.symbol.get(childid).getCombo().size();
                }
            }
        }
        return tradesToday;
    }

    public static double[] applyBrokerage(Database<String, String> db, ArrayList<BrokerageRate> brokerage, double pointValue, String fileName, String timeZone, double startingEquity, String accountName, String equityFileName, String strategyName) {
        double[] profitGrid = new double[14];
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
             * 10 => Average Drawdown days
             * 11 => # days in current drawdown
             */

            String today = DateUtil.getFormatedDate("yyyy-MM-dd", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
            String todaylongtime = DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
            String todayyyyyMMdd = DateUtil.getFormatedDate("yyyyMMdd", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(timeZone));
            //int tradesToday=tradesToday(db,strategyName,timeZone,accountName,today);
            //set brokerage for open trades.
            for (String key : db.getKeys("opentrades_" + strategyName)) {
                if (key.contains("_" + strategyName)) {
                    String account = Trade.getAccountName(db, key);
                    if (account.equals(accountName)) {
                        int tradesTodayTemp = tradesToday(db, strategyName, timeZone, accountName, Trade.getEntryTime(db, key).substring(0, 10));
                        ArrayList<Double> tempBrokerage = calculateBrokerage(db, key, brokerage, accountName, tradesTodayTemp);
                        Trade.setEntryBrokerage(db, key, "opentrades", Utilities.round(tempBrokerage.get(0), 0));
                        Trade.setExitBrokerage(db, key, "opentrades", Utilities.round(tempBrokerage.get(1), 0));
                        String expiry = Trade.getParentSymbol(db, key).split("_", -1)[2];
                        if (expiry != null && !expiry.equals("") && expiry.compareTo(todayyyyyMMdd) <= 0) {
                            double tdyexitprice = Trade.getMtmToday(db, key);
                            double ydyexitprice = Trade.getExitPrice(db, key);
                            int ydayexitsize = Trade.getExitSize(db, key);
                            double exitprice;
                            if (ydayexitsize > 0) {
                                int tdyexitsize = Trade.getEntrySize(db, key) - ydayexitsize;
                                exitprice = (ydyexitprice * ydayexitsize + tdyexitprice * tdyexitsize) / Trade.getEntrySize(db, key);
                            } else {
                                exitprice = Trade.getMtmToday(db, key);
                            }
                            Trade.setExitPrice(db, key, "opentrades", exitprice);
                            Trade.setExitSize(db, key, "opentrades", Trade.getEntrySize(db, key));
                            Trade.setExitTime(db, key, "opentrades", todaylongtime);
                            Trade.closeTrade(db, key);
                        }
                    }
                }
            }

            //set brokerage for closed trades, that has not still been calculated i.e it equals 0
            for (String key : db.getKeys("closedtrades_" + strategyName)) {
                if (key.contains("_" + strategyName) && Trade.getExitBrokerage(db, key) == 0) {
                    String account = Trade.getAccountName(db, key);
                    if (account.equals(accountName)) {
                        int tradesTodayTemp = tradesToday(db, strategyName, timeZone, accountName, Trade.getExitTime(db, key).substring(0, 10));
                        ArrayList<Double> tempBrokerage = calculateBrokerage(db, key, brokerage, accountName, tradesTodayTemp);
                        Trade.setExitBrokerage(db, key, "closedtrades", Utilities.round(tempBrokerage.get(1), 0));
                    }
                }
                if (key.contains("_" + strategyName) && Trade.getEntryBrokerage(db, key) == 0) {
                    String account = Trade.getAccountName(db, key);
                    if (account.equals(accountName)) {
                        int tradesTodayTemp = tradesToday(db, strategyName, timeZone, accountName, Trade.getEntryTime(db, key).substring(0, 10));
                        ArrayList<Double> tempBrokerage = calculateBrokerage(db, key, brokerage, accountName, tradesTodayTemp);
                        Trade.setEntryBrokerage(db, key, "closedtrades", Utilities.round(tempBrokerage.get(0), 0));
                    }
                }
            }
            Set<String> dates = db.getKeys("pnl_" + strategyName + ":" + accountName);

            //Get last pnl record
            String yesterday = "";
            int count = 0;
            SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdfDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            TreeSet<Long> times = new TreeSet<>();
            String startDate = "";
            for (String d : dates) {
                if (d.contains(strategyName) && d.contains(accountName)) {
                    count = count + 1;
                    int len = d.split("_")[1].split(":").length;
                    long time = sdfDate.parse(d.split("_")[1].split(":")[len - 1]).getTime();
                    times.add(time);
                }
            }
            if (times.size() > 0) {
                yesterday = sdfDate.format(new Date(times.last()));
                startDate = yesterday;
                addPNLRecords(db, strategyName, accountName, startDate, today, true, startingEquity);
            } else {
                times.clear();
                Set<String> keys1 = db.getKeys("opentrades_" + strategyName);
                Set<String> keys2 = db.getKeys("closedtrades_" + strategyName);
                keys1.addAll(keys2);
                for (String key : keys1) {
                    if (key.contains("_" + strategyName) && Trade.getAccountName(db, key).equals(accountName)) {
                        long time = sdfDateTime.parse(Trade.getEntryTime(db, key)).getTime();
                        times.add(time);
                    }
                }
                if (times.size() > 0) {
                    yesterday = sdfDate.format(new Date(times.first()));
                    startDate = yesterday;
                } else {
                    startDate = today;
                }
                addPNLRecords(db, strategyName, accountName, startDate, today, false, startingEquity);
            }

            yesterday = getLastPNLRecordDate(db, accountName, strategyName, today, true);
            String key = strategyName + ":" + accountName + ":" + yesterday;
            profitGrid[0] = Utilities.getDouble(db.getValue("pnl", key, "todaypnl"), 0);
            profitGrid[2] = Utilities.getDouble(db.getValue("pnl", key, "todaypnl"), 0);
            profitGrid[4] = Utilities.getDouble(db.getValue("pnl", key, "ytd"), 0);
            profitGrid[5] = Utilities.getDouble(db.getValue("pnl", key, "drawdownpercentmax"), 0);
            profitGrid[6] = Utilities.getDouble(db.getValue("pnl", key, "drawdowndaysmax"), 0);
            profitGrid[8] = Utilities.getDouble(db.getValue("pnl", key, "sharpe"), 0);
            profitGrid[10] = Utilities.getDouble(db.getValue("pnl", key, "averagedddays"), 0);
            profitGrid[11] = Utilities.getDouble(db.getValue("pnl", key, "currentdddays"), 0);
            profitGrid[12] = Utilities.getDouble(db.getValue("pnl", key, "averageddvalue"), 0);
            profitGrid[13] = Utilities.getDouble(db.getValue("pnl", key, "currentddvalue"), 0);

            count = count + 1;//increased count for new pnl recort
            dates = db.getKeys("pnl_" + strategyName + ":" + accountName);
            int recordsInHistory = dates.size();
            profitGrid[9] = Utilities.getDouble(recordsInHistory, 0);

        } catch (Exception ex) {
            logger.log(Level.SEVERE, "101", ex);
        }
        return profitGrid;
    }

    public static void addPNLRecords(Database<String, String> db, String strategy, String account, String startDate, String endDate, boolean startfromNBD, double startingEquity) throws ParseException {
        String strategyaccount = strategy + ":" + account;
        TreeMap<Long, String> pair = new TreeMap<>();
        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd");
        String bpd = DateUtil.getFormatedDate("yyyy-MM-dd", TradingUtil.getAlgoDate().getTime(), TimeZone.getTimeZone(Algorithm.timeZone));
        for (String key : db.getKeys("closedtrades_" + strategy)) {
            if (key.contains("_" + strategy) && key.contains(account)) {
                long time = sdfTime.parse(Trade.getExitTime(db, key)).getTime();
                while (pair.containsKey(time)) {
                    time = time + 1;
                }
                pair.put(time, key);
            }
        }
        for (String key : db.getKeys("opentrades_" + strategy)) {
            if (key.contains("_" + strategy) && key.contains(account)) {
                long time = sdfDate.parse(bpd).getTime();
                while (pair.containsKey(time)) {
                    time = time + 1;
                }
                pair.put(time, key);
            }
        }
        int longwins = 0;
        int longlosses = 0;
        int shortwins = 0;
        int shortlosses = 0;
        int tradeCount = 0;
        ArrayList<Double> dailyEquity = new ArrayList();

        //update dailyequity with current records
        Set<String> pnlKeys = db.getKeys("pnl");
        TreeMap<Long, String> pnl = new TreeMap<>();
        for (String key : pnlKeys) {
            if (key.contains("_" + strategy) && key.contains(account)) {
                int length = key.length();
                long time = sdfDate.parse(key.substring(length - 10, length)).getTime();
                pnl.put(time, key);
            }
        }
        for (Map.Entry<Long, String> entry : pnl.entrySet()) {
            String date = sdfDate.format(new Date(entry.getKey()));
            dailyEquity.add(Utilities.getDouble(db.getValue("pnl", strategy + ":" + account + ":" + date, "ytd"), 0) + startingEquity);
        }
        //pnlDates contains the dates for which we need to generate trade records
        ArrayList<String> pnlDates = new ArrayList<>();
        if (!startfromNBD) {
            pnlDates.add(startDate);
        }
        //Enhance pnlDates to contain all dates between startDate and endDate
        for (Date d = DateUtil.parseDate("yyyy-MM-dd", startDate); d.compareTo(DateUtil.parseDate("yyyy-MM-dd", endDate)) <= 0; d = DateUtil.addDays(d, 1)) {
            String temp = sdfDate.format(TradingUtil.nextGoodDay(d, 24 * 60, Algorithm.timeZone, 9, 15, 15, 30, Algorithm.holidays, true));
            if (temp.compareTo(endDate) <= 0) {
                if (!pnlDates.contains(temp)) {
                    pnlDates.add(temp);
                }
            }
        }
        for (String today : pnlDates) {
            //get last trade record date
            logger.log(Level.INFO, "PNLRecords,{0}", new Object[]{strategy + delimiter + account + delimiter + today});
            String yesterday = getLastPNLRecordDate(db, account, strategy, today, false);
            double ytdpnl = 0;
            if (!yesterday.equals("")) {
                ytdpnl = Utilities.getDouble(db.getValue("pnl", strategy + ":" + account + ":" + yesterday, "ytd"), 0);
            }
            if (yesterday.equals("")) {
                yesterday = sdfDate.format(Utilities.previousGoodDay(sdfDate.parse(startDate), -24 * 60, Algorithm.timeZone, 9, 15, 15, 30, Algorithm.holidays, true));
            }
            Iterator<Map.Entry<Long, String>> keys = pair.entrySet().iterator();
            while (keys.hasNext()) {
                String key = keys.next().getValue();
                String exitDate = Trade.getExitTime(db, key);
                exitDate = exitDate.equals("") ? "" : exitDate.substring(0, 10);
                if (exitDate.compareTo(today) <= 0 && !exitDate.equals("")) {//update win loss ratio
                    EnumOrderSide side = Trade.getEntrySide(db, key);
                    switch (side) {
                        case BUY:
                            if (Trade.getExitPrice(db, key) > Trade.getEntryPrice(db, key)) {
                                longwins = longwins + 1;
                            } else {
                                longlosses = longlosses + 1;
                            }
                            break;
                        case SHORT:
                            if (Trade.getExitPrice(db, key) < Trade.getEntryPrice(db, key)) {
                                shortwins = shortwins + 1;
                            } else {
                                shortlosses = shortlosses + 1;
                            }
                            break;
                        default:
                            break;
                    }
                    tradeCount = tradeCount + 1;
                }
                if (exitDate.compareTo(yesterday) > 0 || exitDate.equals("")) {
                    try {
                        String entryDate = Trade.getEntryTime(db, key).substring(0, 10);
                        if (entryDate.compareTo(today) <= 0) {
                            double entryPrice = 0;
                            double exitPrice;
                            double mtmYesterday = Trade.getMtmYesterday(db, key);
                            if (mtmYesterday == 0 || Trade.getEntryTime(db, key).contains(today) || pnlDates.size() > 1) {
                                //get mtmyesterday only if the entry date was today or else we are running pnl for many days 
                                mtmYesterday = getSettlePrice(new BeanSymbol(Trade.getEntrySymbol(db, key)), sdfDate.parse(yesterday));
                            }
                            if (exitDate.equals("")) {
                                Trade.setMtmYesterday(db, key, "opentrades", mtmYesterday);
                            } else {
                                Trade.setMtmYesterday(db, key, "closedtrades", mtmYesterday);
                            }
                            if (entryDate.equals(today)) {
                                entryPrice = Trade.getEntryPrice(db, key);
                            } else if (entryDate.compareTo(yesterday)<=0) {
                                entryPrice = Trade.getMtmYesterday(db, key);
                            } //else if (entryDate.compareTo(yesterday) < 0) {
                                //entryPrice = getSettlePrice(new BeanSymbol(Trade.getEntrySymbol(db, key)), sdfDate.parse(yesterday));
                            //} 
                              else {
                            }
                            int entrySize = Trade.getEntrySize(db, key);
                            if (exitDate.equals("") || exitDate.compareTo(today) > 0) {
                                exitPrice = getSettlePrice(new BeanSymbol(Trade.getEntrySymbol(db, key)), sdfDate.parse(today));
                                if (exitPrice == -1 && today.equals(bpd)) {
                                    exitPrice = Trade.getMtmToday(db, key);
                                }
                                if (exitDate.equals("")) {
                                    Trade.setMtmToday(db, key, "opentrades", exitPrice);
                                } else {
                                    Trade.setMtmToday(db, key, "closedtrades", exitPrice);
                                }
                            } else {
                                exitPrice = Trade.getExitPrice(db, key);
                            }
                            if (exitPrice > 0 && entryPrice > 0) {
                                double buypnl = (exitPrice - entryPrice) * entrySize;
                                EnumOrderSide side = Trade.getEntrySide(db, key);
                                double netpnl = side == EnumOrderSide.BUY ? buypnl : -buypnl;
                                double brokerage = 0;
                                if (Trade.getEntryTime(db, key).contains(today)) {
                                    brokerage = brokerage + Trade.getEntryBrokerage(db, key);
                                }
                                if (Trade.getExitTime(db, key).contains(today)) {
                                    brokerage = brokerage + Trade.getExitBrokerage(db, key);
                                }

                                netpnl = netpnl - brokerage;
                                db.setHash("pnl", strategyaccount + ":" + today, "drilldown:" + key, String.valueOf(entrySize) + "_" + String.valueOf(entryPrice) + "_" + String.valueOf(exitPrice) + "_" + Utilities.formatDouble(brokerage, new DecimalFormat("#")) + "_" + Utilities.formatDouble(netpnl, new DecimalFormat("#")));
                                ytdpnl = ytdpnl + (side == EnumOrderSide.BUY ? buypnl : -buypnl);
                                if (entryDate.equals(today)) {
                                    ytdpnl = ytdpnl - Trade.getEntryBrokerage(db, key);
                                }
                                if (exitDate.equals(today)) {
                                    ytdpnl = ytdpnl - Trade.getExitBrokerage(db, key);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.log(Level.SEVERE, null, e);
                        logger.log(Level.SEVERE, "Key:{0}, EntryTime:{1}", new Object[]{key, Trade.getEntryTime(db, key)});
                    }
                } else {
                    keys.remove();
                }

            }//end iter
            dailyEquity.add(startingEquity + ytdpnl);
            double drawdowndaysmax = TradingUtil.drawdownDays(dailyEquity)[0];
            double drawdownpercentage = TradingUtil.maxDrawDownPercentage(dailyEquity);
            double sharpe = TradingUtil.sharpeRatio(dailyEquity);
            double winratio = longwins + shortwins + longlosses + shortlosses > 0 ? ((longwins + shortwins) * 100 / (longwins + shortwins + longlosses + shortlosses)) : 0;
            double longwinratio = longwins + longlosses > 0 ? (longwins * 100 / (longwins + longlosses)) : 0;
            double shortwinratio = shortwins + shortlosses > 0 ? (shortwins * 100 / (shortwins + shortlosses)) : 0;
            String sd = today;
            db.setHash("pnl", strategyaccount + ":" + sd, "ytd", String.valueOf(Utilities.round(ytdpnl, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "winratio", String.valueOf(Utilities.round(winratio, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "longwinratio", String.valueOf(Utilities.round(longwinratio, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "shortwinratio", String.valueOf(Utilities.round(shortwinratio, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "tradecount", String.valueOf(tradeCount));
            double todaypnl = dailyEquity.size() > 1 ? dailyEquity.get(dailyEquity.size() - 1) - dailyEquity.get(dailyEquity.size() - 2) : dailyEquity.get(dailyEquity.size() - 1) - startingEquity;
            db.setHash("pnl", strategyaccount + ":" + sd, "todaypnl", String.valueOf(String.valueOf(Utilities.round(todaypnl, 0))));
            db.setHash("pnl", strategyaccount + ":" + sd, "sharpe", String.valueOf(Utilities.round(sharpe, 2)));
            db.setHash("pnl", strategyaccount + ":" + sd, "drawdowndaysmax", String.valueOf(Utilities.round(drawdowndaysmax, 0)));
            db.setHash("pnl", strategyaccount + ":" + sd, "drawdownpercentmax", String.valueOf(Utilities.round(drawdownpercentage, 2)));

        }//end pnldates

        updateDrawDownMetrics(db, strategy, account);
    }

    private static double getSettlePrice(BeanSymbol s, Date d) {
        double settlePrice = -1;
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
                default:
                    metric = null;
                    break;
            }
            Date startDate = d;
            Date endDate = d;
            String strike = Utilities.formatDouble(Utilities.getDouble(s.getOption(), 0), new DecimalFormat("#.##"));

            QueryBuilder builder = QueryBuilder.getInstance();
            builder.setStart(startDate)
                    .setEnd(DateUtil.addSeconds(d, 1))
                    .addMetric(metric)
                    .addTag("symbol", s.getBrokerSymbol().toLowerCase());
            if (!s.getExpiry().equals("")) {
                builder.getMetrics().get(0).addTag("expiry", s.getExpiry());
            }
            if (!s.getRight().equals("")) {
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
                Object value = dataPoint.getValue();
                settlePrice = Double.parseDouble(value.toString());
            }
        } catch (Exception e) {
            logger.log(Level.INFO, null, e);
        }
        return settlePrice;
    }

    private static String getLastPNLRecordDate(Database<String, String> db, String accountName, String strategyName, String referenceDate, boolean equals) {
        Set<String> dates = db.getKeys("pnl_" + strategyName + ":" + accountName);
        String yesterday = "";
        for (String d : dates) {
            int len = d.split("_")[1].split(":").length;
            String yesterday1 = d.split("_")[1].split(":")[len - 1];
            if (!equals) {
                if (yesterday1.compareTo(referenceDate) < 0 && yesterday.compareTo(yesterday1) < 0) {
                    yesterday = yesterday1;
                }
            } else {
                if (yesterday1.compareTo(referenceDate) <= 0 && yesterday.compareTo(yesterday1) < 0) {
                    yesterday = yesterday1;
                }
            }
        }
        return yesterday;
    }

    private static void updateDrawDownMetrics(Database<String, String> db, String strategyName, String accountName) {
        Set<String> keys = db.getKeys("pnl");
        TreeMap<Long, String> pair = new TreeMap<>();
        SimpleDateFormat sdfTime = new SimpleDateFormat("yyyy-MM-dd");
        //Create a sorted map
        for (String key : keys) {
            try {
                if (key.contains("_" + strategyName) && key.contains(accountName)) {
                    long time = sdfTime.parse(key.substring(key.length() - 10, key.length())).getTime();
                    pair.put(time, key);
                }
            } catch (Exception e) {
                logger.log(Level.SEVERE, null, e);
            }
        }
        double hwmvalue = 0;
        int hwmcount = 0;
        int ddcount = 0;
        double ytdpnl = 0;
        double priordayytdpnl = 0;
        int numberofuniquedd = 0;
        int numberofuniquehwm = 0;
        double currentdd = 0;
        double currenthwm = 0;
        double currentlwm = Double.MAX_VALUE;
        double ddvalue = 0;
        double ddstartpnl = 0;
        int ddstart = 0;

        ArrayList<Double> ddvalueseries = new ArrayList<>();
        ArrayList<Integer> ddcountseries = new ArrayList<>();

        for (String key : pair.values()) {
            String key1 = key.substring(4, key.length());

            ytdpnl = Utilities.getDouble(db.getValue("pnl", key1, "ytd"), 0);
            if (ytdpnl >= hwmvalue) {
                if (currenthwm == 0) {
                    numberofuniquehwm++;
                    ddvalue = ddvalue + ddstartpnl - priordayytdpnl;
                    ddvalueseries.add(ddstartpnl - currentlwm);
                    ddcountseries.add(ddcount - ddstart);
                    currentlwm = Double.MAX_VALUE;
                    ddstartpnl = 0;
                }
                currenthwm++;
                hwmcount++;
                hwmvalue = ytdpnl;
                currentdd = 0;
            } else {
                if (currentdd == 0) {
                    numberofuniquedd++;
                    ddstartpnl = priordayytdpnl;
                    ddstart = ddcount;
                }
                if (ytdpnl < currentlwm) {
                    currentlwm = ytdpnl;
                }
                currentdd++;
                ddcount++;
                currenthwm = 0;
            }
            priordayytdpnl = ytdpnl;
            //write data to pnl files
            double dddaysmean = calculateMean(ddcountseries);
            double dddayssd = calculateSD(ddcountseries);
            double ddvaluemean = calculateMean(ddvalueseries);
            double ddvaluesd = calculateSD(ddvalueseries);

            db.setHash("pnl", key1, "hwmvalue", String.valueOf(Utilities.round(hwmvalue, 0)));
            db.setHash("pnl", key1, "hwmcount", String.valueOf(Utilities.round(hwmcount, 0)));
            db.setHash("pnl", key1, "hwmuniquestarts", String.valueOf(Utilities.round(numberofuniquehwm, 0)));
            db.setHash("pnl", key1, "ddcount", String.valueOf(Utilities.round(ddcount, 0)));
            db.setHash("pnl", key1, "dduniquestarts", String.valueOf(Utilities.round(numberofuniquedd, 0)));
            //db.setHash("pnl", key1, "averagedddays",String.valueOf(Utilities.round(ddcount/numberofuniquedd, 1)));
            db.setHash("pnl", key1, "averagedddays", String.valueOf(Utilities.round(dddaysmean, 1)));
            db.setHash("pnl", key1, "sddddays", String.valueOf(Utilities.round(dddayssd, 1)));
            db.setHash("pnl", key1, "averageddvalue", String.valueOf(Utilities.round(ddvaluemean, 0)));
            db.setHash("pnl", key1, "sdddvalue", String.valueOf(Utilities.round(ddvaluesd, 1)));
            db.setHash("pnl", key1, "currentdddays", String.valueOf(Utilities.round(currentdd, 0)));
            //currentlwm=currentlwm<Double.MAX_VALUE?currentlwm:0;
            db.setHash("pnl", key1, "currentddvalue", String.valueOf(Utilities.round(ddstartpnl - (currentlwm < Double.MAX_VALUE ? currentlwm : 0), 0)));
            if (ddvalueseries.size() > 0) {
                db.setHash("pnl", key1, "maxddvalue", String.valueOf(Utilities.round(Collections.max(ddvalueseries), 0)));
            }
            if (ddcountseries.size() > 0) {
                db.setHash("pnl", key1, "maxdddays", String.valueOf(Utilities.round(Collections.max(ddcountseries), 0)));
            }
        }
    }

    private static <T> double calculateMean(List<T> data) {
        double sum = 0;
        if (!data.isEmpty()) {
            for (T d : data) {
                sum += Double.valueOf(d.toString());
            }
            return sum / data.size();
        }
        return sum;
    }

    private static <T> double calculateSD(List<T> data) {
        double sum = 0;
        double avg = calculateMean(data);
        if (!data.isEmpty()) {
            for (T d : data) {

                sum += Math.pow(Double.valueOf(d.toString()) - avg, 2);
            }
            return Math.sqrt(sum / data.size());
        }
        return sum;
    }

    public static ArrayList<Double> calculateBrokerage(Database<String, String> db, String key, ArrayList<BrokerageRate> brokerage, String accountName, int tradesToday) {
        ArrayList<Double> brokerageCost = new ArrayList<>();
        brokerageCost.add(0D);
        brokerageCost.add(0D);
        int entryorderidint = Trade.getEntryOrderIDInternal(db, key);
        String childsymboldisplayname = Trade.getEntrySymbol(db, key);
        int id = Utilities.getIDFromDisplayName(Parameters.symbol, childsymboldisplayname);
        String account = Trade.getAccountName(db, key);
        if (id >= 0 && Parameters.symbol.get(id).getType().equals("COMBO")) {
            if (!account.equals("Order")) {
                ArrayList<Double> singleLegCost = calculateSingleLegBrokerage(db, key, brokerage, tradesToday);
                double earlierEntryCost = brokerageCost.get(0);
                double earlierExitCost = brokerageCost.get(1);
                brokerageCost.set(0, earlierEntryCost + singleLegCost.get(0));
                brokerageCost.set(1, earlierExitCost + singleLegCost.get(1));
                /*      
                 Set<Entry>entries=trades.entrySet();
                 for(Entry entry:entries){
                 String subkey=(String)entry.getKey();
                 int sentryorderidint=Trade.getEntryOrderIDInternal(trades, subkey);
                 int sid=Trade.getEntrySymbolID(trades, key);
                 if(sentryorderidint==entryorderidint && !Parameters.symbol.get(sid).getType().equals("COMBO")){//only calculate brokerage for child legs
                 ArrayList<Double> singleLegCost=calculateSingleLegBrokerage(trades,subkey,brokerage,tradesToday);
                 double earlierEntryCost=brokerageCost.get(0);
                 double earlierExitCost=brokerageCost.get(1);
                 brokerageCost.set(0, earlierEntryCost+singleLegCost.get(0));
                 brokerageCost.set(1, earlierExitCost+singleLegCost.get(1));
                 }
                 }                
                 */
            } else {
                //get legs for orders
                String parentdisplayname = Trade.getParentSymbol(db, key);
                int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
                int parentEntryOrderIDInt = Trade.getParentExitOrderIDInternal(db, key);
                for (Map.Entry<BeanSymbol, Integer> comboComponent : Parameters.symbol.get(parentid).getCombo().entrySet()) {
                    int childid = comboComponent.getKey().getSerialno() - 1;
                    for (String subkey : db.getKeys("opentrades")) {
                        int sparentEntryOrderIDInt = Trade.getParentEntryOrderIDInternal(db, key);
                        if (sparentEntryOrderIDInt == parentEntryOrderIDInt && !Trade.getEntrySymbol(db, subkey).equals(Trade.getParentSymbol(db, subkey))) {
                            ArrayList<Double> singleLegCost = calculateSingleLegBrokerage(db, subkey, brokerage, tradesToday);
                            double earlierEntryCost = brokerageCost.get(0);
                            double earlierExitCost = brokerageCost.get(1);
                            brokerageCost.set(0, earlierEntryCost + singleLegCost.get(0));
                            brokerageCost.set(1, earlierExitCost + singleLegCost.get(1));

                        }
                    }
                }
            }
        } else {
            brokerageCost = calculateSingleLegBrokerage(db, key, brokerage, tradesToday);
        }
//         TradingUtil.writeToFile("brokeragedetails.txt", t.getEntrySymbol()+","+brokerageCost.get(0)+","+brokerageCost.get(1));
        return brokerageCost;
    }

    private static ArrayList<Double> calculateSingleLegBrokerage(Database<String, String> db, String key, ArrayList<BrokerageRate> brokerage, int tradesToday) {
        ArrayList<Double> brokerageCost = new ArrayList<>();
        double entryCost = 0;
        double exitCost = 0;
        String parentdisplayname = Trade.getParentSymbol(db, key);
        int parentid = Utilities.getIDFromDisplayName(Parameters.symbol, parentdisplayname);
        double entryPrice = Trade.getEntryPrice(db, key);
        double exitPrice = Trade.getExitPrice(db, key);
        int entrySize = Trade.getEntrySize(db, key);
        int exitSize = Trade.getExitSize(db, key);
        String exitTime = Trade.getExitTime(db, key);
        String entryTime = Trade.getEntryTime(db, key);
        EnumOrderSide entrySide = Trade.getEntrySide(db, key);
        EnumOrderSide exitSide = Trade.getExitSide(db, key);

        //calculate entry costs
        for (BrokerageRate b : brokerage) {
            switch (b.primaryRule) {
                case VALUE:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide.equals(EnumOrderSide.BUY) || exitSide.equals(EnumOrderSide.COVER)))) {
                        entryCost = entryCost + (entryPrice * entrySize * b.primaryRate / 100) + (entryPrice * entrySize * b.primaryRate * b.secondaryRate / 10000);
                    }
                    break;
                case SIZE:
                    String symboldisplayName = Trade.getEntrySymbol(db, key);
                    int id = Utilities.getIDFromDisplayName(Parameters.symbol, symboldisplayName);
                    int contractsize = 0;
                    if (id >= 0) {//symbols have moved on...
                        contractsize = Parameters.symbol.get(id).getMinsize();
                    }
                    int entrySizeContracts = 1;
                    if (contractsize > 0) {
                        entrySizeContracts = (entrySize / contractsize);
                    }
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
                        entryCost = entryCost + entrySizeContracts * b.primaryRate + (entrySizeContracts * b.primaryRate * b.secondaryRate / 100);
                    }
                    break;
                case FLAT:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
                        entryCost = entryCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                    }
                    break;
                case DISTRIBUTE:
                    if (!(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (entrySide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER))) {
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
                    if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                        exitCost = exitCost + (exitPrice * exitSize * b.primaryRate / 100) + (exitPrice * exitSize * b.primaryRate * b.secondaryRate / 10000);
                    }
                    break;
                case SIZE:
                    String symboldisplayName = Trade.getEntrySymbol(db, key);
                    int id = Utilities.getIDFromDisplayName(Parameters.symbol, symboldisplayName);
                    int contractsize = 0;
                    if (id >= 0) {//symbols have moved on...
                        contractsize = Parameters.symbol.get(id).getMinsize();
                    }
                    int exitSizeContracts = 1;
                    if (contractsize > 0) {
                        exitSizeContracts = (exitSize / contractsize);
                    }
                    if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                        exitCost = exitCost + exitSizeContracts * b.primaryRate + (exitSizeContracts * b.primaryRate * b.secondaryRate / 100);
                    }
                    break;
                case FLAT:
                    if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                        exitCost = exitCost + b.primaryRate + b.primaryRate * b.secondaryRate;
                    }
                    break;
                case DISTRIBUTE:
                    if (!exitTime.equals("") && !(b.secondaryRule == EnumSecondaryApplication.EXCLUDEBUY && (exitSide == EnumOrderSide.BUY || exitSide == EnumOrderSide.COVER) || (b.secondaryRule == EnumSecondaryApplication.EXCLUDEINTRADAYREVERSAL && exitTime.contains(entryTime.substring(0, 10))))) {
                        if (tradesToday > 0) {
                            exitCost = exitCost + b.primaryRate / tradesToday + (b.primaryRate / tradesToday) * b.secondaryRate;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
        entryCost = Trade.getEntryBrokerage(db, key) == 0D ? entryCost : Trade.getEntryBrokerage(db, key);
        brokerageCost.add(entryCost);
        exitCost = Trade.getExitBrokerage(db, key) == 0D ? exitCost : Trade.getExitBrokerage(db, key);
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
        if (returns.size() > 0) {
            double mean = stats.getMean();
            double std = stats.getStandardDeviation();
            if (std > 0) {
                return (mean) / std * Math.sqrt(260);
            } else {
                return 0;
            }
        } else {
            return 0;
        }
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
        DataStore<String, String> trades = new DataStore<>();
        ArrayList<BrokerageRate> brokerage = new ArrayList();
        brokerage.add(new BrokerageRate());
        brokerage.get(0).primaryRate = 0.01;
        brokerage.get(0).primaryRule = EnumPrimaryApplication.VALUE;
        brokerage.get(0).type = "FUT";
        //applyBrokerage(trades, brokerage, 50, "USDADROrders.csv", "", 100000, "DU67768", "Equity.csv");
        //String out=DateUtil.getFormattedDate("yyyy-MM-dd HH:mm:ss",new Date().getTime(),TimeZone.getTimeZone("GMT-4:00"));
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
     * Returns linked external broker orders. The integer argument must specify
     * the reference to a broker order.
     * <p>
     * If the argument is not found as an open order, an empty ArrayList will be
     * returned. If there are no other linked orders, and the argument is the
     * only order, * the size of the list will be 1 and will contain the
     * argument value
     *
     * @param orderId reference to an order for which linked orders are needed
     */
    static ArrayList<Integer> getLinkedOrderIds(int orderid, BeanConnection c) {
        ArrayList<Integer> out = new ArrayList<>();
        HashMap<Index, ArrayList<Integer>> orderMapping;
        synchronized (c.lockOrderMapping) {
            orderMapping = c.getOrderMapping();
        }
        for (Map.Entry<Index, ArrayList<Integer>> arr : orderMapping.entrySet()) {
            if (arr.getValue().contains(Integer.valueOf(orderid))) {
                for (int i : arr.getValue()) {
                    out.add(i);
                    //logger.log(Level.FINE, "Debug: GetLinkedOrderIds, Requested Order childid: {0},orderid added:{1}, Strategy:{2},InternalOrderID:{3}", new Object[]{orderid,i,arr.getKey().getStrategy(),arr.getKey().getSymbolID()});
                }
            }
        }
        return out;
    }

    public static void logProperties() {
        Properties p = System.getProperties();
        Enumeration<Object> i = p.keys();
        logger.log(Level.INFO, "System Properties");
        logger.log(Level.INFO, "------------------------------------------------------------");
        while (i.hasMoreElements()) {
            String props = (String) i.nextElement();
            logger.log(Level.INFO, ",,Startup,Host System Variables,{0} = {1}", new Object[]{props, (String) p.get(props)});
        }
        logger.log(Level.INFO, "------------------------------------------------------------");
    }
}
