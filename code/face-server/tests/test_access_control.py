"""Testes da decisao de acesso deterministica (comodos por pessoa).

Substituem os testes do antigo access_rules: a decisao agora sai de uma tabela,
nao da interpretacao de uma frase, entao da para afirmar o resultado exato.
"""
from __future__ import annotations

import json

import access_control
import people_store
import rooms_store


# ==========================================================================
# Comodos
# ==========================================================================
def test_comodo_casa_por_nome_ignorando_acento_e_caixa():
    quarto = rooms_store.create_room("Quarto do João")

    assert rooms_store.find_by_name("quarto do joao").id == quarto.id
    assert rooms_store.find_by_name("QUARTO DO JOÃO").id == quarto.id
    assert rooms_store.find_by_name("Cozinha") is None


def test_resolve_aceita_id_ou_nome():
    cozinha = rooms_store.create_room("Cozinha")

    assert rooms_store.resolve(room_id=cozinha.id).id == cozinha.id
    assert rooms_store.resolve(name="cozinha").id == cozinha.id
    # Id invalido cai para o nome, que e o caminho do ROOM do .env.
    assert rooms_store.resolve(room_id="inexistente", name="Cozinha").id == cozinha.id
    assert rooms_store.resolve(room_id="inexistente", name="Fantasma") is None


def test_apagar_comodo_o_remove_das_pessoas():
    """Comodo apagado nao pode continuar liberado no cadastro de ninguem."""
    cozinha = rooms_store.create_room("Cozinha")
    sala = rooms_store.create_room("Sala")
    pessoa = people_store.create_person("Ana", "Prestador", [cozinha.id, sala.id])

    rooms_store.delete_room(cozinha.id)
    assert people_store.remove_room_everywhere(cozinha.id) == 1
    assert people_store.get_person(pessoa.id).rooms == [sala.id]


# ==========================================================================
# Categoria / vinculo
# ==========================================================================
def test_categorias_do_catalogo_batem_com_o_design():
    """Os chips da tela de cadastro saem daqui."""
    assert people_store.CATEGORIES == ("Morador", "Visitante", "Prestador")


def test_cadastro_sem_categoria_cai_na_mais_restritiva():
    pessoa = people_store.create_person("Sem vinculo")
    assert pessoa.category == "Visitante"
    assert pessoa.rooms == []


# ==========================================================================
# A decisao
# ==========================================================================
def test_comodo_marcado_no_cadastro_libera():
    quarto = rooms_store.create_room("Quarto do Joao")
    joao = people_store.create_person("Joao", "Morador", [quarto.id])

    permitido, motivo = access_control.evaluate(joao, quarto)
    assert permitido is True
    assert "liberado" in motivo


def test_comodo_fora_do_cadastro_e_negado():
    """Cenario do TCC: Antonio entrando no quarto do Joao."""
    cozinha = rooms_store.create_room("Cozinha")
    quarto = rooms_store.create_room("Quarto do Joao")
    antonio = people_store.create_person("Antonio", "Prestador", [cozinha.id])

    assert access_control.evaluate(antonio, cozinha)[0] is True
    permitido, motivo = access_control.evaluate(antonio, quarto)
    assert permitido is False
    assert "nao liberado" in motivo


def test_chave_mestra_vence_os_comodos():
    """`authorized` = false bloqueia antes de olhar a lista de comodos."""
    quarto = rooms_store.create_room("Quarto do Joao")
    bloqueado = people_store.create_person(
        "Bloqueado", "Morador", [quarto.id], authorized=False
    )

    permitido, motivo = access_control.evaluate(bloqueado, quarto)
    assert permitido is False
    assert "sem autorizacao" in motivo


def test_cadastro_sem_comodo_nao_entra_em_lugar_nenhum():
    cozinha = rooms_store.create_room("Cozinha")
    visitante = people_store.create_person("Visita", "Visitante")
    assert access_control.evaluate(visitante, cozinha)[0] is False


def test_sem_comodo_nao_opina():
    """None = "nao sei": decide so a chave mestra, como antes dos comodos."""
    pessoa = people_store.create_person("Alguem", "Morador")
    assert access_control.evaluate(pessoa, None) == (None, "")
    assert access_control.evaluate(None, None) == (None, "")


def test_comodo_inexistente_no_cadastro_nao_vira_permissao():
    """Fecha por padrao: configuracao orfa nunca abre porta."""
    cozinha = rooms_store.create_room("Cozinha")
    orfa = people_store.create_person("Orfa", "Morador", ["comodo-que-nao-existe"])

    permitido, _ = access_control.evaluate(orfa, cozinha)
    assert permitido is False


