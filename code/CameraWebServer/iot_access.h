#ifndef IOT_ACCESS_H
#define IOT_ACCESS_H

#include <Arduino.h>

// ==========================================
// Configuração de Pinos de Acesso e Sensores
// ==========================================
#define PIR_PIN          13   // Sensor de Presença PIR (GPIO 13)
// GPIO 14 (HS2_CLK do slot SD, não usado aqui): não é strapping pin, pode ir
// HIGH ou LOW no boot sem afetar a inicialização. Substitui o GPIO 12, que é
// strapping pin (MTDI / seleção de tensão da flash) e trava o boot se ficar
// HIGH com o relé conectado. Ver tasks.md L8/A2.
#define RELAY_PIN        14   // Trava / Relé de Liberação de Acesso (GPIO 14)
#define ONBOARD_LED_PIN  33   // LED vermelho embutido (Active LOW no ESP32-CAM)

// ==========================================
// Protótipos de Funções de Controle e Estado
// ==========================================
void setFlashLed(bool on, int duty = 255);
bool getFlashLedState();
int  getFlashLedDuty();

void setRelay(bool on);
void triggerRelay(uint32_t durationMs = 3000);
bool getRelayState();

bool getPirState();
uint32_t getPirCount();

int  getWifiRssi();
uint32_t getUptimeSeconds();

#endif // IOT_ACCESS_H
