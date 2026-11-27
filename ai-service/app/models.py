from typing import List, Optional

from pydantic import BaseModel, Field


class SuspectCode(BaseModel):
    filePath: str
    className: str
    methodName: Optional[str] = None
    startLine: int = 0
    endLine: int = 0
    score: float = 0.0
    confidence: str = "LOW"
    reasons: List[str] = Field(default_factory=list)
    sourceSnippet: str = ""


class DiagnosisRequest(BaseModel):
    testClass: str
    testMethod: str
    exceptionType: str = ""
    exceptionMessage: str = ""
    stackTrace: List[str] = Field(default_factory=list)
    suspects: List[SuspectCode] = Field(default_factory=list)


class DiagnosisResponse(BaseModel):
    source: str  # "rules" or "llm"
    category: str
    rootCause: str
    explanation: str
    suggestedFix: str
    confidence: str  # HIGH / MEDIUM / LOW
    suspectFile: Optional[str] = None
    suspectMethod: Optional[str] = None
    notes: List[str] = Field(default_factory=list)