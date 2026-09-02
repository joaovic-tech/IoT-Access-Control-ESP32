#include "face_client.h"

#include <WiFi.h>
#include <HTTPClient.h>
#include "esp_camera.h"
#include "img_converters.h"
#include "esp_heap_caps.h"
#include "secrets_config.h"

// --- Parâmetros ---
static const uint32_t HTTP_TIMEOUT_MS = 5000;
static const char *DETECT_PATH = "/detect";

// --- Estado da task ---
static TaskHandle_t s_task = nullptr;
static volatile bool s_pending = false;
static volatile bool s_running = false;
static volatile int s_last_status = -1;
static char s_reason[16] = "pir";

// Constrói e envia o multipart. Executa dentro da task (nunca no loop()).
static void send_detection(const char *reason) {
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[FACE] Wi-Fi desconectado -> envio abortado");
    s_last_status = 0;
    return;
  }

  camera_fb_t *fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("[FACE] Falha ao capturar frame");
    s_last_status = 0;
    return;
  }

  // Garante bytes JPEG (a config padrão já é PIXFORMAT_JPEG).
  uint8_t *jpg_buf = fb->buf;
  size_t jpg_len = fb->len;
  bool jpg_allocated = false;
  if (fb->format != PIXFORMAT_JPEG) {
    if (!frame2jpg(fb, 80, &jpg_buf, &jpg_len)) {
      Serial.println("[FACE] Falha ao converter frame para JPEG");
      esp_camera_fb_return(fb);
      s_last_status = 0;
      return;
    }
    jpg_allocated = true;
  }

  String boundary = "----esp32cam" + String((uint32_t)millis(), HEX);
  String head =
    "--" + boundary + "\r\n"
    "Content-Disposition: form-data; name=\"source\"\r\n\r\n" +
    String(reason) + "\r\n"
    "--" + boundary + "\r\n"
    "Content-Disposition: form-data; name=\"file\"; filename=\"esp32cam.jpg\"\r\n"
    "Content-Type: image/jpeg\r\n\r\n";
  String tail = "\r\n--" + boundary + "--\r\n";

  size_t body_len = head.length() + jpg_len + tail.length();
  // Prefere PSRAM; cai para heap interna se necessário.
  uint8_t *body = (uint8_t *)heap_caps_malloc(body_len, MALLOC_CAP_SPIRAM);
  if (!body) {
    body = (uint8_t *)malloc(body_len);
  }
  if (!body) {
    Serial.printf("[FACE] Sem memória para corpo de %u bytes\n", (unsigned)body_len);
    if (jpg_allocated) free(jpg_buf);
    esp_camera_fb_return(fb);
    s_last_status = 0;
    return;
  }

  size_t off = 0;
  memcpy(body + off, head.c_str(), head.length());
  off += head.length();
  memcpy(body + off, jpg_buf, jpg_len);
  off += jpg_len;
  memcpy(body + off, tail.c_str(), tail.length());

  if (jpg_allocated) free(jpg_buf);
  esp_camera_fb_return(fb);

  WiFiClient client;
  HTTPClient http;
  String url = String(FACE_SERVER_URL) + DETECT_PATH;
  http.setConnectTimeout(HTTP_TIMEOUT_MS);
  http.setTimeout(HTTP_TIMEOUT_MS);
  if (!http.begin(client, url)) {
    Serial.println("[FACE] http.begin falhou (URL inválida?)");
    free(body);
    s_last_status = 0;
    return;
  }
  http.addHeader("Content-Type", "multipart/form-data; boundary=" + boundary);

  int code = http.POST(body, body_len);
  s_last_status = code;
  if (code > 0) {
    Serial.printf("[FACE] POST %s (%s) -> HTTP %d\n", url.c_str(), reason, code);
  } else {
    Serial.printf("[FACE] POST %s (%s) -> erro %s\n", url.c_str(), reason, http.errorToString(code).c_str());
  }

  http.end();
  free(body);
}

static void face_task(void *arg) {
  for (;;) {
    if (s_pending) {
      s_running = true;
      char reason[16];
      strncpy(reason, s_reason, sizeof(reason));
      reason[sizeof(reason) - 1] = '\0';
      s_pending = false;
      send_detection(reason);
      s_running = false;
    }
    vTaskDelay(pdMS_TO_TICKS(100));
  }
}

void faceClientInit() {
  if (s_task) return;
  // Stack generosa: HTTPClient + TLS-off ainda usa bastante pilha.
  xTaskCreatePinnedToCore(face_task, "face_client", 6144, nullptr, 1, &s_task, 0);
  Serial.println("[FACE] Task de envio iniciada");
}

bool faceClientRequestDetection(const char *reason) {
  if (s_pending || s_running) return false;
  if (reason && *reason) {
    strncpy(s_reason, reason, sizeof(s_reason));
    s_reason[sizeof(s_reason) - 1] = '\0';
  }
  s_pending = true;
  return true;
}

bool faceClientBusy() {
  return s_pending || s_running;
}

int faceClientLastStatus() {
  return s_last_status;
}
