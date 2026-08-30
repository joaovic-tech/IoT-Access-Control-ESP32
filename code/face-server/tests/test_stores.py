"""Testes do armazenamento de pessoas e do historico de deteccoes."""
from __future__ import annotations

import detections_store
import people_store


def test_create_person_starts_pending_and_becomes_active():
    person = people_store.create_person("Joao Victor", "Morador", authorized=True)
    assert person.samples == 0
    assert person.status == "pendente"

    for _ in range(3):
        people_store.add_sample_bytes(person, b"fake-jpeg-bytes")

    reloaded = people_store.get_person(person.id)
    assert reloaded is not None
    assert reloaded.samples == 3
    assert reloaded.status == "ativo"
    assert reloaded.authorized is True


def test_sample_files_are_numbered_sequentially():
    person = people_store.create_person("Maria")
    paths = [people_store.add_sample_bytes(person, b"x") for _ in range(3)]
    assert [p.name for p in paths] == ["1.jpg", "2.jpg", "3.jpg"]
    assert [p.name for p in person.sample_files()] == ["1.jpg", "2.jpg", "3.jpg"]


def test_update_and_delete_person():
    person = people_store.create_person("Carlos", "Visitante")
    updated = people_store.update_person(person.id, authorized=True, category="Prestador")
    assert updated is not None
    assert updated.authorized is True
    assert updated.category == "Prestador"

    people_store.add_sample_bytes(person, b"x")
    assert people_store.delete_person(person.id) is True
    assert people_store.get_person(person.id) is None
    assert person.sample_dir().exists() is False
    assert people_store.delete_person(person.id) is False


def test_list_people_is_newest_first():
    first = people_store.create_person("Primeiro")
    second = people_store.create_person("Segundo")
    second.created_at = first.created_at + 10
    people_store._save(second)

    names = [p.name for p in people_store.list_people()]
    assert names[0] == "Segundo"


def test_slugify_removes_accents_and_symbols():
    assert detections_store.slugify("Joao Victor C. Alves") == "joao-victor-c-alves"
    assert detections_store.slugify("Antonio Silva") == "antonio-silva"
    assert detections_store.slugify("") == "sem-nome"


def test_save_detection_writes_image_and_metadata():
    record = detections_store.save(
        image=b"jpeg-bytes",
        status="recognized",
        person="Joao Victor",
        person_id="abc123",
        category="Morador",
        authorized=True,
        distance=0.42,
        source="pir",
    )
    image_path = detections_store.DETECTIONS_DIR / record["file"]
    assert image_path.read_bytes() == b"jpeg-bytes"
    assert record["file"].endswith("_recognized_joao-victor.jpg")
    assert record["url"] == f"/media/detections/{record['file']}"

    items, total = detections_store.list_recent()
    assert total == 1
    assert items[0]["person"] == "Joao Victor"
    assert items[0]["authorized"] is True


def test_save_detection_avoids_filename_collision():
    kwargs = dict(image=b"a", status="unknown", person="")
    first = detections_store.save(**kwargs)
    second = detections_store.save(**kwargs)
    assert first["file"] != second["file"]

    _, total = detections_store.list_recent()
    assert total == 2


def test_list_recent_paginates():
    for i in range(5):
        detections_store.save(image=b"a", status="unknown", person=f"P{i}")
    page, total = detections_store.list_recent(limit=2, offset=2)
    assert total == 5
    assert len(page) == 2
