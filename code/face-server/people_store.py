"""Armazenamento simples (um JSON por pessoa) dos metadados de pessoas.

Estrutura em disco:
    people/<id>.json                -> metadados
    known_faces/<id>/<n>.jpg        -> amostras faciais

Campos do cadastro (tasks.md, "Mudancas de campos"): amostras, nome,
categoria/vinculo e os comodos que a pessoa pode acessar. Os comodos ficam na
PROPRIA pessoa -- o nivel intermediario que existia antes foi removido, porque
a tela de cadastro marca comodo por comodo para cada pessoa.
"""
from __future__ import annotations

import json
import re
import shutil
import time
import uuid
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any

from config import KNOWN_FACES_DIR, LEVELS_DIR, MIN_SAMPLES_ACTIVE, PEOPLE_DIR

_SAMPLE_RE = re.compile(r"^(\d+)\.jpg$", re.IGNORECASE)

# Categorias/vinculos oferecidos na tela de cadastro
# (design-app-mobile/cadastrar-nova-pessoa.png). E um catalogo, nao uma regra:
# quem decide o acesso e a lista de comodos da pessoa.
CATEGORIES: tuple[str, ...] = ("Morador", "Visitante", "Prestador")

# Usada quando o cadastro nao informa categoria. A mais restritiva por
# convencao -- nunca a que tem mais acesso.
FALLBACK_CATEGORY = "Visitante"


@dataclass
class Person:
    id: str
    name: str
    # Vinculo com a casa ("Morador", "Visitante", "Prestador"). Texto livre:
    # o app oferece CATEGORIES, mas um cadastro antigo pode trazer outro nome.
    category: str = FALLBACK_CATEGORY
    # Ids dos comodos que esta pessoa pode acessar. Lista vazia = nenhum.
    rooms: list[str] = field(default_factory=list)
    # Chave mestra do cadastro: False bloqueia a pessoa em qualquer comodo,
    # independentemente dos comodos marcados.
    authorized: bool = True
    created_at: float = field(default_factory=time.time)

    def sample_dir(self) -> Path:
        return KNOWN_FACES_DIR / self.id

    def sample_files(self) -> list[Path]:
        d = self.sample_dir()
        if not d.is_dir():
            return []
        files = [p for p in d.iterdir() if _SAMPLE_RE.match(p.name)]
        return sorted(files, key=lambda p: int(p.stem))

    @property
    def samples(self) -> int:
        return len(self.sample_files())

    @property
    def status(self) -> str:
        """Prontidao do cadastro facial: "ativo" ou "pendente".

        Fica "pendente" ate a pessoa ter MIN_SAMPLES_ACTIVE amostras. Ela ja e
        comparada nesse estado -- so que com poucas amostras o reconhecimento
        erra mais, entao a etiqueta pede mais fotos do rosto. Nao tem relacao
        com `authorized`, que e quem decide o acesso.
        """
        return "ativo" if self.samples >= MIN_SAMPLES_ACTIVE else "pendente"

    def to_dict(self) -> dict[str, Any]:
        data = asdict(self)
        data["samples"] = self.samples
        data["status"] = self.status
        # Vai junto de cada pessoa para o app poder dizer QUANTAS amostras ainda
        # faltam. Sem isso a etiqueta "Pendente" nao explicava o que fazer.
        data["min_samples_active"] = MIN_SAMPLES_ACTIVE
        # Import tardio: access_control importa este modulo.
        import access_control

        data["room_names"] = access_control.room_names(self)
        data["access_label"] = access_control.describe(self)
        return data


def _meta_path(person_id: str) -> Path:
    return PEOPLE_DIR / f"{person_id}.json"


def _comodos_do_nivel(level_id: str) -> tuple[str, list[str]] | None:
    """Le um `levels/<id>.json` de instalacoes antigas.

    O modulo `levels_store` nao existe mais; a leitura e feita aqui, direto no
    arquivo, so para converter os cadastros que ainda apontam para um nivel.
    """
    caminho = LEVELS_DIR / f"{level_id}.json"
    if not caminho.is_file():
        return None
    try:
        raw = json.loads(caminho.read_text(encoding="utf-8"))
    except Exception:
        return None

    nome = str(raw.get("name") or "").strip()
    if raw.get("all_rooms"):
        # "Acessa tudo" vira a lista dos comodos existentes hoje: a pessoa
        # mantem o acesso que tinha, e comodos criados depois entram pela tela.
        import rooms_store

        return nome, [c.id for c in rooms_store.list_rooms()]

    comodos = raw.get("rooms")
    return nome, [str(r) for r in comodos] if isinstance(comodos, list) else []


