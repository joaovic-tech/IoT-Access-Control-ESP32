import paho.mqtt.client as mqtt
import os
import time
import face_recognition
import numpy as np
from PIL import Image, ImageOps
import io

# Configurações do Broker
BROKER = "localhost"  # O Python está na mesma máquina que o Mosquitto
PORT = 1883
TOPIC = "esp32/camera"
UPLOAD_DIR = "uploads_mqtt"
KNOWN_FACES_DIR = "known_faces"

# Criar pastas se não existirem
for directory in [UPLOAD_DIR, KNOWN_FACES_DIR]:
    if not os.path.exists(directory):
        os.makedirs(directory)

def load_image_with_exif_fix(path):
    """Carrega imagem corrigindo a rotação EXIF (fotos de celular)."""
    pil_image = Image.open(path)
    # Corrige a orientação baseada nos metadados EXIF
    pil_image = ImageOps.exif_transpose(pil_image)
    # Converte para RGB (remove canal alpha se existir)
    pil_image = pil_image.convert("RGB")
    return np.array(pil_image)

# Função para carregar os rostos conhecidos
def load_known_faces():
    known_encodings = []
    known_names = []
    
    print("⏳ Carregando banco de rostos conhecidos...")
    for filename in sorted(os.listdir(KNOWN_FACES_DIR)):
        if filename.lower().endswith(('.png', '.jpg', '.jpeg')):
            path = os.path.join(KNOWN_FACES_DIR, filename)
            image = load_image_with_exif_fix(path)
            
            encodings = face_recognition.face_encodings(image)
            if encodings:
                known_encodings.append(encodings[0])
                name = os.path.splitext(filename)[0]
                known_names.append(name)
                print(f"   ✅ Rosto carregado: {name}")
            else:
                print(f"   ⚠️ Nenhum rosto encontrado na imagem: {filename}")
                
    print(f"📊 Total de rostos carregados: {len(known_encodings)}")
    return known_encodings, known_names

known_face_encodings, known_face_names = load_known_faces()

def on_connect(client, userdata, flags, reason_code, properties):
    if reason_code == 0:
        print("✅ Conectado ao Broker MQTT!")
        client.subscribe(TOPIC)
    else:
        print(f"❌ Erro na conexão. Código: {reason_code}")

def on_message(client, userdata, msg):
    filename = f"photo_{int(time.time())}.jpg"
    file_path = os.path.join(UPLOAD_DIR, filename)
    
    with open(file_path, "wb") as f:
        f.write(msg.payload)
        
    print(f"\n📸 Foto recebida via MQTT e salva: {filename} ({len(msg.payload)} bytes)")
    
    print("🔍 Iniciando reconhecimento facial...")
    try:
        unknown_image = face_recognition.load_image_file(file_path)
        # number_of_times_to_upsample=2 melhora detecção em imagens de baixa resolução (ESP32-CAM)
        face_locations = face_recognition.face_locations(unknown_image, number_of_times_to_upsample=2)
        face_encodings = face_recognition.face_encodings(unknown_image, face_locations)
        
        if not face_encodings:
            print("❌ Nenhum rosto detectado na imagem recebida.")
            return

        for face_encoding in face_encodings:
            matches = face_recognition.compare_faces(known_face_encodings, face_encoding, tolerance=0.6)
            name = "Desconhecido"

            if True in matches:
                # Usa a menor distância para encontrar o melhor match
                face_distances = face_recognition.face_distance(known_face_encodings, face_encoding)
                best_match_index = np.argmin(face_distances)
                if matches[best_match_index]:
                    name = known_face_names[best_match_index]
                    distance = face_distances[best_match_index]
                    print(f"👤 Reconhecimento Concluído: Rosto correspondente a '{name}' (Distância: {distance:.2f})")
            else:
                print("❌ Rosto desconhecido detectado.")
                
    except Exception as e:
        print(f"⚠️ Erro ao processar o reconhecimento facial: {e}")

client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
client.on_connect = on_connect
client.on_message = on_message

print(f"🚀 Servidor MQTT aguardando imagens no tópico '{TOPIC}'...")
try:
    client.connect(BROKER, PORT, 60)
    client.loop_forever()
except ConnectionRefusedError:
    print("❌ Erro: Não foi possível conectar ao Broker MQTT. Verifique se o Mosquitto está rodando.")
except KeyboardInterrupt:
    print("\n🛑 Servidor encerrado.")
