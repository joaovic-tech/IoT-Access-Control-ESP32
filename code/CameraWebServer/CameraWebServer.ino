#include <Arduino.h>
#include "esp_camera.h"
#include <WiFi.h>
#include "board_config.h"
#include "iot_access.h"
#include "secrets_config.h"
#include "face_client.h"

#define FW_VERSION "1.1.0"

// ==========================================
// Configuração do Wi-Fi (segredos em arduino_secrets.h -> ver .h.example)
// ==========================================
const char *ssid = WIFI_SSID;
const char *password = WIFI_PASS;

// Cooldown entre disparos automáticos do PIR para o servidor de faces (ms).
static const unsigned long PIR_DETECT_COOLDOWN_MS = 10000;
static unsigned long g_last_pir_detect_sent = 0;

// ==========================================
// Estado Global dos Atuadores e Sensores
// ==========================================
static bool g_flash_state = false;
static int  g_flash_duty = 0;

static bool g_relay_state = false;
static unsigned long g_relay_timer_start = 0;
static unsigned long g_relay_duration = 0;
static bool g_relay_timer_active = false;

static bool g_pir_state = false;
static uint32_t g_pir_count = 0;
static bool g_last_pir_read = false;
static unsigned long g_last_pir_debounce = 0;

static unsigned long g_last_wifi_check = 0;

// Declarações externas do servidor HTTP (app_httpd.cpp)
void startCameraServer();
void setupLedFlash();

// ==========================================
// Implementação das Funções do iot_access.h
// ==========================================

void setFlashLed(bool on, int duty) {
  g_flash_state = on;
  if (on) {
    g_flash_duty = (duty > 0 && duty <= 255) ? duty : 255;
  } else {
    g_flash_duty = 0;
  }
#if defined(LED_GPIO_NUM)
  ledcWrite(LED_GPIO_NUM, g_flash_duty);
#endif
  Serial.printf("[FLASH] Estado: %s | Duty: %d\n", g_flash_state ? "LIGADO" : "DESLIGADO", g_flash_duty);
}

bool getFlashLedState() {
  return g_flash_state;
}

int getFlashLedDuty() {
  return g_flash_duty;
}

void setRelay(bool on) {
  g_relay_state = on;
  g_relay_timer_active = false; // Cancela timer se acionado manualmente
  digitalWrite(RELAY_PIN, g_relay_state ? HIGH : LOW);
  Serial.printf("[RELAY] Estado: %s\n", g_relay_state ? "ATIVADO (ABERTO)" : "DESATIVADO (FECHADO)");
}

void triggerRelay(uint32_t durationMs) {
  g_relay_state = true;
  digitalWrite(RELAY_PIN, HIGH);
  g_relay_timer_start = millis();
  g_relay_duration = durationMs;
  g_relay_timer_active = true;
  Serial.printf("[RELAY] Pulso de abertura ativado por %lu ms\n", (unsigned long)durationMs);
}

bool getRelayState() {
  return g_relay_state;
}

bool getPirState() {
  return g_pir_state;
}

uint32_t getPirCount() {
  return g_pir_count;
}

int getWifiRssi() {
  return WiFi.status() == WL_CONNECTED ? WiFi.RSSI() : -100;
}

uint32_t getUptimeSeconds() {
  return (uint32_t)(millis() / 1000);
}

// ==========================================
// Setup
// ==========================================
void setup() {
  Serial.begin(115200);
  Serial.setDebugOutput(false);
  delay(500);

  Serial.println("\n==========================================");
  Serial.println(" ESP32-CAM: IoT Access Control & Streaming");
  Serial.println("==========================================");

  // 1. Inicialização de GPIOs (PIR, Relé, LED Onboard)
  pinMode(PIR_PIN, INPUT);
  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, LOW); // Relé inicia desligado

  pinMode(ONBOARD_LED_PIN, OUTPUT);
  digitalWrite(ONBOARD_LED_PIN, HIGH); // LED vermelho apagado (Active LOW)

  // 2. Configuração dos parâmetros da câmera OV2640
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
  config.frame_size = FRAMESIZE_VGA;      // Resolução inicial 640x480 (balanceada para stream)
  config.pixel_format = PIXFORMAT_JPEG;   // Streaming MJPEG
  config.grab_mode = CAMERA_GRAB_LATEST;  // Garante stream em tempo real sem atraso acumulado
  config.fb_location = CAMERA_FB_IN_PSRAM;
  config.jpeg_quality = 12;               // 10-14 entrega ótima qualidade com baixo tráfego
  config.fb_count = 2;                    // Double buffer na PSRAM para fluidez

  if (!psramFound()) {
    Serial.println("[AVISO] PSRAM não encontrada! Reduzindo buffer para DRAM interna.");
    config.frame_size = FRAMESIZE_QVGA;
    config.fb_location = CAMERA_FB_IN_DRAM;
    config.fb_count = 1;
    config.jpeg_quality = 15;
    config.grab_mode = CAMERA_GRAB_WHEN_EMPTY;
  }

  // 3. Inicializa o sensor da câmera
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[ERRO] Falha ao inicializar câmera: 0x%x\n", err);
    return;
  }

  sensor_t *s = esp_camera_sensor_get();
  if (s != NULL) {
    if (s->id.PID == OV3660_PID) {
      s->set_vflip(s, 1);
      s->set_brightness(s, 1);
      s->set_saturation(s, -2);
    }
    s->set_framesize(s, FRAMESIZE_VGA); // Define VGA 640x480 por padrão
  }

  // 4. Configuração do Flash LED
