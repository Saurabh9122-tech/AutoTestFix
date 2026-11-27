"""Optional LLM diagnosis through the Anthropic Messages API.

Enabled only when ANTHROPIC_API_KEY is set. Any failure raises LlmError so the
caller can fall back to the offline rules.
"""
import json
import os
import re

import httpx

from .models import DiagnosisRequest, DiagnosisResponse

API_URL = "https://api.anthropic.com/v1/messages"
DEFAULT_MODEL = "claude-sonnet-4-6"  # override with the ANTHROPIC_MODEL environment variable


class LlmError(Exception):
    pass


def llm_available() -> bool:
    return bool(os.getenv("ANTHROPIC_API_KEY"))


def _build_prompt(req: DiagnosisRequest) -> str:
    suspects = "\n\n".join(
        f"File: {s.filePath} ({s.className}.{s.methodName}, lines {s.startLine}-{s.endLine})\n"
        f"Reasons: {'; '.join(s.reasons)}\n```java\n{s.sourceSnippet}\n```"
        for s in req.suspects[:3]
    ) or "(no suspect code was found)"
    trace = "\n".join(req.stackTrace[:20])
    return (
        "You are helping a student debug a Java project. A unit test failed.\n"
        "Explain the root cause in simple language and suggest a fix idea. "
        "Do not write a full patch.\n\n"
        f"Failed test: {req.testClass}.{req.testMethod}\n"
        f"Exception: {req.exceptionType}\nMessage: {req.exceptionMessage}\n"
        f"Stack trace:\n{trace}\n\nSuspect code:\n{suspects}\n\n"
        "Reply with ONLY a JSON object, no markdown, with these keys: "
        '"category" (short UPPER_SNAKE_CASE label), "rootCause", "explanation", '
        '"suggestedFix", "confidence" (HIGH, MEDIUM or LOW).'
    )


def diagnose_with_llm(req: DiagnosisRequest) -> DiagnosisResponse:
    key = os.getenv("ANTHROPIC_API_KEY")
    if not key:
        raise LlmError("ANTHROPIC_API_KEY is not set")

    try:
        resp = httpx.post(
            API_URL,
            headers={
                "x-api-key": key,
                "anthropic-version": "2023-06-01",
                "content-type": "application/json",
            },
            json={
                "model": os.getenv("ANTHROPIC_MODEL", DEFAULT_MODEL),
                "max_tokens": 1000,
                "messages": [{"role": "user", "content": _build_prompt(req)}],
            },
            timeout=60,
        )
        resp.raise_for_status()
        text = "".join(b.get("text", "") for b in resp.json().get("content", []))
        text = re.sub(r"^```(?:json)?|```$", "", text.strip(), flags=re.MULTILINE).strip()
        data = json.loads(text)
    except (httpx.HTTPError, ValueError, KeyError) as e:
        raise LlmError(f"LLM call failed: {e}") from e

    confidence = str(data.get("confidence", "LOW")).upper()
    if confidence not in ("HIGH", "MEDIUM", "LOW"):
        confidence = "LOW"
    top = sorted(req.suspects, key=lambda s: s.score, reverse=True)[0] if req.suspects else None
    return DiagnosisResponse(
        source="llm",
        category=str(data.get("category", "UNKNOWN")),
        rootCause=str(data.get("rootCause", "")),
        explanation=str(data.get("explanation", "")),
        suggestedFix=str(data.get("suggestedFix", "")),
        confidence=confidence,
        suspectFile=top.filePath if top else None,
        suspectMethod=top.methodName if top else None,
        notes=["Produced by an LLM. Treat it as a suggestion until tests verify a fix."],
    )