package com.incurrency.framework;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Date utility
 *
 * $Id$
 */
public class DateUtil {

    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
    private static final long MILLI_SEC_PER_DAY = 1000 * 60 * 60 * 24;
    private static final Logger logger = Logger.getLogger(DateUtil.class.getName());
    public final static long SECOND_MILLIS = 1000;
    public final static long MINUTE_MILLIS = SECOND_MILLIS*60;
    public final static long HOUR_MILLIS = MINUTE_MILLIS*60;
    public final static long DAY_MILLIS = HOUR_MILLIS*24;
    public final static long YEAR_MILLIS = DAY_MILLIS*365;
    public static long getCurrentTime() {
        return System.currentTimeMillis();
    }
    
        /**
     * Get the seconds difference
     */
    public static int secondsDiff( Date earlierDate, Date laterDate )
    {
        if( earlierDate == null || laterDate == null ) return 0;
        
        return (int)((laterDate.getTime()/SECOND_MILLIS) - (earlierDate.getTime()/SECOND_MILLIS));
    }

    /**
     * Get the minutes difference
     */
    public static int minutesDiff( Date earlierDate, Date laterDate )
    {
        if( earlierDate == null || laterDate == null ) return 0;
        
        return (int)((laterDate.getTime()/MINUTE_MILLIS) - (earlierDate.getTime()/MINUTE_MILLIS));
    }
    
    /**
     * Get the hours difference
     */
    public static int hoursDiff( Date earlierDate, Date laterDate )
    {
        if( earlierDate == null || laterDate == null ) return 0;
        
        return (int)((laterDate.getTime()/HOUR_MILLIS) - (earlierDate.getTime()/HOUR_MILLIS));
    }
    
    /**
     * Get the days difference
     */
    public static int daysDiff( Date earlierDate, Date laterDate )
    {
        if( earlierDate == null || laterDate == null ) return 0;
        
        return (int)((laterDate.getTime()/DAY_MILLIS) - (earlierDate.getTime()/DAY_MILLIS));
    }

    public static String toTimeString(long time) {
        return ((time < 1300) ? time / 100 : time / 100 - 12)
                + ":" + time % 100
                + ((time < 1200) ? " AM" : " PM");
    }

    public static long getDeltaDays(String date) {
        long deltaDays = 0;

        try {
            Date d = sdf.parse(date);
            deltaDays = (d.getTime() - getCurrentTime()) / MILLI_SEC_PER_DAY;
        } catch (Throwable t) {
            System.out.println(" [Error] Problem parsing date: " + date + ", Exception: " + t.getMessage());
            logger.log(Level.INFO, "101", t);
        }
        return deltaDays;
    }
    // Get  date in given format and default timezone

    public static String getFormatedDate(String format, long timeMS) {
        TimeZone tz = TimeZone.getDefault();
        String date = getFormatedDate(format, timeMS, tz);
        return date;
    }
    
    // Get  date in given format and timezone
    public static String getFormatedDate(String format, long timeMS, TimeZone tmz) {
        SimpleDateFormat sdf = new SimpleDateFormat(format);
        sdf.setTimeZone(tmz);
        String date = sdf.format(new Date(timeMS));
        return date;
    }

    //parse the date string in the given format and timezone to return a date object
    public static Date parseDate(String format, String date) {
        Date dt = null;
        try {
            SimpleDateFormat sdf1 = new SimpleDateFormat(format);
            dt = sdf1.parse(date);
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
        return dt;
    }

    public static Date parseDate(String format, String date, String timeZone) {
        Date dt = null;
        try {
            TimeZone tz;
            SimpleDateFormat sdf1 = new SimpleDateFormat(format);
            if("".compareTo(timeZone)==0){
                tz=TimeZone.getDefault();
            }else{
                tz=TimeZone.getTimeZone(timeZone);
            }
            sdf1.setTimeZone(tz);
            dt = sdf1.parse(date);
            
        } catch (Exception e) {
            logger.log(Level.INFO, "101", e);
        }
        return dt;
    }
    
    public static Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DATE, days); //minus number would decrement the days
        return cal.getTime();
    }

    public static Date addSeconds(Date date, int seconds) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.SECOND, seconds); //minus number would decrement the days
        return cal.getTime();
    }

    public static Date timeToDate(String time) {
        Date startDate=MainAlgorithm.strategyInstances.size()>0?MainAlgorithm.strategyInstances.get(0).getStartDate():TradingUtil.getAlgoDate();
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", startDate.getTime(),TimeZone.getTimeZone(MainAlgorithm.strategyInstances.get(0).getTimeZone()));
        time = currDateStr + " " + time;
        return DateUtil.parseDate("yyyyMMdd HH:mm:ss", time);

    }
    
    public static Date timeToDate(String time,String timeZone){
        Date startDate=MainAlgorithm.strategyInstances.size()>0?MainAlgorithm.strategyInstances.get(0).getStartDate():TradingUtil.getAlgoDate();
        String currDateStr = DateUtil.getFormatedDate("yyyyMMdd", startDate.getTime(),TimeZone.getTimeZone(timeZone));
        time = currDateStr + " " + time;
        return DateUtil.parseDate("yyyyMMdd HH:mm:ss", time);

    }
    
    //Testing routine
    public static void main(String args[]){
        String out=DateUtil.getFormatedDate("yyyy-MM-dd HH:mm:ss",TradingUtil.getAlgoDate().getTime(),TimeZone.getTimeZone("GMT-4:00"));
        System.out.println(out);
        
    }
}
