from __future__ import annotations

import argparse
import json

import requests


def _post_json(url: str, payload: dict, headers: dict[str, str]) -> dict:
    response = requests.post(
        url,
        json=payload,
        headers=headers,
        timeout=60,
    )
    response.raise_for_status()
    return response.json()


def sign_in_anonymously(api_key: str) -> str:
    url = f"https://identitytoolkit.googleapis.com/v1/accounts:signUp?key={api_key}"
    response = _post_json(
        url,
        {"returnSecureToken": True},
        headers={},
    )
    return response["idToken"]


def invoke_callable(function_url: str, id_token: str, data: dict) -> dict:
    return _post_json(
        function_url,
        {"data": data},
        headers={"Authorization": f"Bearer {id_token}"},
    )


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke test the deployed Firebase callable agent proxy.")
    parser.add_argument("--project-id", required=True)
    parser.add_argument("--api-key", required=True)
    parser.add_argument("--function-url", required=True)
    parser.add_argument("--conversation-id", default="smoke-conversation-1")
    parser.add_argument("--message", default="Please diagnose my tomato crop. The leaves have yellow spots.")
    parser.add_argument("--locale", default="en")
    parser.add_argument("--image-url", default=None)
    args = parser.parse_args()

    id_token = sign_in_anonymously(args.api_key)
    result = invoke_callable(
        args.function_url,
        id_token,
        {
            "conversationId": args.conversation_id,
            "message": args.message,
            "locale": args.locale,
            "imageUrl": args.image_url,
            "recentMessages": [],
        },
    )
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
