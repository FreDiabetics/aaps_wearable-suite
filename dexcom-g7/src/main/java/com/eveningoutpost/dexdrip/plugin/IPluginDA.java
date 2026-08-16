/*
 * Derived from NightscoutFoundation/xDrip IPluginDA at commit
 * 66eb3a17063a21b8dff344719e5e72a7decbc1a6. Modified for Sugarlicious by
 * removing the Android annotation dependency. Licensed under GPL-3.0-or-later.
 */
package com.eveningoutpost.dexdrip.plugin;

public interface IPluginDA {
    byte[][] aNext();
    byte[][] bNext();
    byte[][] cNext();
    void amConnected();
    boolean bondNow(byte[] data);
    boolean receivedResponse(byte[] data);
    boolean receivedResponse2(byte[] data);
    boolean receivedResponse3(byte[] data);
    boolean receivedData(byte[] data);
    boolean receivedData2(byte[] data);
    boolean receivedData3(byte[] data);
    byte[] getPersistence(int channel);
    boolean setPersistence(int channel, byte[] data);
    String getStatus();
    String getName();
}
