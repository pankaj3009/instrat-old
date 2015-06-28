package com.incurrency.framework;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
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
    public static List<String> holidays;
    public static String timeZone;
    public static int openHour;
    public static int openMinute;
    public static int closeHour;
    public static int closeMinute;
    public static boolean useForTrading;

    public Algorithm(HashMap<String, String> args) throws Exception {
        globalProperties = Utilities.loadParameters(args.get("propertyfile"));
        String holidayFile = globalProperties.getProperty("holidayfile").toString().trim();
        if (holidayFile != null) {
            File inputFile = new File(holidayFile);
            if (inputFile.exists() && !inputFile.isDirectory()) {
                holidays = Files.readAllLines(Paths.get(holidayFile), StandardCharsets.UTF_8);
            }
        }
        useForTrading=Boolean.parseBoolean(globalProperties.getProperty("trading","false").toString().trim());
        timeZone = globalProperties.getProperty("timezone", "Asia/Kolkata").toString().trim();
        openHour = Integer.valueOf(globalProperties.getProperty("openhour", "9").toString().trim());
        openMinute = Integer.valueOf(globalProperties.getProperty("openminute", "15").toString().trim());
        closeHour = Integer.valueOf(globalProperties.getProperty("closehour", "15").toString().trim());
        closeMinute = Integer.valueOf(globalProperties.getProperty("closeminute", "30").toString().trim());
        boolean symbolfileneeded = Boolean.parseBoolean(globalProperties.getProperty("symbolfileneeded", "false"));
        boolean connectionfileneeded = Boolean.parseBoolean(globalProperties.getProperty("connectionfileneeded", "false"));
        if (symbolfileneeded) {
            String symbolFileName = globalProperties.getProperty("symbolfile", "symbols.csv").toString().trim();
            File symbolFile = new File(symbolFileName);
            logger.log(Level.FINE, "102, Symbol File, {0}", new Object[]{symbolFileName});
            boolean symbolFileOK = Validator.validateSymbolFile(args);
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
                boolean connectionFileOK = Validator.validateConnectionFile(args);
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