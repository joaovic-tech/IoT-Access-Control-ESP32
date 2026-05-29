## Testar projeto

1. Inicie o Mosquitto (se ainda não estiver rodando):

```bash
sudo systemctl start mosquitto
```

2. Inicie o servidor:

```bash
cd code/server
python main.py
```

Você deve ver algo como:

```bash
⏳ Carregando banco de rostos conhecidos...
   - Rosto carregado: joao-frente
   - Rosto carregado: joao-direita
   ...
✅ Conectado ao Broker MQTT!
🚀 Servidor MQTT aguardando imagens no tópico 'esp32/camera'...
```

3. Ligue o ESP32-CAM e passe na frente dele!

O servidor deve printar o resultado do reconhecimento assim que receber a foto. Me diz como foi! 🚀
