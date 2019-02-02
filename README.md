# SimpleUsbTerminal

This Android app provides a line-oriented terminal / console for devices with a serial / UART interface connected with a USB-to-serial-converter.

It supports USB to serial converters based on
- FTDI FT232, FT2232, ...
- Prolific PL2303
- Silabs CP2102, CP2105, ...
- Qinheng CH340

and devices implementing the USB CDC protocol like
- Arduino using ATmega32U4
- Digispark using V-USB software USB
- BBC micro:bit using ARM mbed DAPLink firmware

## How to start

The app uses the [usb-serial-for-android](https://github.com/kai-morich/usb-serial-for-android) library, 
which is unfortunately not available in jcenter or maven-central repositories, so you have to add it manually.
Copy usbSerialForAndroid folder from usb-serial-for-android project into folder containing this README.md

## Motivation

I got various requests asking for help with Android development or source code for my
[Serial USB Terminal](https://play.google.com/store/apps/details?id=de.kai_morich.serial_usb_terminal) app.
Here you find a simplified version of my app.
