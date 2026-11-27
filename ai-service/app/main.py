import os

from fastapi import FastAPI

from .llm import LlmError, diagnose_with_llm, llm_available
from .models import DiagnosisRequest, DiagnosisResponse
from .rules import diagnose as diagnose_with_rules

app = FastAPI(title="AutoTestFix AI Service", version="0.1.0")


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "AutoTestFix AI Service",
        "llmConfigured": llm_available(),
        "mode": os.getenv("AI_MODE", "rules"),
    }


@app.post("/diagnose", response_model=DiagnosisResponse)
def diagnose(req: DiagnosisRequest) -> DiagnosisResponse:
    """AI_MODE=rules (default) -> offline. AI_MODE=llm -> LLM, with fallback to rules."""
    if os.getenv("AI_MODE", "rules").lower() == "llm":
        try:
            return diagnose_with_llm(req)
        except LlmError as e:
            result = diagnose_with_rules(req)
            result.notes.append(f"LLM mode was requested but failed, so rules were used instead: {e}")
            return result
    return diagnose_with_rules(req)