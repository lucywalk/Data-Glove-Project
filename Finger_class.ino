#include <ArduinoBLE.h> //BLE library
#include <Arduino_LSM9DS1.h> //IMU library

BLEService sensorService("180C");  // arbitrary UUID 
BLEStringCharacteristic dataChar("2A56", BLERead | BLENotify, 100);   //up to 100 chars per update
BLEStringCharacteristic rxChar("abcd", BLEWrite | BLEWriteWithoutResponse, 100); //allows to read values from MIT
unsigned long start_time = 0; 

class rotation{
  public:
    float roll, pitch, yaw; // Gyroscope values yaw and pitch represent arm movement, not relevant to wrist
    static const int n = 20;
    float rbuffer[n];
    int bufferIndex;
    bool bufferFilled;
    float mean;
    float deviation;
    String list_calc;

    rotation(){
      bufferIndex = 0;
      bufferFilled = false;
    }

    void update() {
      if (IMU.gyroscopeAvailable()) {
        IMU.readGyroscope(roll, pitch, yaw);
        rbuffer[bufferIndex] = roll; //fill buffer with roll values
        bufferIndex++;

        if (bufferIndex >= n) { //check if buffer is full
          bufferIndex = 0;
          bufferFilled = true;
        }
      }
    }

    bool isReady() {
      return bufferFilled;
    }

    float find_max(){ //loop through array to find the maximum value
      float max_value = rbuffer[0];
      for(int i = 0; i < n; i++){
        if(rbuffer[i] > max_value){
          max_value = rbuffer[i];
        }
      }
      return max_value;
    }

    float find_min(){ //loop through array to find minimum value
      float min_value = rbuffer[0];
      for(int i = 0; i < n; i++){
        if(rbuffer[i] < min_value){
          min_value = rbuffer[i];
        }
      }
      return min_value;
    }

    float find_mean(){
      float sum = 0;
      for(int i = 0; i<n; i++){
        sum += rbuffer[i];
      }
      mean = sum/n;
      return mean;
    }

    float find_deviation(){ //tremour detection
      float sum = 0;
      float m = find_mean();
      for(int i = 0; i < n; i++){
        float diff = rbuffer[i] - m;
        sum += diff * diff;
      }
      deviation = sqrt(sum/n);
      return deviation;
    }

    String send_IMU(){ 
      float m = find_mean();
      float d = find_deviation();
      float amplitude = find_max() - find_min();
      String movement;
      if(amplitude <10){  
        movement = "Still";
      }
      else if(amplitude < 50){
        movement = "Moving";
      }
      else{
        movement = "Active";
      }

      String stability;
      if(d<20){ 
        stability = "Stable";
      }
      else{
        stability = "Unstable";
      }

      list_calc = String(m) + "," + movement + "," + stability; //send with commas to separate values
      dataChar.writeValue(list_calc.c_str()); //test
      return list_calc;
    }

    void reset() {  //empty buffer
      bufferIndex = 0;
      bufferFilled = false;
    }
};

class finger{ //class to calculate parameters for each finger
  public:
    float base_value; //output when fingers are flat
    int max_value;
    int angle;
    int pin;
    int R = 62;    //fixed resistor value
    float m; 

    finger(int Pin, float M){
      pin = Pin;
      m = M;  //specify sensitivity for each finger
      max_value = 0;  //initialise all values to zero
      angle = 0;
      base_value = 0;
    }

    int find_angle(){
      angle = ((analogRead(pin)/1023.0*R)/(1-analogRead(pin)/1023.0)-base_value)/m; //if dividing by 1023 allows returns an int which will always be zero
      return angle*-1; //to give positive angles
    }

    int find_max(){
      int a = find_angle(); //call angle function so not dependent on this being called before
      if (a>max_value){
        max_value = a;
      }
      return max_value;
    }

    float calibrate(){ //reset y intercept
      base_value = (analogRead(pin)*R/1023.0)/(1-analogRead(pin)/1023.0);
      return base_value;
    }

    void reset_max(){
      max_value = 0;
    }
};

class hand{ //calculates values for all fingers simultaneously using finger class
  public:
  
    finger thumb;
    finger indexf;
    finger middle;
    finger ring;
    finger pinky;
    rotation wrist; //test
    
    String list_angles;
    String list_max;

    //set microcontroller pins and sensitivity for each sensor
    hand(int thumb_pin, float thumb_m, int index_pin, float index_m, int middle_pin, float middle_m, int ring_pin, float ring_m, int pinky_pin, float pinky_m)
    : thumb(thumb_pin, thumb_m), indexf(index_pin, index_m), middle(middle_pin, middle_m),  ring(ring_pin, ring_m), pinky(pinky_pin, pinky_m) {}

    float calibrate_hand(){
      thumb.calibrate();
      indexf.calibrate();
      middle.calibrate();
      ring.calibrate();
      pinky.calibrate();
    }

