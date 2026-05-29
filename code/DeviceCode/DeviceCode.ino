//SciCraft
#include "esp_camera.h"
#include <WiFi.h>
#include <AsyncMqttClient.h>
#include <esp_timer.h>

// Wi-Fi credentials
const char* ssid = "Lja";
const char* password = "lja98009890";

// MQTT Broker Configuration
const char* mqttHost = "192.168.15.6"; 
const int mqttPort = 1883;
const char* mqttTopic = "esp32/camera";

AsyncMqttClient mqttClient;
TimerHandle_t mqttReconnectTimer;
TimerHandle_t wifiReconnectTimer;

// PIR sensor
#define PIR_PIN 13
unsigned long lastMotionTime = 0;
const unsigned long motionCooldown = 15000; // 15 seconds

// Streaming support
#define CAMERA_MODEL_AI_THINKER
#include "camera_pins.h"
void startCameraServer();

void connectToWifi() {
  Serial.println("Connecting to Wi-Fi...");
  WiFi.begin(ssid, password);
}

void connectToMqtt() {
  Serial.println("Connecting to MQTT...");
  mqttClient.connect();
}

void onMqttConnect(bool sessionPresent) {
  Serial.println("Connected to MQTT.");
}

void onMqttDisconnect(AsyncMqttClientDisconnectReason reason) {
  Serial.println("Disconnected from MQTT.");
  if (WiFi.isConnected()) {
    xTimerStart(mqttReconnectTimer, 0);
  }
}

void sendPhotoMQTT(camera_fb_t * fb) {
  if (!mqttClient.connected()) {
    Serial.println("MQTT not connected, skipping upload");
    return;
  }
  
  // Publish image bytes directly to topic
  // QoS 0, retain false
  uint16_t packetId = mqttClient.publish(mqttTopic, 0, false, (char*)fb->buf, fb->len);
  
  if (packetId > 0) {
    Serial.printf("📸 Photo published to MQTT (ID: %u)\n", packetId);
  } else {
    Serial.println("❌ Failed to publish photo");
  }
}

void onWifiDisconnect(WiFiEvent_t event, WiFiEventInfo_t info) {
  Serial.println("Disconnected from Wi-Fi.");
  xTimerStop(mqttReconnectTimer, 0); 
  xTimerStart(wifiReconnectTimer, 0);
}

void setup() {
  Serial.begin(115200);
  pinMode(PIR_PIN, INPUT);

  mqttReconnectTimer = xTimerCreate("mqttTimer", pdMS_TO_TICKS(2000), pdFALSE, (void*)0, reinterpret_cast<TimerCallbackFunction_t>(connectToMqtt));
  wifiReconnectTimer = xTimerCreate("wifiTimer", pdMS_TO_TICKS(2000), pdFALSE, (void*)0, reinterpret_cast<TimerCallbackFunction_t>(connectToWifi));

  WiFi.onEvent(onWifiDisconnect, ARDUINO_EVENT_WIFI_STA_DISCONNECTED);

  mqttClient.onConnect(onMqttConnect);
  mqttClient.onDisconnect(onMqttDisconnect);
  mqttClient.setServer(mqttHost, mqttPort);

  connectToWifi();
  
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected");
  
  delay(2000); // Wait for power to stabilize
  connectToMqtt();

  camera_config_t config;
  config.ledc_channel = LEDC_CHANNEL_0;
  config.ledc_timer = LEDC_TIMER_0;
  config.pin_d0 = Y2_GPIO_NUM;
  config.pin_d1 = Y3_GPIO_NUM;
  config.pin_d2 = Y4_GPIO_NUM;
  config.pin_d3 = Y5_GPIO_NUM;
  config.pin_d4 = Y6_GPIO_NUM;
  config.pin_d5 = Y7_GPIO_NUM;
  config.pin_d6 = Y8_GPIO_NUM;
  config.pin_d7 = Y9_GPIO_NUM;
  config.pin_xclk = XCLK_GPIO_NUM;
  config.pin_pclk = PCLK_GPIO_NUM;
  config.pin_vsync = VSYNC_GPIO_NUM;
  config.pin_href = HREF_GPIO_NUM;
  config.pin_sccb_sda = SIOD_GPIO_NUM;
  config.pin_sccb_scl = SIOC_GPIO_NUM;
  config.pin_pwdn = PWDN_GPIO_NUM;
  config.pin_reset = RESET_GPIO_NUM;
  config.xclk_freq_hz = 20000000;
  config.pixel_format = PIXFORMAT_JPEG;
  config.frame_size = FRAMESIZE_VGA; // Aumentado de QVGA para VGA (640x480)
  config.jpeg_quality = 4;          // Qualidade melhor (menor número = melhor qualidade no ESP32)
  config.fb_count = 1;               // Mantido 1 para estabilidade de RAM

  if (esp_camera_init(&config) != ESP_OK) {
    Serial.println("Camera init failed");
    return;
  }

  Serial.println("Camera Ready!");
  startCameraServer();
}

void loop() {
  if (digitalRead(PIR_PIN) == HIGH && millis() - lastMotionTime > motionCooldown) {
    camera_fb_t * fb = esp_camera_fb_get();

    lastMotionTime = millis();
    Serial.println("🚨 Motion detected!");

    if (!fb) {
      Serial.println("Camera capture failed");
      return;
    }
    
    sendPhotoMQTT(fb);
    esp_camera_fb_return(fb);
  }
}
