"""formSchema → OpenAI Function Calling Tool 변환"""
import json
import logging
from datetime import date

logger = logging.getLogger(__name__)


# 필드 타입별 JSON Schema 매핑
TYPE_TO_JSON_SCHEMA = {
    "text": {"type": "string"},
    "textarea": {"type": "string"},
    "number": {"type": "number"},
    "date": {"type": "string", "format": "date", "description_suffix": "(YYYY-MM-DD 형식)"},
    "time": {"type": "string", "format": "time", "description_suffix": "(HH:mm 형식)"},
    "select": {"type": "string"},
    "file": {"type": "string", "description_suffix": "(파일 URL)"},
}


def parse_form_schema(form_schema_str: str) -> list[dict]:
    """formSchema JSON에서 fields 배열 추출"""
    if not form_schema_str:
        return []

    try:
        schema = json.loads(form_schema_str)
        return schema.get("fields", [])
    except json.JSONDecodeError as e:
        logger.error(f"[form_builder] formSchema 파싱 실패: {e}")
        return []


def get_visible_fields(fields: list[dict]) -> list[dict]:
    """hidden 필드 제외한 사용자 입력 가능 필드"""
    return [f for f in fields if f.get("type") != "hidden"]


def field_to_json_schema(field: dict) -> dict:
    """필드 1개 → OpenAI Function Calling용 JSON Schema 변환"""
    field_type = field.get("type", "text")
    label = field.get("label", "")

    base = TYPE_TO_JSON_SCHEMA.get(field_type, {"type": "string"}).copy()
    suffix = base.pop("description_suffix", "")

    description = label
    if suffix:
        description = f"{label} {suffix}"

    schema = {
        "type": base["type"],
        "description": description
    }

    # format 추가 (date, time)
    if "format" in base:
        schema["format"] = base["format"]

    # select → enum
    if field_type == "select":
        options = field.get("options", [])
        if options:
            schema["enum"] = options

    # 조건부 필드 안내
    if "visibleWhen" in field:
        cond = field["visibleWhen"]
        schema["description"] += f" ({cond.get('field')}가 '{cond.get('value')}'일 때만 입력)"

    return schema


def build_tool_from_form(document: dict) -> dict | None:
    """
    ApprovalDocument → OpenAI Function Calling Tool 변환

    Args:
        document: approval-service에서 받은 양식 정보 (formSchema 포함)

    Returns:
        OpenAI Function Calling tool 형식
    """
    document_name = document.get("documentName", "결재")
    form_schema_str = document.get("formSchema", "")

    fields = parse_form_schema(form_schema_str)
    visible_fields = get_visible_fields(fields)

    if not visible_fields:
        logger.warning(f"[form_builder] 입력 필드 없음: {document_name}")
        return None

    properties = {}
    required = []

    for field in visible_fields:
        name = field.get("name")
        if not name:
            continue

        properties[name] = field_to_json_schema(field)

        if field.get("required"):
            required.append(name)

    tool = {
        "type": "function",
        "function": {
            "name": "fill_approval_form",
            "description": f"{document_name} 작성에 필요한 정보를 추출합니다.",
            "parameters": {
                "type": "object",
                "properties": properties,
                "required": required
            }
        }
    }

    logger.info(
        f"[form_builder] Tool 생성: {document_name}, "
        f"필드 {len(properties)}개 (필수 {len(required)}개)"
    )

    return tool


def is_field_visible(field: dict, slots: dict) -> bool:
    """
    조건부 필드(visibleWhen)가 현재 슬롯 상태에서 보여야 하는지 판단

    예: condolenceType은 vacationType="경조사"일 때만 보임
    """
    visible_when = field.get("visibleWhen")
    if not visible_when:
        return True  # 조건 없으면 항상 보임

    target_field = visible_when.get("field")
    target_value = visible_when.get("value")

    if not target_field:
        return True

    actual_value = slots.get(target_field)
    return actual_value == target_value


def find_missing_required_fields(slots: dict, form_schema_str: str) -> list[dict]:
    """
    필수 필드 중 슬롯에 없는 것 찾기 (조건부 필드 고려)

    Returns:
        부족한 필드 목록 [{"name": ..., "label": ..., "type": ...}, ...]
    """
    fields = parse_form_schema(form_schema_str)
    visible_fields = get_visible_fields(fields)

    missing = []
    for field in visible_fields:
        # 조건부 필드는 조건 만족 시에만 검사
        if not is_field_visible(field, slots):
            continue

        # 필수 필드인지
        if not field.get("required"):
            continue

        # 슬롯에 값이 있는지
        name = field.get("name")
        value = slots.get(name)

        if value is None or value == "":
            missing.append({
                "name": name,
                "label": field.get("label", name),
                "type": field.get("type", "text"),
                "options": field.get("options", [])
            })

    return missing


def render_preview(slots: dict, form_schema_str: str) -> dict:
    """
    슬롯 → 사용자 미리보기용 dict 변환 (label 사용)
    """
    fields = parse_form_schema(form_schema_str)
    visible_fields = get_visible_fields(fields)

    preview_fields = {}
    for field in visible_fields:
        # 조건부 필드는 조건 만족 시에만 표시
        if not is_field_visible(field, slots):
            continue

        name = field.get("name")
        label = field.get("label", name)
        value = slots.get(name)

        if value is None or value == "":
            continue

        preview_fields[label] = value

    return preview_fields