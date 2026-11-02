#!/usr/bin/env python3
"""Минимальный CLI-клиент DeepSeek API без сторонних зависимостей."""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

API_URL = "https://api.deepseek.com/chat/completions"
MODEL = "deepseek-flash"
TIMEOUT_SECONDS = 60


class ApiError(Exception):
    """Ошибка обращения к API или разбора его ответа."""


def request_answer(prompt: str, api_key: str) -> str:
    payload = {
        "model": MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "max_tokens": 1024,
        "stream": False,
    }
    request = urllib.request.Request(
        API_URL,
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        method="POST",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "User-Agent": "day01-llm-api/1.0",
        },
    )

    try:
        with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
            body = response.read().decode("utf-8")
    except urllib.error.HTTPError as error:
        raw_body = error.read().decode("utf-8", errors="replace")
        try:
            error_data = json.loads(raw_body)
            detail = error_data.get("error", {}).get("message", "нет описания")
        except (json.JSONDecodeError, AttributeError):
            detail = "нет описания"
        raise ApiError(f"API вернул HTTP {error.code}: {detail}") from None
    except (urllib.error.URLError, TimeoutError) as error:
        raise ApiError(f"Не удалось обратиться к API: {error}") from None

    try:
        data = json.loads(body)
        answer = data["choices"][0]["message"]["content"]
    except (json.JSONDecodeError, KeyError, IndexError, TypeError):
        raise ApiError("API вернул неожиданный ответ без текста") from None

    if not isinstance(answer, str) or not answer.strip():
        raise ApiError("API вернул пустой ответ")
    return answer.strip()


def main() -> int:
    parser = argparse.ArgumentParser(description="Отправить вопрос в DeepSeek API")
    parser.add_argument("prompt", nargs="*", help="текст вопроса")
    args = parser.parse_args()

    prompt = " ".join(args.prompt).strip()
    if not prompt:
        try:
            prompt = input("Вопрос: ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nВвод отменён", file=sys.stderr)
            return 1
    if not prompt:
        parser.error("вопрос не должен быть пустым")

    api_key = os.environ.get("DEEPSEEK_API_KEY", "").strip()
    if not api_key:
        print(
            "Ошибка: задайте API key в переменной окружения DEEPSEEK_API_KEY",
            file=sys.stderr,
        )
        return 1

    try:
        answer = request_answer(prompt, api_key)
    except ApiError as error:
        print(f"Ошибка: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print("\nЗапрос отменён", file=sys.stderr)
        return 1

    print(answer)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
