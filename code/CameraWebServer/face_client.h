#ifndef FACE_CLIENT_H
#define FACE_CLIENT_H

#include <Arduino.h>

// ==========================================================================
// Cliente HTTP assíncrono que envia um frame JPEG da câmera para o servidor
// Python (FACE_SERVER_URL/detect) via multipart/form-data.
//
// O envio roda numa task FreeRTOS dedicada para NUNCA bloquear o loop() nem o
// stream MJPEG. Falhas de rede são registradas e ignoradas (sem reset).
// ==========================================================================

// Cria a task de envio. Chamar uma vez no setup(), após a câmera e o Wi-Fi.
void faceClientInit();

// Enfileira uma captura + POST /detect. Retorna false se já houver um envio
// pendente (evita enfileirar múltiplos disparos do mesmo evento).
// `reason` é enviado como campo de formulário "source" (ex.: "pir", "scan").
bool faceClientRequestDetection(const char *reason);

// true enquanto um envio está pendente ou em andamento.
bool faceClientBusy();

// Código HTTP da última tentativa (200 = ok, 0 = erro de conexão, -1 = nunca).
int faceClientLastStatus();

#endif // FACE_CLIENT_H
