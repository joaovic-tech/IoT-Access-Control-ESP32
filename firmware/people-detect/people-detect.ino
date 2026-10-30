/*
  Projeto: ESP32-CAM com Sensor PIR e LED
  Descrição: Acende o LED quando o sensor PIR detecta presença/movimento.
  
  Conexões do Circuito:
  - Sensor PIR (ex: HC-SR501):
      * VCC -> Pino 5V do ESP32 / ESP32-CAM-MB
      * GND -> Pino GND
      * OUT -> GPIO 13 do ESP32-CAM
  - LED (Vermelho, Verde, Azul ou Amarelo):
      * Ânodo (perna longa, +) -> GPIO 16 do ESP32-CAM
      * Cátodo (perna curta, -) -> Resistor de 220 Ω -> GND do ESP32
*/

// Definição dos pinos GPIO utilizados
const int PINO_PIR = 13; // Entradas do sensor PIR
const int PINO_LED = 16; // Saída para acionar o LED

// Variável para armazenar a leitura do sensor
int estadoPIR = LOW;

void setup() {
  // Inicializa o Monitor Serial para depuração a 115200 baud
  Serial.begin(115200);
  delay(1000);

  Serial.println("==========================================");
  Serial.println("   Inicializando ESP32-CAM + Sensor PIR   ");
  Serial.println("==========================================");

  // Configura o pino do LED como saída digital
  pinMode(PINO_LED, OUTPUT);
  digitalWrite(PINO_LED, LOW); // Garante que o LED inicie desligado

  // Configura o pino do Sensor PIR como entrada digital
  pinMode(PINO_PIR, INPUT);

  Serial.println("Aguardando estabelização do sensor PIR...");
  delay(5000); // Aguarda alguns segundos para o sensor calibrar ao ligar
  Serial.println("Sistema ativo e monitorando!");
}

void loop() {
  // Lê o sinal digital do sensor PIR (HIGH = Movimento / LOW = Sem Movimento)
  estadoPIR = digitalRead(PINO_PIR);

  if (estadoPIR == HIGH) {
    digitalWrite(PINO_LED, HIGH); // Liga o LED
    Serial.println("🚨 Movimento detectado! LED LIGADO.");
  } else {
    digitalWrite(PINO_LED, LOW);  // Desliga o LED
    Serial.println("🟢 Área limpa. LED DESLIGADO.");
  }

  // Pequena pausa para estabilizar as leituras
  delay(300);
}