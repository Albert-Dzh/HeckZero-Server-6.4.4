package ru.heckzero.server.items;

public class ItemsDct {
    public static int getBaseType(double type) {return (int)Math.floor(type);}

    public static final int MONEY_COPP = 1;                                                                                                 //money copper
    public static final int MONEY_SILV = 2;                                                                                                 //money silver
    public static final int MONEY_GOLD = 3;                                                                                                 //money gold

    public static final double TYPE_BLD_KEY = 782;                                                                                          //building key-card base type
    public static final double TYPE_BLD_KEY_COPY = 782.1;                                                                                   //building key-card copy


    public static final double TYPE_BANK_KEY = 786;
    public static final double TYPE_BANK_KEY_COPY = 786.2;

    public static final double TYPE_PASSPORT = 778;                                                                                         //Passport
    public static final double TYPE_VIP_CARD = 856;                                                                                         //VIP CARD
}
