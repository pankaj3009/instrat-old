/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.incurrency.framework;

import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.primitives.Doubles;
import com.google.common.primitives.Ints;
import com.incurrency.RatesClient.RequestClient;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Pankaj
 */
public class Utilities {

    private static final Logger logger = Logger.getLogger(Utilities.class.getName());
    public static String newline = System.getProperty("line.separator");

    /**
     * 
     * @param s
     * @param timeSeries - example {"open","close"}
     * @param metric - example "india.nse.index.s4.daily"
     * @param startTime format 20150101 00:00:00 or yyyyMMdd HH:mm:ss
     * @param endTime format 20150101 00:00:00 or yyyyMMdd HH:mm:ss
     * @param barSize
     * @param appendAtEnd data retrieved from the request is appended to the end of specified output text file
     * @return 
     */
     public BeanSymbol requestMarketData(BeanSymbol s, String[] timeSeries, String metric, String startTime, String endTime, EnumBarSize barSize,boolean appendAtEnd) {
        try {
            SimpleDateFormat sdfExtendedTimeFormat = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
            Date startDate = sdfExtendedTimeFormat.parse(startTime);
            Date endDate = sdfExtendedTimeFormat.parse(endTime);
            String symbol = null;
            symbol = s.getDisplayname().replaceAll(" ", "").replaceAll("&", "").toLowerCase() + "_" + s.getType();
            String path = Algorithm.globalProperties.getProperty("path").toString().toLowerCase();
            RequestClient rc = new RequestClient(path);
            String concatMetrics = null;
            for (String m : timeSeries) {
                if (concatMetrics == null) {
                    concatMetrics = metric + "." + m;
                } else {
                    concatMetrics = concatMetrics + "," + metric + "." + m;
                }
            }
            rc.sendRequest("backfill", s, new String[]{barSize.toString(), String.valueOf(startDate.getTime()), String.valueOf(endDate.getTime())}, concatMetrics, timeSeries,appendAtEnd);
            rc.run();
            rc.start.put("start");
            String finished = rc.end.take();


        } catch (Exception e) {
        } finally {
            return s;
        }
    }

    /**
     * Prints data in an ExtendedHashMap to a file.
     *
     * @param h
     * @param filename
     * @param printOrder
     */
    public static <J, K, V> void print(ExtendedHashMap<J, K, V> h, String filename, String[] printOrder) {

        if (h.store.size() > 0) {
            boolean headersWritten = false;
            String headers = "";
            for (Object key : h.store.keySet()) {
                String output = key.toString();
                //for(Map.Entry<String,Double> values:h.store.get(key).entrySet()){
                if (printOrder != null) {
                    for (int i = 0; i < printOrder.length; i++) {
                        Iterator it = h.store.get(key).entrySet().iterator();
                        while (it.hasNext()) {
                            Map.Entry<K, V> values = (Map.Entry) it.next();

                            if (values.getKey().equals(printOrder[i])) {
                                if (!headersWritten) {
                                    headers = headers + "," + values.getKey().toString();
                                }
                                output = output + "," + values.getValue().toString();
                                it.remove();
                            }
                        }
                    }
                }
                Iterator it = h.store.get(key).entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<K, V> values = (Map.Entry) it.next();
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

    public static void loadMarketData(String filePath, String displayName, List<BeanSymbol> symbols) {
        int id = Utilities.getIDFromDisplayName(symbols,displayName);
        EnumBarSize barSize=EnumBarSize.valueOf(filePath.split("_")[1].split("\\.")[0]);
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
                            String[]timeOfDay=input[1].split(":");
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
                String[]timeOfDay=input[1].split(":");
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

    public static <K> String  concatStringArray(K[] input) {
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
    
     public static String  concatStringArray(double[] input) {
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
  * Rounds to the specified step as http://bytes.com/topic/visual-basic-net/answers/553549-how-round-number-custom-step-0-25-20-100-a
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
            return round;
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
        value=value.trim();
        return value.matches("-?\\d+(\\.\\d+)?");
    }

    public static boolean isInteger(String str) {
        if (str == null) {
            return false;
        }
        str=str.trim();
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

    public static double getDouble(String input, double defvalue) {
        if (isDouble(input)) {
            return Double.parseDouble(input.trim());
        } else {
            return defvalue;
        }
    }

    public static int getInt(String input, int defvalue) {
        if (isInteger(input)) {
            return Integer.parseInt(input.trim());
        } else {
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
        File f=new File(parameterFile);
        if(f.exists()){
        try {
            propFile = new FileInputStream(parameterFile);
            p.load(propFile);

        } catch (Exception ex) {
            logger.log(Level.INFO, "101", ex);
        }
        }

        return p;
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
    public static int getIDFromSymbol(List<BeanSymbol> symbols,String symbol, String type, String expiry, String right, String option) {
        for (BeanSymbol symb : symbols) {
            String s = symb.getSymbol() == null ? "" : symb.getSymbol();
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
    public static int getIDFromSymbol(List<BeanSymbol> symbols,String[] symbol) {
        String si = "", ti = "", ei = "", ri = "", oi = "";
        switch (symbol.length) {
            case 2:
                si = symbol[0];
                ti = symbol[1];
                ei = "";
                ri = "";
                oi = "";
                break;
            case 3:
                si = symbol[0];
                ti = symbol[1];
                ei = symbol[2];
                ri = "";
                oi = "";
                break;
            case 5:
                si = symbol[0];
                ti = symbol[1];
                ei = symbol[2];
                ri = symbol[3];
                oi = symbol[4];
                break;
            default:
                break;
        }

        for (BeanSymbol symb : symbols) {
            String s = symb.getSymbol() == null ? "" : symb.getDisplayname().replace("&", "");
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
    public static int getIDFromDisplayName(List<BeanSymbol>symbols,String displayName) {
        for (BeanSymbol symb : symbols) {
            if (symb.getDisplayname().equals(displayName)) {
                return symb.getSerialno() - 1;
            }
        }
        return -1;
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

    /**
     * Writes content in String[] to a file.The first column in the file has the
     * timestamp,used to format content[0] to correct time.The first two columns
     * in the FILENAME will be written with date and time respectively.
     *
     * @param filename
     * @param content
     * @param timeZone
     */
    public static void writeToFile(String filename, String[] content, String timeZone,boolean appendAtEnd) {
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
            if(!appendAtEnd){
             if (!file.exists()) {
                file.createNewFile();
            }   
                File newfile = new File(dir, filename+".old");
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
            if(!appendAtEnd){
                File newfile = new File(dir, filename+".old");
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

    
    /**
     * Writes to filename, the values in String[].
     *
     * @param filename
     * @param content
     */
    public static void writeToFile(String filename, String content) {
        try {
            File dir = new File("logs");
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
        } catch (IOException ex) {
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
    
    public static void deleteFile(String filename){
        File dir = new File("logs");
            File file = new File(dir, filename);
            if (file.exists()) {
                file.delete();
            }
    }
    
        public static void deleteFile(File file){
            if (file.exists()) {
                file.delete();
            }
    }
}