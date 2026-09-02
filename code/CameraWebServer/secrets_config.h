#ifndef SECRETS_CONFIG_H
#define SECRETS_CONFIG_H
// ==========================================================================
// Resolucao dos segredos de configuracao.
//
// Prioridade:
//   1. arduino_secrets.h  (arquivo real, ignorado no git)
//   2. arduino_secrets.h.example  (fallback documentado, gera #warning)
//
// Depois garante valores padrao seguros para qualquer macro que ainda
// esteja indefinida, para que o firmware SEMPRE compile.
// ==========================================================================

#if defined(__has_include)
#  if __has_include("arduino_secrets.h")
#    include "arduino_secrets.h"
#  else
#    warning "arduino_secrets.h nao encontrado -> usando arduino_secrets.h.example. Crie o arquivo real antes de gravar na placa."
#    include "arduino_secrets.h.example"
#  endif
#else
#  include "arduino_secrets.h"
#endif

#ifndef WIFI_SSID
#define WIFI_SSID "CHANGE_ME_SSID"
#endif

#ifndef WIFI_PASS
#define WIFI_PASS "CHANGE_ME_PASS"
#endif

#ifndef FACE_SERVER_URL
#define FACE_SERVER_URL "http://192.168.15.10:8000"
#endif

// ACCESS_TOKEN e opcional: quando ausente, a checagem de token e desativada.

#endif // SECRETS_CONFIG_H
