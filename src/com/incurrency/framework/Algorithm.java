package com.incurrency.framework;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import javax.swing.JOptionPane;

/**
 *
 * @author admin
 */
public class Algorithm {

    private final static Logger logger = Logger.getLogger(Algorithm.class.getName());
    private final String delimiter = "_";
    public static Properties globalProperties;
    public static Properties instratInfo=new Properties();
    public static List<String> holidays;
    public static String timeZone;
    public static int openHour=9;
    public static int openMinute=15;
    public static int closeHour=15;
    public static int closeMinute=30;
    public static boolean useForTrading;
    public static boolean useForSimulation;
    public static ConcurrentHashMap<EnumBarSize,Long> databarSetup=new ConcurrentHashMap<>();
    public static AtomicInteger orderidint=new AtomicInteger(0);
    public static boolean useRedis=false;
    public static String redisURL=null;
    public static Database<String,String>db;
    public static String cassandraIP;
    
    public Algorithm(HashMap<String, String> args) throws Exception {
        globalProperties = Utilities.loadParameters(args.get("propertyfile"));
        instratInfo=Utilities.loadParameters(args.get("instratinfo"));           
        
        String holidayFile = globalProperties.getProperty("holidayfile","").toString().trim();
        if (holidayFile != null && !holidayFile.equals("")) {
            File inputFile = new File(holidayFile);
            if (inputFile.exists() && !inputFile.isDirectory()) {
                holidays = Files.readAllLines(Paths.get(holidayFile), StandardCharsets.UTF_8);
            }
        }
        
        useRedis=globalProperties.getProperty("redisurl")!=null?true:false;
        cassandraIP=globalProperties.getProperty("cassandraconnection", "127.0.0.1");
        if(useRedis){
            redisURL=globalProperties.getProperty("redisurl").toString().trim();
            db=new RedisConnect(redisURL.split(":")[0],Utilities.getInt(redisURL.split(":")[1], 6379),Utilities.getInt(redisURL.split(":")[2], 0));
        } 
        
        useForTrading=Boolean.parseBoolean(globalProperties.getProperty("trading","false").toString().trim());
        useForSimulation=Boolean.parseBoolean(globalProperties.getProperty("simulation","false").toString().trim());
        timeZone = globalProperties.getProperty("timezone", "Asia/Kolkata").toString().trim();
        openHour = Integer.valueOf(globalProperties.getProperty("openhour", "9").toString().trim());
        openMinute = Integer.valueOf(globalProperties.getProperty("openminute", "15").toString().trim());
        closeHour = Integer.valueOf(globalProperties.getProperty("closehour", "15").toString().trim());
        closeMinute = Integer.valueOf(globalProperties.getProperty("closeminute", "30").toString().trim());
        boolean symbolfileneeded = Boolean.parseBoolean(globalProperties.getProperty("symbolfileneeded", "false"));
        boolean connectionfileneeded = Boolean.parseBoolean(globalProperties.getProperty("connectionfileneeded", "false"));
        Calendar marketOpenTime=Calendar.getInstance(TimeZone.getTimeZone(Algorithm.timeZone));
        marketOpenTime.set(Calendar.HOUR_OF_DAY, openHour);
        marketOpenTime.set(Calendar.MINUTE, openMinute);
        marketOpenTime.set(Calendar.SECOND, 0);
        marketOpenTime.set(Calendar.MILLISECOND,0);
        
        if (symbolfileneeded) {
            String symbolFileName = globalProperties.getProperty("symbolfile", "symbols.csv").toString().trim();
            File symbolFile = new File(symbolFileName);
            logger.log(Level.FINE, "102, Symbol File, {0}", new Object[]{symbolFileName});
            boolean symbolFileOK = Validator.validateSymbolFile(symbolFileName);
            if (!symbolFileOK) {
                JOptionPane.showMessageDialog(null, "Symbol File did not pass inStrat validation. Please check logs and correct the symbolFile. inStrat will now close.");
                System.exit(0);
            }
            if (symbolFile.exists() && !symbolFile.isDirectory()) {
                new BeanSymbol().reader(symbolFileName, Parameters.symbol);
            } else {
                JOptionPane.showMessageDialog(null, "The specified file containing symbols information could not be found. inStrat will close.");
                System.exit(0);
            }
        }
        
            if (connectionfileneeded) {
                String connectionFileName = globalProperties.getProperty("connectionfile", "connections.csv").toString().trim();
                File connectionFile = new File(connectionFileName);
                logger.log(Level.FINE, "102, Connection File, {0}", new Object[]{connectionFileName});
                boolean connectionFileOK = Validator.validateConnectionFile(connectionFileName);
                if (!connectionFileOK) {
                    JOptionPane.showMessageDialog(null, "Connection File did not pass inStrat validation. Please check logs and correct the connectionFile. inStrat will now close.");
                    System.exit(0);
                }
                if (connectionFile.exists() && !connectionFile.isDirectory()) {
                    new BeanConnection().reader(connectionFileName, Parameters.connection);
                } else {
                    JOptionPane.showMessageDialog(null, "The specified file containing connection information not be found. inStrat will close.");
                    System.exit(0);
                }
            }
        
    }
}
