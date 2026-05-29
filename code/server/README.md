# Server

Neste diretório, foi construída uma API Python utilizando FastAPI para receber fotos do dispositivo IoT via protocolo HTTP.

## Requisitos

- Python 3.7+
- Dependências listadas em `requirements.txt`

## Como executar

1. Instale as dependências:
   ```bash
   pip install -r requirements.txt
   ```
2. Execute o servidor:
   ```bash
   python main.py
   ```

O servidor ficará disponível na porta 8000. As fotos recebidas serão salvas no diretório `uploads/`.

## API Endpoints

- `GET /`: Verifica se o servidor está online.
- `POST /upload`: Recebe a foto (campo `photo`) em formato `multipart/form-data`.