#if defined(LED_GPIO_NUM)
  setupLedFlash();
  setFlashLed(false, 0);
#endif

  // 5. Conexão Wi-Fi
  Serial.printf("[WIFI] Conectando a %s ", ssid);
  WiFi.mode(WIFI_STA);
  WiFi.begin(ssid, password);
  WiFi.setSleep(false); // Mantém rádio Wi-Fi sempre ativo para menor latência

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    digitalWrite(ONBOARD_LED_PIN, !digitalRead(ONBOARD_LED_PIN)); // Pisca LED onboard enquanto conecta
    attempts++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    digitalWrite(ONBOARD_LED_PIN, LOW); // LED ligado contínuo (Active LOW) indica conectado
    Serial.println("\n[WIFI] Conectado com sucesso!");
  } else {
    digitalWrite(ONBOARD_LED_PIN, HIGH); // LED apagado indica falha
    Serial.println("\n[WIFI] Falha na conexão inicial! Tentará reconectar no loop.");
  }

  // 6. Inicia os Servidores HTTP (Stream & API) + cliente de reconhecimento facial
  startCameraServer();
  faceClientInit();

  // 7. Exibição das Rotas Disponíveis
  Serial.println("\n==========================================");
  Serial.print  (" ESP32-CAM Pronto! Endereço IP: ");
  Serial.println(WiFi.localIP());
  Serial.println("==========================================");
  Serial.println("Rotas disponíveis para o App Kotlin:");
  Serial.printf (" • Stream MJPEG:    http://%s:81/stream\n", WiFi.localIP().toString().c_str());
  Serial.printf (" • Captura JPEG:    http://%s/capture\n", WiFi.localIP().toString().c_str());
  Serial.printf (" • Status JSON:     http://%s/api/status\n", WiFi.localIP().toString().c_str());
  Serial.printf (" • Controle Flash:  http://%s/api/flash?state=1 (ou state=0)\n", WiFi.localIP().toString().c_str());
  Serial.printf (" • Destravar Acesso:http://%s/api/unlock\n", WiFi.localIP().toString().c_str());
  Serial.printf (" • Escanear (faces):http://%s/api/scan\n", WiFi.localIP().toString().c_str());
  Serial.printf (" • Painel Web:      http://%s/\n", WiFi.localIP().toString().c_str());
  Serial.printf (" • Firmware:        %s | Servidor faces: %s\n", FW_VERSION, FACE_SERVER_URL);
  Serial.println("==========================================\n");
}

// ==========================================
// Loop Principal
// ==========================================
void loop() {
  unsigned long now = millis();

  // 1. Gerenciamento do temporizador não-bloqueante do Relé de Acesso
  if (g_relay_timer_active) {
    if (now - g_relay_timer_start >= g_relay_duration) {
      g_relay_timer_active = false;
      g_relay_state = false;
      digitalWrite(RELAY_PIN, LOW);
      Serial.println("[RELAY] Tempo expirado -> Relé desativado (fechado)");
    }
  }

  // 2. Leitura e debounce do Sensor PIR (GPIO 13)
  bool pir_reading = digitalRead(PIR_PIN);
  if (pir_reading != g_last_pir_read) {
    g_last_pir_debounce = now;
  }

  if ((now - g_last_pir_debounce) > 50) { // Debounce de 50ms
    if (pir_reading != g_pir_state) {
      g_pir_state = pir_reading;
      if (g_pir_state) {
        g_pir_count++;
        Serial.printf("[PIR] Movimento detectado! Total: %u\n", g_pir_count);
        // Borda de subida -> enfileira captura + POST /detect (com cooldown).
        if (now - g_last_pir_detect_sent >= PIR_DETECT_COOLDOWN_MS) {
          if (faceClientRequestDetection("pir")) {
            g_last_pir_detect_sent = now;
            Serial.println("[PIR] Deteccao facial enfileirada");
          }
        }
      } else {
        Serial.println("[PIR] Área desobstruída.");
      }
    }
  }
  g_last_pir_read = pir_reading;

  // 3. Verificação periódica de conexão Wi-Fi (a cada 10 segundos)
  if (now - g_last_wifi_check >= 10000) {
    g_last_wifi_check = now;
    if (WiFi.status() != WL_CONNECTED) {
      Serial.println("[WIFI] Conexão perdida! Tentando reconectar...");
      WiFi.disconnect();
      WiFi.reconnect();
      digitalWrite(ONBOARD_LED_PIN, HIGH);
    } else {
      digitalWrite(ONBOARD_LED_PIN, LOW); // Conectado
    }
  }

  delay(10); // Pequeno respiro para a task do FreeRTOS
}
