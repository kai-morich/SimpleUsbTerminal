package de.kai_morich.simple_usb_terminal;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDeviceConnection;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.security.InvalidParameterException;

public class SerialSocket implements SerialInputOutputManager.Listener {

    private static final int WRITE_WAIT_MILLIS = 2000; // 0 blocked infinitely on unprogrammed arduino

    private final BroadcastReceiver disconnectBroadcastReceiver;

    private final Context context;
    private SerialListener listener;
    private UsbDeviceConnection connection;
    private UsbSerialPort serialPort;
    private SerialInputOutputManager ioManager;

    SerialSocket(Context context, UsbDeviceConnection connection, UsbSerialPort serialPort) {
        if(context instanceof Activity)
            throw new InvalidParameterException("expected non UI context");
        this.context = context;
        this.connection = connection;
        this.serialPort = serialPort;
        disconnectBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (listener != null)
                    listener.onSerialIoError(new IOException("background disconnect"));
                disconnect(); // disconnect now, else would be queued until UI re-attached
            }
        };
    }

    String getName() { return serialPort.getDriver().getClass().getSimpleName().replace("SerialDriver",""); }

    void connect(SerialListener listener) throws IOException {
        final int XR_SET_REG = 0x40;
        final int XR_REQ = 0x00;

        // index is 2 byte; LSB -> register, MSB -> block (shifted 8)
        final int XR_BLOCK_UART = 0x00;
        final int XR_BLOCK_UART_MANAGER = 0x04 << 8;
        final int XR_BLOCK_I2C_EEPROM = 0x65 << 8;
        final int XR_BLOCK_UART_CUSTOM = 0x66 << 8;

        final int XR_REG_FIFO_ENABLE = 0x10;
        final int XR_REG_UART_ENABLE = 0x03;

        // buffer will always be null, length 0

        // following is the sequence of control transfers sniffed from ExarUSB_android_app_ver1C.apk

        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x00, XR_BLOCK_UART_MANAGER | XR_REG_FIFO_ENABLE, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x00, XR_REG_UART_ENABLE, null, 0, 5000);

        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0xA0, 0x04, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x01, 0x05, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x00, 0x06, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x6d, 0x07, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x0b, 0x08, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x6a, 0x09, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x0b, 0x0a, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x08, 0x0b, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x00, 0x0c, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x0b, 0x1a, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x00, 0x12, null, 0, 5000);

        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x00, 0x03 | XR_BLOCK_UART_CUSTOM, null, 0, 5000);

        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x01, 0x18 | XR_BLOCK_UART_MANAGER, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x01, 0x1c | XR_BLOCK_UART_MANAGER, null, 0, 5000);

        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x01, XR_BLOCK_UART_MANAGER | XR_REG_FIFO_ENABLE, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x03, XR_REG_UART_ENABLE, null, 0, 5000);
        this.connection.controlTransfer(XR_SET_REG, XR_REQ, 0x03, XR_BLOCK_UART_MANAGER | XR_REG_FIFO_ENABLE, null, 0, 5000);

        this.listener = listener;
        context.registerReceiver(disconnectBroadcastReceiver, new IntentFilter(Constants.INTENT_ACTION_DISCONNECT));
        ioManager = new SerialInputOutputManager(serialPort, this);
        ioManager.start();
    }

    void disconnect() {
        listener = null; // ignore remaining data and errors
        if (ioManager != null) {
            ioManager.setListener(null);
            ioManager.stop();
            ioManager = null;
        }
        if (serialPort != null) {
            try {
                serialPort.setDTR(false);
                serialPort.setRTS(false);
            } catch (Exception ignored) {
            }
            try {
                serialPort.close();
            } catch (Exception ignored) {
            }
            serialPort = null;
        }
        if(connection != null) {
            connection.close();
            connection = null;
        }
        try {
            context.unregisterReceiver(disconnectBroadcastReceiver);
        } catch (Exception ignored) {
        }
    }

    void write(byte[] data) throws IOException {
        if(serialPort == null)
            throw new IOException("not connected");
        serialPort.write(data, WRITE_WAIT_MILLIS);
    }

    @Override
    public void onNewData(byte[] data) {
        if(listener != null)
            listener.onSerialRead(data);
    }

    @Override
    public void onRunError(Exception e) {
        if (listener != null)
            listener.onSerialIoError(e);
    }
}
