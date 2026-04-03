from pathlib import Path

from agent_service.image_utils import build_inline_image_part, infer_mime_type


def test_infer_mime_type_prefers_supported_header() -> None:
    assert infer_mime_type("https://example.com/file", "image/png") == "image/png"


def test_build_image_part_from_local_file_bytes() -> None:
    image_path = Path("/Users/m1/AndroidStudioProjects/androidifyxy/website/public/screenshots/crop-scan.png")
    image_bytes = image_path.read_bytes()
    part = build_inline_image_part(data=image_bytes, mime_type="image/png")
    assert part.inline_data is not None
    assert part.inline_data.mime_type == "image/png"
