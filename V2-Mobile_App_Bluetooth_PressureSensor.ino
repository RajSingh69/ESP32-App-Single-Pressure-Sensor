#include "BluetoothSerial.h"

// I AM CODE EDIT ME LEARN HOW TO DO COMMENT BLOCKS AND STUFF NIGHTTTTTT

BluetoothSerial SerialBT;
const int LED_PIN = 2;  // Built-in LED pin on ESP32

#define FORCE_SENSOR_PIN 35 // ESP32 pin GIOP35 (ADC1)

// Define the pins for the LEDs
#define LED1_PIN 5 
#define LED2_PIN 18 
#define LED3_PIN 19 
#define LED4_PIN 21 

void setup() {
  Serial.begin(9600);
  SerialBT.begin("esp32"); // Bluetooth device name
  Serial.println("The device started, now you can pair it with Bluetooth!");

  // Set LED pins as outputs
  pinMode(LED1_PIN, OUTPUT);
  pinMode(LED2_PIN, OUTPUT);
  pinMode(LED3_PIN, OUTPUT);
  pinMode(LED4_PIN, OUTPUT);
}

void loop() {
  int analogReading = analogRead(FORCE_SENSOR_PIN);

  Serial.print("The force sensor value = ");
  Serial.println(analogReading); // print the raw analog reading

  // Send the reading over Bluetooth
  SerialBT.print(analogReading);
  SerialBT.print('\n'); // Adding a newline for easier parsing

  int brightness1 = 0, brightness2 = 0, brightness3 = 0, brightness4 = 0;

  if (analogReading < 10) {       // from 0 to 9
    Serial.println(" -> no pressure");
    brightness1 = brightness2 = brightness3 = brightness4 = 0;
  } else if (analogReading < 200) { // from 10 to 199
    Serial.println(" -> light touch");
    brightness1 = map(analogReading, 10, 199, 0, 255);
  } else if (analogReading < 500) { // from 200 to 499
    Serial.println(" -> light squeeze");
    brightness1 = 255;
    brightness2 = map(analogReading, 200, 499, 0, 255);
  } else if (analogReading < 800) { // from 500 to 799
    Serial.println(" -> medium squeeze");
    brightness1 = 255;
    brightness2 = 255;
    brightness3 = map(analogReading, 500, 799, 0, 255);
  } else { // from 800 to 1023
    Serial.println(" -> big squeeze");
    brightness1 = 255;
    brightness2 = 255;
    brightness3 = 255;
    brightness4 = map(analogReading, 800, 1023, 0, 255);
  }

  // Set LED brightnesses
  analogWrite(LED1_PIN, brightness1);
  analogWrite(LED2_PIN, brightness2);
  analogWrite(LED3_PIN, brightness3);
  analogWrite(LED4_PIN, brightness4);

  delay(1000);
}