def test_decisao_e_sempre_a_mesma_para_a_mesma_entrada():
    """O ponto do desenho: determinismo, ao contrario do modelo de linguagem."""
    quarto = rooms_store.create_room("Quarto do Joao")
    cozinha = rooms_store.create_room("Cozinha")
    pessoa = people_store.create_person("Antonio", "Prestador", [cozinha.id])

    resultados = {access_control.evaluate(pessoa, quarto) for _ in range(20)}
    assert len(resultados) == 1


# ==========================================================================
# Descricao para o app (template, nao modelo de linguagem)
# ==========================================================================
def test_descricao_do_acesso():
    cozinha = rooms_store.create_room("Cozinha")
    sala = rooms_store.create_room("Sala")

    ana = people_store.create_person("Ana", "Prestador", [cozinha.id, sala.id])
    descricao = access_control.describe(ana)
    assert descricao.startswith("Prestador:")
    assert "Cozinha" in descricao and "Sala" in descricao

    visita = people_store.create_person("Visita", "Visitante")
    assert "nenhum comodo" in access_control.describe(visita)

    bloqueado = people_store.create_person(
        "X", "Morador", [cozinha.id], authorized=False
    )
    assert access_control.describe(bloqueado) == "Sem autorizacao de acesso"


# ==========================================================================
# Migracao dos cadastros antigos
# ==========================================================================
def _gravar_nivel(tmp_path, level_id: str, payload: dict) -> None:
    (tmp_path / "levels").mkdir(parents=True, exist_ok=True)
    (tmp_path / "levels" / f"{level_id}.json").write_text(
        json.dumps(payload), encoding="utf-8"
    )


def _gravar_pessoa(person_id: str, payload: dict) -> None:
    people_store.PEOPLE_DIR.mkdir(parents=True, exist_ok=True)
    (people_store.PEOPLE_DIR / f"{person_id}.json").write_text(
        json.dumps(payload), encoding="utf-8"
    )


def test_cadastro_com_nivel_vira_categoria_e_comodos(isolated_storage):
    """O nome do nivel vira a categoria; os comodos do nivel vao para a pessoa."""
    cozinha = rooms_store.create_room("Cozinha")
    _gravar_nivel(
        isolated_storage,
        "nivel01",
        {"id": "nivel01", "name": "Prestador", "all_rooms": False, "rooms": [cozinha.id]},
    )
    _gravar_pessoa(
        "antigo01",
        {
            "id": "antigo01",
            "name": "Joao Victor",
            "level_id": "nivel01",
            "authorized": True,
            "created_at": 1756000000.0,
        },
    )

    pessoa = people_store.get_person("antigo01")
    assert pessoa.category == "Prestador"
    assert pessoa.rooms == [cozinha.id]
    # A migracao NAO pode mexer na chave mestra, no nome nem na data.
    assert pessoa.authorized is True
    assert pessoa.name == "Joao Victor"
    assert pessoa.created_at == 1756000000.0

    # O campo legado some do disco depois da migracao.
    gravado = json.loads(
        (people_store.PEOPLE_DIR / "antigo01.json").read_text(encoding="utf-8")
    )
    assert "level_id" not in gravado


def test_nivel_com_acesso_total_vira_a_lista_de_comodos(isolated_storage):
    cozinha = rooms_store.create_room("Cozinha")
    quarto = rooms_store.create_room("Quarto")
    _gravar_nivel(
        isolated_storage,
        "nivel02",
        {"id": "nivel02", "name": "Morador", "all_rooms": True, "rooms": []},
    )
    _gravar_pessoa("antigo02", {"id": "antigo02", "name": "Ana", "level_id": "nivel02"})

    pessoa = people_store.get_person("antigo02")
    assert pessoa.category == "Morador"
    assert sorted(pessoa.rooms) == sorted([cozinha.id, quarto.id])


def test_cadastro_so_com_categoria_nao_ganha_comodo():
    """Nunca migrar para mais permissivo do que estava."""
    _gravar_pessoa(
        "antigo03",
        {"id": "antigo03", "name": "X", "category": "Entregador", "authorized": True},
    )

    pessoa = people_store.get_person("antigo03")
    assert pessoa.category == "Entregador"
    assert pessoa.rooms == []


def test_conta_pessoas_por_comodo():
    cozinha = rooms_store.create_room("Cozinha")
    people_store.create_person("Um", "Morador", [cozinha.id])
    people_store.create_person("Dois", "Visitante")
    assert people_store.count_by_room(cozinha.id) == 1
