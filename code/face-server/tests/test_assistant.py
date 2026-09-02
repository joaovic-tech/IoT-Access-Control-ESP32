"""Testes do assistente do administrador (G3).

O Ollama entra por um duble: o que importa testar e que o modelo apenas ESCOLHE
a intencao, que a execucao e codigo comum, e -- o ponto de seguranca -- que
nenhuma escrita acontece sem confirmacao explicita.
"""
from __future__ import annotations

import json

import assistant
import people_store
import rooms_store


class FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        return None

    def json(self):
        return {"response": json.dumps(self._payload, ensure_ascii=False)}


def _liga_modelo(monkeypatch, payload, capturadas=None):
    monkeypatch.setattr(assistant, "OLLAMA_ENABLED", True)
    monkeypatch.setattr(assistant, "OLLAMA_MODEL", "llama3.1")

    def fake_post(url, json=None, timeout=None):
        if capturadas is not None:
            capturadas.append(json)
        return FakeResponse(payload)

    monkeypatch.setattr(assistant.requests, "post", fake_post)


def test_desligado_avisa_em_vez_de_falhar(monkeypatch):
    monkeypatch.setattr(assistant, "OLLAMA_ENABLED", False)
    resposta = assistant.responder("quem entrou na cozinha?")

    assert resposta["ok"] is False
    assert "OLLAMA_MODEL" in resposta["answer"]


def test_consulta_ao_historico_e_respondida_na_hora(monkeypatch):
    import detections_store

    detections_store.save(b"jpeg", "recognized", person="Antonio", room="Cozinha")
    detections_store.save(b"jpeg", "recognized", person="Joao", room="Garagem")

    _liga_modelo(
        monkeypatch,
        {"acao": "consultar_deteccoes", "comodo": "Cozinha"},
    )
    resposta = assistant.responder("quem entrou na cozinha?")

    assert resposta["ok"] is True
    assert resposta["action"] == "consultar_deteccoes"
    assert len(resposta["detections"]) == 1
    assert resposta["detections"][0]["person"] == "Antonio"
    # Leitura pura: nao existe proposta a confirmar.
    assert "proposal" not in resposta


def test_filtro_de_negados(monkeypatch):
    import detections_store

    detections_store.save(b"j", "recognized", person="Antonio", authorized=False)
    detections_store.save(b"j", "recognized", person="Joao", authorized=True)

    _liga_modelo(monkeypatch, {"acao": "consultar_deteccoes", "apenas_negados": True})
    resposta = assistant.responder("quais acessos foram negados?")

    assert len(resposta["detections"]) == 1
    assert resposta["detections"][0]["person"] == "Antonio"


def test_contexto_leva_os_nomes_reais_ao_modelo(monkeypatch):
    """Sem os nomes reais o modelo inventaria pessoas e comodos."""
    rooms_store.create_room("Garagem")
    people_store.create_person("Antonio", "Visitante")

    capturadas: list = []
    _liga_modelo(monkeypatch, {"acao": "nao_entendi", "motivo": "..."}, capturadas)
    assistant.responder("qualquer coisa")

    prompt = capturadas[0]["prompt"]
    assert "Antonio" in prompt
    assert "Garagem" in prompt
    assert "Visitante" in prompt


# ==========================================================================
# Escrita: sempre proposta, nunca acao direta
# ==========================================================================
def test_mudanca_de_categoria_vira_proposta_e_nao_altera_nada(monkeypatch):
    antonio = people_store.create_person("Antonio", "Visitante", authorized=True)

    _liga_modelo(
        monkeypatch,
        {"acao": "alterar_categoria", "pessoa": "Antonio", "categoria": "Morador"},
    )
    resposta = assistant.responder("mude o Antonio para morador")

    assert resposta["requires_confirmation"] is True
    assert resposta["proposal"]["category"] == "Morador"
    # Nada mudou ainda: a pessoa continua na categoria antiga.
    assert people_store.get_person(antonio.id).category == "Visitante"


