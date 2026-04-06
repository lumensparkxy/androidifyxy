from __future__ import annotations

import mimetypes
from dataclasses import dataclass
from urllib.error import HTTPError, URLError
from urllib.parse import urlparse
from urllib.request import Request, urlopen

from google.genai import types

from .app_config import config

SUPPORTED_IMAGE_MIME_TYPES = {
    "image/jpeg",
    "image/jpg",
    "image/png",
    "image/webp",
}


class ImageDownloadError(RuntimeError):
    pass


@dataclass(frozen=True)
class DownloadedImage:
    data: bytes
    mime_type: str


def _normalize_mime_type(value: str | None) -> str | None:
    if not value:
        return None
    normalized = value.split(";", 1)[0].strip().lower()
    if normalized == "image/jpg":
        return "image/jpeg"
    return normalized


def infer_mime_type(image_url: str, content_type: str | None = None) -> str:
    normalized = _normalize_mime_type(content_type)
    if normalized in SUPPORTED_IMAGE_MIME_TYPES:
        return normalized

    guessed, _ = mimetypes.guess_type(urlparse(image_url).path)
    guessed = _normalize_mime_type(guessed)
    if guessed in SUPPORTED_IMAGE_MIME_TYPES:
        return guessed

    return "image/jpeg"


def download_image(image_url: str) -> DownloadedImage:
    request = Request(
        image_url,
        headers={
            "User-Agent": "KrishiAgentService/0.1",
            "Accept": ", ".join(sorted(SUPPORTED_IMAGE_MIME_TYPES)),
        },
    )

    try:
        with urlopen(request, timeout=config.image_download_timeout_seconds) as response:
            mime_type = infer_mime_type(image_url, response.headers.get("Content-Type"))
            if mime_type not in SUPPORTED_IMAGE_MIME_TYPES:
                raise ImageDownloadError(f"Unsupported image MIME type: {mime_type}")

            content_length = response.headers.get("Content-Length")
            if content_length and int(content_length) > config.max_image_bytes:
                raise ImageDownloadError(
                    f"Image is too large. Maximum supported size is {config.max_image_bytes} bytes."
                )

            data = response.read(config.max_image_bytes + 1)
            if len(data) > config.max_image_bytes:
                raise ImageDownloadError(
                    f"Image is too large. Maximum supported size is {config.max_image_bytes} bytes."
                )

            return DownloadedImage(data=data, mime_type=mime_type)
    except HTTPError as error:
        raise ImageDownloadError(f"Image download failed with HTTP {error.code}") from error
    except URLError as error:
        raise ImageDownloadError(f"Image download failed: {error.reason}") from error


def build_inline_image_part(data: bytes, mime_type: str) -> types.Part:
    return types.Part.from_bytes(data=data, mime_type=_normalize_mime_type(mime_type) or "image/jpeg")


def build_image_part(image_url: str) -> types.Part:
    downloaded = download_image(image_url)
    return build_inline_image_part(data=downloaded.data, mime_type=downloaded.mime_type)
