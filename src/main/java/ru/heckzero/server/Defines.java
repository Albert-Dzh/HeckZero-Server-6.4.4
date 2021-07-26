package ru.heckzero.server;

public class Defines {
    public static final String VERSION = "0.3";
    public static final int PORT = 5190;
    public static final int MAX_PACKET_SIZE = 1500;
    public static final int ENCRYPTION_KEY_SIZE = 32;                                                                                       //password encryption key size (bytes)
    public static final int READ_TIMEOUT = 32;                                                                                              //timeout after witch the client will be disconnected
    public static final String CONF_DIR = "conf";                                                                                           //directory containing configuration files
    public static final String CONF_FILE = "heckzero.xml";                                                                                  //server configuration files
}

