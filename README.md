# Data-Glove-Project
Project to measure finger movement for stroke patients from a data glove using the Arduino Nano 33 BLE. The device connects to an app on a phone via BLE, which is coded using Kotlin. The Arduino determines what code to execute by checking for inputs from the app, and setting flags accordingly. The code uses a finger, hand and wrist class with the finger class containing functions to calculate the current angle and maximum angle. As well as this, there is a function to calibrate the sensor readings. The hand class creates five objects of the finger class, enabling these functions to be carried out for all fingers simultaneously. It then sends these parameters as strings sepatarated by commas, so that the commas can be used as delimiters to split the data at the receiving end. The wrist class uses an IMU library to calculate values of roll. A buffer is used to calculate mathematical parameters.

The app has four levels that contain hand rehabilitation exercises to complete and progress from this is tracked, setting a streak, highscore for each level and overall score. Levels are only unlocked once completed, and the maximum bend angle for each finger is tracked and showed in a progress page.  

**Data Glove Code**
User Installation Instructions for data glove code:
- Download Arduino IDE
- Set device to Arduino Nano 33 BLE
- Install the ArduinoBLE library by Arduino (https://docs.arduino.cc/libraries/arduinoble/)
- Install the Arduino_LSM9DS1 library by Arduino (https://github.com/arduino-libraries/Arduino_LSM9DS1)
- To recalibrate your device, uncomment the calculations for parameters a-e in the setup. This will allow you to calculate a suitable value for m, which needs to be set in the pinch constructor.

How to run the code:
- Verify and upload to Arduino via USB cable.
- For the code to run, the serial monitor needs to be open. This will display 'Bluetooth Active' once the code is uploaded and the Arduino is ready to connect. The serial monitor can also be used for debugging.
- If having issues with the BLE communication, the LightBlue app (available on the android app store) is useful. This lists all available Bluetooth devices and can connect to them using their name for identification.

Technical Details:
- Angle calculated by combining and rearranging following equations:
     𝑉_𝑜𝑢𝑡=𝑎𝑛𝑎𝑙𝑜𝑔𝑅𝑒𝑎𝑑×𝑉_𝑖𝑛/1023  (conversion of pin reading to voltage)
     𝑅_𝑓𝑙𝑒𝑥=𝑚×𝑎𝑛𝑔𝑙𝑒+𝑐            (flex sensor characterisation)
     𝑉_𝑜𝑢𝑡=(𝑅𝑉_𝑖𝑛)/(𝑅_𝑓𝑙𝑒𝑥+𝑅)   (voltage divider)
- Calibration resets y intercept (c) in flex sensor charcaterisation. This is calculated by rearranging the angle calulation and setting the angle equal to zero.

**Kotlin App Code**
User Installation Instructions for app code:
- Install Android Studio
- Create a new project with an empty activity
- Edit the AndroidManifest.xml to add the following permissions:
    <uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
- Create a new class/file within your package called BLEManager.kt and copy the BLEManager code into this.
- To use the streak function, the API level will need to be increased. To do this, replace the build.gradle code with the code provided.

How to run the code:
- If your computer has low RAM, close all other applications, otherwise the code will be very slow to run.
- You can run the device using the emulator. To do this, select which device you want to emulate and download its template. Then run the code, with this device selected. For this project, the Google Pixel 3a was used.
- To run the app on a phone, go to settings -> about phone -> build number. Tap build number 7 times and then you will see 'you are now a developer'.
- Then go to settings -> system -> developer options and enable USB debugging.
- Connect your computer to your phone via USB and you will be prompted to allow USB debugging each time you do this. Once you do, this will show up as a device option to run the code on.
- Within app permissions, the location needs to be enabled for the BLE communication to work.

Future Improvements:
- Develop more levels based on stroke rehabilitation exercises.