def test_proposta_confirmada_e_aplicada(monkeypatch):
    antonio = people_store.create_person("Antonio", "Visitante", authorized=True)

    _liga_modelo(
        monkeypatch,
        {"acao": "alterar_categoria", "pessoa": "Antonio", "categoria": "Morador"},
    )
    proposta = assistant.responder("mude o Antonio")["proposal"]

    resultado = assistant.aplicar(proposta)
    assert resultado["ok"] is True
    assert people_store.get_person(antonio.id).category == "Morador"


def test_liberar_comodo_vira_proposta(monkeypatch):
    garagem = rooms_store.create_room("Garagem")
    antonio = people_store.create_person("Antonio", "Prestador")

    _liga_modelo(
        monkeypatch,
        {"acao": "liberar_comodo", "pessoa": "Antonio", "comodo": "Garagem"},
    )
    resposta = assistant.responder("libere o Antonio na garagem")

    assert resposta["requires_confirmation"] is True
    assert people_store.get_person(antonio.id).rooms == []

    assistant.aplicar(resposta["proposal"])
    assert people_store.get_person(antonio.id).rooms == [garagem.id]


def test_pessoa_inexistente_nao_gera_proposta(monkeypatch):
    _liga_modelo(
        monkeypatch,
        {"acao": "alterar_categoria", "pessoa": "Fantasma", "categoria": "Morador"},
    )
    resposta = assistant.responder("promova o Fantasma")

    assert resposta["ok"] is False
    assert "Fantasma" in resposta["answer"]
    assert "proposal" not in resposta


def test_pedido_fora_do_escopo_nao_inventa_acao(monkeypatch):
    _liga_modelo(monkeypatch, {"acao": "nao_entendi", "motivo": "pedido vago"})
    resposta = assistant.responder("abra a porta agora")

    assert resposta["ok"] is False
    assert resposta["action"] == "nao_entendi"


def test_assistente_nunca_decide_acesso(monkeypatch):
    """Nenhuma acao do assistente destranca porta ou autoriza deteccao."""
    acoes = {
        assistant.ACAO_CONSULTAR,
        assistant.ACAO_ALTERAR_CATEGORIA,
        assistant.ACAO_LIBERAR_COMODO,
        assistant.ACAO_NAO_ENTENDI,
    }
    # O conjunto de acoes e fechado e nao inclui destravar nem autorizar.
    assert not any("unlock" in a or "autoriz" in a for a in acoes)
    assert assistant.aplicar({"acao": "destravar_porta"})["ok"] is False


def test_falha_do_ollama_nao_derruba_a_rota(monkeypatch):
    monkeypatch.setattr(assistant, "OLLAMA_ENABLED", True)
    monkeypatch.setattr(assistant, "OLLAMA_MODEL", "llama3.1")

    def boom(url, json=None, timeout=None):
        raise assistant.requests.ConnectionError("sem rede")

    monkeypatch.setattr(assistant.requests, "post", boom)
    resposta = assistant.responder("quem entrou hoje?")

    assert resposta["ok"] is False
    assert "sem rede" in assistant.LAST_ERROR


def test_nome_com_acento_casa_com_o_do_modelo(monkeypatch):
    """O modelo devolve "Antonio"; o cadastro tem "Antônio"."""
    people_store.create_person("Antônio", "Visitante", authorized=True)

    _liga_modelo(
        monkeypatch,
        {"acao": "alterar_categoria", "pessoa": "Antonio", "categoria": "Morador"},
    )
    resposta = assistant.responder("mude o Antonio")

    assert resposta["ok"] is True
    assert resposta["requires_confirmation"] is True


def test_consulta_por_comodo_com_acento(monkeypatch):
    import detections_store

    detections_store.save(b"j", "recognized", person="Antônio", room="Cozinha")
    _liga_modelo(monkeypatch, {"acao": "consultar_deteccoes", "pessoa": "Antonio"})

    assert len(assistant.responder("o que o Antonio fez?")["detections"]) == 1
