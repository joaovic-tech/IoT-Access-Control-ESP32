"""G3 -- assistente do administrador em linguagem natural (Ollama local).

Onde o modelo de linguagem entra neste projeto -- e onde NAO entra.

NAO entra: a decisao de acesso. Essa e uma tabela deterministica em
`access_control.py`, e a frase mostrada no app vem de template. Ver o principio
de arquitetura em tasks.md secao 3.2.

Entra: traduzir portugues em CONSULTA ao historico ou em PROPOSTA de mudanca de
configuracao. O modelo so escolhe a intencao e os parametros; quem executa e
codigo comum, e toda escrita passa por confirmacao explicita do administrador.

    "quem entrou na cozinha ontem a noite?"  -> consulta, respondida na hora
    "libere o Antonio na garagem"            -> proposta, aplicada so apos OK

Assim o pior erro possivel do modelo e responder a pergunta errada ou propor a
mudanca errada -- nunca abrir uma porta.
"""
from __future__ import annotations

import json
from typing import Any

import requests

import detections_store
import people_store
import rooms_store
from detections_store import slugify
from config import OLLAMA_ENABLED, OLLAMA_MODEL, OLLAMA_TIMEOUT, OLLAMA_URL

LAST_ERROR: str = ""

ACAO_CONSULTAR = "consultar_deteccoes"
ACAO_ALTERAR_CATEGORIA = "alterar_categoria"
ACAO_LIBERAR_COMODO = "liberar_comodo"
ACAO_NAO_ENTENDI = "nao_entendi"

_SYSTEM_PROMPT = (
    "Voce interpreta pedidos do administrador de um sistema de controle de "
    "acesso e devolve SOMENTE um objeto JSON com a chave \"acao\" e os "
    "parametros dela. Acoes possiveis:\n"
    '- "consultar_deteccoes": ver o historico. Parametros opcionais: "pessoa", '
    '"comodo", "status" ("recognized", "unknown", "person_no_face", "no_face"), '
    '"apenas_negados" (bool), "limite" (int).\n'
    '- "alterar_categoria": mudar a categoria/vinculo de alguem. Parametros: '
    '"pessoa", "categoria".\n'
    '- "liberar_comodo": liberar um comodo para uma pessoa. Parametros: '
    '"pessoa", "comodo".\n'
    '- "nao_entendi": quando o pedido nao encaixa em nenhuma acao. Parametro: '
    '"motivo".\n'
    "Use exatamente os nomes de pessoas, categorias e comodos listados no contexto. "
    "Nunca invente nomes. Na duvida, responda nao_entendi."
)


def enabled() -> bool:
    return OLLAMA_ENABLED


def stats() -> dict[str, Any]:
    return {
        "enabled": OLLAMA_ENABLED,
        "model": OLLAMA_MODEL or "",
        "url": OLLAMA_URL if OLLAMA_ENABLED else "",
        "error": LAST_ERROR,
    }


def _contexto() -> str:
    """Nomes reais do sistema, para o modelo nao inventar entidades."""
    pessoas = [p.name for p in people_store.list_people()]
    comodos = [c.name for c in rooms_store.list_rooms()]
    return (
        f"Pessoas cadastradas: {', '.join(pessoas) or 'nenhuma'}\n"
        f"Categorias existentes: {', '.join(people_store.CATEGORIES)}\n"
        f"Comodos cadastrados: {', '.join(comodos) or 'nenhum'}"
    )


def interpretar(pergunta: str) -> dict[str, Any] | None:
    """Traduz o pedido em intencao estruturada. None se o modelo nao responder."""
    global LAST_ERROR
    if not OLLAMA_ENABLED or not (pergunta or "").strip():
        return None
    try:
        resp = requests.post(
            f"{OLLAMA_URL.rstrip('/')}/api/generate",
            json={
                "model": OLLAMA_MODEL,
                "system": _SYSTEM_PROMPT,
                "prompt": f"{_contexto()}\n\nPedido: {pergunta.strip()}",
                "stream": False,
                "format": "json",
                "options": {"temperature": 0},
            },
            timeout=OLLAMA_TIMEOUT,
        )
        resp.raise_for_status()
        intencao = json.loads(resp.json().get("response", ""))
    except (requests.RequestException, ValueError, TypeError) as exc:
        LAST_ERROR = str(exc)
        print(f"[ASSISTENTE] falha ao interpretar o pedido: {exc}")
        return None

    LAST_ERROR = ""
    return intencao if isinstance(intencao, dict) else None


# ==========================================================================
# Execucao -- codigo comum, sem modelo
# ==========================================================================
def _consultar(params: dict[str, Any]) -> dict[str, Any]:
    """Filtra o historico. Leitura pura, nada muda no sistema."""
    limite = params.get("limite")
    limite = int(limite) if isinstance(limite, (int, float, str)) and str(limite).isdigit() else 20
    itens, _ = detections_store.list_recent(limit=200)

    # Filtros tambem por slug, pelo mesmo motivo dos nomes acentuados.
    pessoa = slugify(str(params.get("pessoa") or ""), "")
    comodo = slugify(str(params.get("comodo") or ""), "")
    status = str(params.get("status") or "").strip().lower()
    apenas_negados = bool(params.get("apenas_negados"))

    filtrados = []
    for item in itens:
        if pessoa and pessoa not in slugify(str(item.get("person", "")), ""):
            continue
        if comodo and comodo not in slugify(str(item.get("room", "")), ""):
            continue
        if status and item.get("status") != status:
            continue
        if apenas_negados and item.get("authorized"):
            continue
        filtrados.append(item)

    return {
        "total": len(filtrados),
        "items": filtrados[:limite],
    }