    void debug_pins() { //used to check if constructor is working
      Serial.print("Thumb pin: ");
      Serial.println(thumb.pin);
      Serial.print("Index pin: ");
      Serial.println(indexf.pin);
    }

    void send_values(){
      unsigned long current_time = (millis() - start_time)/1000; //calculate time from start of program
      list_angles = String(current_time) + "," + String(thumb.find_angle()) + "," + String(indexf.find_angle()) + "," + String(middle.find_angle()) + "," + String(ring.find_angle()) + "," + String(pinky.find_angle()); // + "," + wr;
      dataChar.writeValue(list_angles.c_str());
      Serial.println(list_angles); //print to serial terminal for debugging
    }

    String calculate_max(){ //use MAX so app can distinguish between angles and maximum values
      list_max = "MAX," + String(thumb.find_max()) + "," + String(indexf.find_max()) + "," + String(middle.find_max()) + "," + String(ring.find_max())+ "," + String(pinky.find_max()); //3 values so MIT doesn't crash
      return list_max;
    }

    void send_max(){ //separate function for sending max, as it needs to be constantly updates but not sent
      list_max = calculate_max();
      dataChar.writeValue(list_max.c_str());
      Serial.println(list_max);
    }

    void Reset_max(){
      thumb.reset_max();
      indexf.reset_max();
      middle.reset_max();
      ring.reset_max();
      pinky.reset_max();
    }
};

volatile bool calibrateFlag; //flags to set what mode code is in
volatile bool sendingFlag;
volatile bool wristFlag;
volatile bool exitFlag;


rotation wrist;
hand pinch(A0,0.7,A1,0.67,A2,0.72,A4,0.67,A5,0.9); //set pins and sensitivity
String incoming; //string to check parameters sent to device via BLE

void setup() {
  Serial.begin(9600);
  delay(2000); 
  start_time = millis(); //initialise timer
  while(!Serial);
  if (!IMU.begin()) {
      Serial.println("Failed to initialize IMU!");
      while (1);
    }

  if (!BLE.begin()) {
    Serial.println("Starting BLE failed!");
    while (1);
  }

  BLE.setLocalName("Tiny Ted's Telecom");  //advertise device name
  BLE.setAdvertisedService(sensorService); //advertise data glove
  sensorService.addCharacteristic(dataChar); //add capabilities to service
  sensorService.addCharacteristic(rxChar);
  BLE.addService(sensorService);
  dataChar.writeValue("Ready");
  BLE.advertise();
  Serial.println("Bluetooth active");
  pinch.calibrate_hand();  //automatically calibrate device when turned on
  calibrateFlag = false; //set all flags to false to prevent unanticipated behaviour
  sendingFlag = false;
  wristFlag = false;
  exitFlag = false;
  
  float a = analogRead(A0);  // parameters to caculate sensitivity of sensors (find difference between output when flat and at 90 degrees and then divde by 90)
  float b = analogRead(A1);
  float c = analogRead(A2);
  float d = analogRead(A4);
  float e = analogRead(A5);
  Serial.print(a, 2); Serial.print(",");
  Serial.print(b, 2); Serial.print(",");
  Serial.print(c, 2); Serial.print(",");
  Serial.print(d, 2); Serial.print(",");
  Serial.println(e, 2);

}

void loop() {
  BLEDevice central = BLE.central();

  if (central.connected()) { //check if connected
    String a = rxChar.value(); //read incoming strings

    if (a == "c") { //set flags according to strings
      calibrateFlag = true;
      sendingFlag = false;
      wristFlag = false;
      exitFlag = false;
    }

    else if (a == "s") {
      calibrateFlag = false;
      sendingFlag = true;
      wristFlag = false;
      exitFlag = false;
    }

    else if (a == "e") {
      calibrateFlag = false;
      sendingFlag = false;
      wristFlag = false;
      exitFlag = true;
    }

    else if (a == "w") {
      calibrateFlag = false;
      sendingFlag = false;
      wristFlag = true;
      exitFlag = false;
    }

    else {
      calibrateFlag = false;
      sendingFlag = false;
      wristFlag = false;
      exitFlag = false;
    }

    if(calibrateFlag){
      pinch.calibrate_hand();
    }

    else if(sendingFlag){
      pinch.send_values();
      pinch.calculate_max(); //constantly calculate max
    }

    else if(wristFlag){
      wrist.update();

      if(wrist.isReady()){
        wrist.send_IMU();
        wrist.reset(); // reset for next batch of 20, without this acts as sliding window
      }
    }

    else if(exitFlag){
      delay(200);
      pinch.calculate_max();
      pinch.send_max();
      exitFlag = false; //only send value once
    }

    BLE.poll(); //check for new inputs
  }

  else{
    //if the device disconnects the following code is executed
    BLE.advertise(); //rescan for connection
    start_time = millis(); //reset timer and max values
    pinch.Reset_max();
    calibrateFlag = false;
    sendingFlag = false;
    wristFlag = false;
    exitFlag = false;
  }
}
