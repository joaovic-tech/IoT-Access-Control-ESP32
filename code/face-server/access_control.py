"""Decisao de acesso por comodo. Deterministica, local e auditavel.

Substitui o `access_rules.py`, que derivava a regra de uma observacao em texto
livre com um modelo de linguagem. A medicao registrada em tasks.md mostrou o
problema desse desenho: para a observacao "Quarto do Antonio" o modelo chegou a
responder `acesso_total: true`, liberando a pessoa na casa inteira. Num controle
de acesso fisico a resposta precisa ser sempre a mesma para a mesma entrada --
e isso e uma tabela, nao uma frase.

A regra completa, em ordem:

    1. pessoa bloqueada (`authorized` = false)  -> NEGADO
    2. comodo nao informado                     -> SEM OPINIAO (None)
    3. comodo marcado no cadastro da pessoa     -> PERMITIDO
    4. qualquer outro caso                      -> NEGADO (fecha por padrao)

O passo 4 e deliberado: um comodo que ninguem marcou, ou um cadastro sem
comodo nenhum, nao libera nada.
"""
from __future__ import annotations

import people_store
import rooms_store


def evaluate(
    person: people_store.Person | None,
    room: rooms_store.Room | None,
) -> tuple[bool | None, str]:
    """Decide se a pessoa pode estar no comodo.

    Retorna `(permitido, motivo)`. `permitido` e None quando nao ha comodo para
    avaliar -- nesse caso quem decide e so a chave mestra `authorized`, como o
    sistema fazia antes de existirem comodos.
    """
    if person is None:
        return None, ""

    # Chave mestra: cadastro bloqueado nao entra em lugar nenhum, sem consultar
    # a lista de comodos.
    if not person.authorized:
        return False, "cadastro sem autorizacao de acesso"

    if room is None:
        return None, ""

    if room.id in person.rooms:
        return True, f"{room.name} liberado para {person.name}"

    return False, f"{room.name} nao liberado no cadastro de {person.name}"


def room_names(person: people_store.Person | None) -> list[str]:
    """Nomes dos comodos liberados, na ordem do cadastro de comodos."""
    if person is None or not person.rooms:
        return []
    return [r.name for r in rooms_store.list_rooms() if r.id in person.rooms]


def describe(person: people_store.Person | None) -> str:
    """Frase curta do acesso da pessoa, para a lista no app.

    Template, nao modelo de linguagem: instantaneo, offline e sempre igual
    (principio de arquitetura registrado em tasks.md).
    """
    if person is None:
        return ""
    if not person.authorized:
        return "Sem autorizacao de acesso"

    nomes = room_names(person)
    if not nomes:
        return f"{person.category}: nenhum comodo liberado"
    return f"{person.category}: {', '.join(nomes)}"