def _achar_pessoa(nome: str):
    """Casa o nome devolvido pelo modelo com o cadastro.

    Comparacao por slug: o modelo costuma devolver o nome sem acento
    ("Antonio"), e o cadastro tem "Antonio" com acento.
    """
    alvo = slugify(nome, "")
    if not alvo:
        return None
    return next(
        (p for p in people_store.list_people() if slugify(p.name, "") == alvo),
        None,
    )


def _proposta_categoria(params: dict[str, Any]) -> dict[str, Any]:
    nome_pessoa = str(params.get("pessoa") or "").strip()
    categoria = str(params.get("categoria") or "").strip()

    pessoa = _achar_pessoa(nome_pessoa)
    if pessoa is None:
        return {"erro": f"Nao encontrei a pessoa '{nome_pessoa}'"}

    # So o catalogo: uma categoria inventada nao diria nada a quem le a lista.
    oficial = next(
        (c for c in people_store.CATEGORIES if slugify(c, "") == slugify(categoria, "")),
        None,
    )
    if oficial is None:
        return {"erro": f"Nao encontrei a categoria '{categoria}'"}

    return {
        "acao": ACAO_ALTERAR_CATEGORIA,
        "person_id": pessoa.id,
        "category": oficial,
        "resumo": f"Mudar {pessoa.name} de '{pessoa.category}' para '{oficial}'",
    }


def _proposta_comodo(params: dict[str, Any]) -> dict[str, Any]:
    nome_pessoa = str(params.get("pessoa") or "").strip()
    nome_comodo = str(params.get("comodo") or "").strip()

    pessoa = _achar_pessoa(nome_pessoa)
    comodo = rooms_store.find_by_name(nome_comodo)
    if pessoa is None:
        return {"erro": f"Nao encontrei a pessoa '{nome_pessoa}'"}
    if comodo is None:
        return {"erro": f"Nao encontrei o comodo '{nome_comodo}'"}
    if comodo.id in pessoa.rooms:
        return {"erro": f"{comodo.name} ja esta liberado para {pessoa.name}"}

    return {
        "acao": ACAO_LIBERAR_COMODO,
        "person_id": pessoa.id,
        "room_id": comodo.id,
        "resumo": f"Liberar '{comodo.name}' para {pessoa.name}",
    }


def responder(pergunta: str) -> dict[str, Any]:
    """Interpreta e ja executa o que for leitura; escrita vira proposta."""
    intencao = interpretar(pergunta)
    if intencao is None:
        return {
            "ok": False,
            "answer": (
                "Assistente indisponivel: configure OLLAMA_MODEL no .env e "
                "confirme que o Ollama esta rodando."
                if not OLLAMA_ENABLED
                else "Nao consegui falar com o modelo local."
            ),
        }

    acao = str(intencao.get("acao") or ACAO_NAO_ENTENDI)

    if acao == ACAO_CONSULTAR:
        resultado = _consultar(intencao)
        return {
            "ok": True,
            "action": acao,
            "answer": f"{resultado['total']} deteccao(oes) encontrada(s).",
            "detections": resultado["items"],
        }

    if acao in (ACAO_ALTERAR_CATEGORIA, ACAO_LIBERAR_COMODO):
        proposta = (
            _proposta_categoria(intencao)
            if acao == ACAO_ALTERAR_CATEGORIA
            else _proposta_comodo(intencao)
        )
        if "erro" in proposta:
            return {"ok": False, "action": acao, "answer": proposta["erro"]}
        return {
            "ok": True,
            "action": acao,
            # Nada muda ate o administrador confirmar na tela.
            "requires_confirmation": True,
            "answer": proposta["resumo"],
            "proposal": proposta,
        }

    return {
        "ok": False,
        "action": ACAO_NAO_ENTENDI,
        "answer": str(intencao.get("motivo") or "Nao entendi o pedido."),
    }


def aplicar(proposta: dict[str, Any]) -> dict[str, Any]:
    """Executa uma proposta ja confirmada pelo administrador."""
    acao = str(proposta.get("acao") or "")

    if acao == ACAO_ALTERAR_CATEGORIA:
        pessoa = people_store.update_person(
            str(proposta.get("person_id")), category=str(proposta.get("category"))
        )
        if pessoa is None:
            return {"ok": False, "answer": "Pessoa nao encontrada"}
        return {"ok": True, "answer": f"{pessoa.name} atualizado", "person": pessoa.to_dict()}

    if acao == ACAO_LIBERAR_COMODO:
        pessoa = people_store.get_person(str(proposta.get("person_id")))
        room_id = str(proposta.get("room_id"))
        if pessoa is None or rooms_store.get_room(room_id) is None:
            return {"ok": False, "answer": "Pessoa ou comodo nao encontrado"}
        atualizada = people_store.update_person(
            pessoa.id, rooms=sorted(set(pessoa.rooms) | {room_id})
        )
        return {
            "ok": True,
            "answer": f"{atualizada.name} atualizado",
            "person": atualizada.to_dict(),
        }

    return {"ok": False, "answer": "Proposta desconhecida"}
