package i.mrhua269.cyanidin.common;

import org.slf4j.Logger;

public class EntryPoint {
    public static Logger LOGGER_INST;

    public static void initLogger(Logger logger){
        LOGGER_INST = logger;
    }
}