def _migrar(raw: dict[str, Any]) -> tuple[str, list[str]]:
    """Converte um cadastro antigo nos campos atuais (categoria + comodos).

    Duas geracoes passam por aqui:
      * `level_id`: o nome do nivel vira a categoria e os comodos do nivel
        passam a ser da pessoa;
      * `category` em texto livre, sem comodo nenhum: mantem a categoria e
        fecha o acesso -- nunca migrar para mais permissivo do que estava.
    """
    level_id = str(raw.get("level_id") or "").strip()
    if level_id:
        convertido = _comodos_do_nivel(level_id)
        if convertido:
            nome, comodos = convertido
            return (nome or FALLBACK_CATEGORY), comodos

    categoria = str(raw.get("category") or "").strip()
    return (categoria or FALLBACK_CATEGORY), []


def _load(person_id: str) -> Person | None:
    path = _meta_path(person_id)
    if not path.is_file():
        return None
    raw = json.loads(path.read_text(encoding="utf-8"))

    comodos = raw.get("rooms")
    precisa_migrar = not isinstance(comodos, list) or bool(raw.get("level_id"))
    if precisa_migrar:
        categoria, comodos = _migrar(raw)
    else:
        categoria = str(raw.get("category") or "").strip() or FALLBACK_CATEGORY
        comodos = [str(r) for r in comodos]

    person = Person(
        id=raw["id"],
        name=raw.get("name", ""),
        category=categoria,
        rooms=comodos,
        authorized=bool(raw.get("authorized", False)),
        created_at=float(raw.get("created_at", time.time())),
    )
    # Grava a migracao (e descarta `level_id`, `note` e `access_rule` legados).
    if precisa_migrar or {"level_id", "note", "access_rule"} & set(raw):
        _save(person)
    return person


def _save(person: Person) -> None:
    PEOPLE_DIR.mkdir(parents=True, exist_ok=True)
    payload = {
        "id": person.id,
        "name": person.name,
        "category": person.category,
        "rooms": person.rooms,
        "authorized": person.authorized,
        "created_at": person.created_at,
    }
    _meta_path(person.id).write_text(
        json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8"
    )


def list_people() -> list[Person]:
    if not PEOPLE_DIR.is_dir():
        return []
    people = []
    for path in PEOPLE_DIR.glob("*.json"):
        p = _load(path.stem)
        if p:
            people.append(p)
    people.sort(key=lambda p: p.created_at, reverse=True)
    return people


def get_person(person_id: str) -> Person | None:
    return _load(person_id)


def create_person(
    name: str,
    category: str | None = None,
    rooms: list[str] | None = None,
    authorized: bool = True,
) -> Person:
    person = Person(
        id=uuid.uuid4().hex[:12],
        name=name.strip(),
        category=(category or "").strip() or FALLBACK_CATEGORY,
        rooms=list(rooms or []),
        authorized=authorized,
    )
    person.sample_dir().mkdir(parents=True, exist_ok=True)
    _save(person)
    return person


def update_person(person_id: str, **patch: Any) -> Person | None:
    person = _load(person_id)
    if not person:
        return None
    if patch.get("name") is not None:
        person.name = str(patch["name"]).strip()
    if patch.get("category") is not None:
        person.category = str(patch["category"]).strip() or FALLBACK_CATEGORY
    if patch.get("rooms") is not None:
        person.rooms = [str(r) for r in patch["rooms"]]
    if patch.get("authorized") is not None:
        person.authorized = bool(patch["authorized"])
    _save(person)
    return person


def delete_person(person_id: str) -> bool:
    person = _load(person_id)
    if not person:
        return False
    shutil.rmtree(person.sample_dir(), ignore_errors=True)
    _meta_path(person_id).unlink(missing_ok=True)
    return True


def remove_room_everywhere(room_id: str) -> int:
    """Tira um comodo apagado de todas as pessoas. Retorna quantas mudaram."""
    alteradas = 0
    for person in list_people():
        if room_id in person.rooms:
            person.rooms = [r for r in person.rooms if r != room_id]
            _save(person)
            alteradas += 1
    return alteradas


def count_by_room(room_id: str) -> int:
    """Quantas pessoas tem este comodo liberado."""
    return sum(1 for p in list_people() if room_id in p.rooms)


def next_sample_index(person: Person) -> int:
    existing = [int(p.stem) for p in person.sample_files()]
    return (max(existing) + 1) if existing else 1


def add_sample_bytes(person: Person, data: bytes) -> Path:
    person.sample_dir().mkdir(parents=True, exist_ok=True)
    idx = next_sample_index(person)
    path = person.sample_dir() / f"{idx}.jpg"
    path.write_bytes(data)
    return path
