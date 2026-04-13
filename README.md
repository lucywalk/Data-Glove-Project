# Data-Glove-Project
Project to measure finger movement for stroke patients from a data glove using the Arduino Nano 33 BLE. The device connects to an app on a phone via BLE, which is coded using Kotlin. The Arduino determines what code to execute by checking for inputs from the app, and setting flags accordingly. The code uses a finger, hand and wrist class with the finger class containing functions to calculate the current angle and maximum angle. As well as this, there is a function to calibrate the sensor readings. The hand class creates five objects of the finger class, enabling these functions to be carried out for all fingers simultaneously. It then sends these parameters as strings sepatarated by commas, so that the commas can be used as delimiters to split the data at the receiving end. The wrist class uses an IMU library to calculate values of roll. A buffer is used to calculate mathematical parameters.

The app has four levels that contain hand rehabilitation exercises to complete and progress from this is tracked, setting a streak, highscore for each level and overall score. Levels are only unlocked once completed, and the maximum bend angle for each finger is tracked and showed in a progress page.  

User Installation Instructions for data glove code:
- Download Arduino IDE
- Set device to Arduino Nano 33 BLE
- Install the ArduinoBLE library by Arduino
- Install the Arduino_LSM9DS1 library by Arduino
- To recalibrate your device, uncomment the calculations for parameters a-e in the setup. This will allow you to calculate a suitable value for m, which needs to be set in the pinch constructor.

User Installation Instructions for app code:
- Install Android Studio

