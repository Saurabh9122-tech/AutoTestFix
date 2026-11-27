"""Offline rule-based diagnosis.

This is a small pattern matcher, NOT a real AI model. It recognises a few
common bug shapes and otherwise reports that it cannot tell.
"""
import re
from typing import List, Optional, Tuple

from .models import DiagnosisRequest, DiagnosisResponse, SuspectCode

_EXPECTED_ACTUAL = re.compile(r"expected:\s*<(.+?)>\s*but was:\s*<(.+?)>", re.DOTALL)
_RETURN_STMT = re.compile(r"return\s+([^;]+);")
_BINARY = re.compile(r"^\s*(\w+)\s*([+\-*/%])\s*(\w+)\s*$")

_OPPOSITE = {"+": "-", "-": "+", "*": "/", "/": "*"}
_NAMES = {"+": "addition", "-": "subtraction", "*": "multiplication",
          "/": "division", "%": "modulo"}


def _top_suspect(req: DiagnosisRequest) -> Optional[SuspectCode]:
    if not req.suspects:
        return None
    return sorted(req.suspects, key=lambda s: s.score, reverse=True)[0]


def _to_int(text: str) -> Optional[int]:
    try:
        return int(text.strip())
    except ValueError:
        return None


def _wrong_operator(req: DiagnosisRequest, suspect: SuspectCode) -> Optional[Tuple[str, str]]:
    """If the code returns 'a OP b' and the opposite operator would make the
    assertion's expected/actual values consistent, return (current_op, better_op)."""
    m = _EXPECTED_ACTUAL.search(req.exceptionMessage or "")
    if not m:
        return None
    expected, actual = _to_int(m.group(1)), _to_int(m.group(2))
    if expected is None or actual is None:
        return None

    ret = _RETURN_STMT.search(suspect.sourceSnippet or "")
    if not ret:
        return None
    expr = _BINARY.match(ret.group(1).split("//")[0])
    if not expr:
        return None
    current = expr.group(2)
    better = _OPPOSITE.get(current)
    if better is None:
        return None

    # Try to recover the inputs from the numbers: for the classic case
    # a=2,b=3 -> expected 5 (a+b) but was -1 (a-b), we cannot know a and b,
    # so we only claim a pattern match, not a proof.
    return current, better


def diagnose(req: DiagnosisRequest) -> DiagnosisResponse:
    suspect = _top_suspect(req)
    exc = req.exceptionType or ""
    msg = req.exceptionMessage or ""
    notes: List[str] = [
        "Produced by the offline rule-based analyzer (a limited pattern matcher, not an AI model)."
    ]

    file_ = suspect.filePath if suspect else None
    method = suspect.methodName if suspect else None
    where = f"{suspect.className}.{method}()" if suspect and method else (
        suspect.className if suspect else "the code under test")

    # Pattern 1: NullPointerException
    if "NullPointerException" in exc:
        return DiagnosisResponse(
            source="rules", category="NULL_POINTER",
            rootCause=f"Something was null when {where} used it.",
            explanation=("A NullPointerException means the code tried to use a value "
                         "that was never set. Look at which variable on the failing line "
                         "can be null."),
            suggestedFix="Check the value for null before using it, or make sure it is always initialised.",
            confidence="MEDIUM" if suspect else "LOW",
            suspectFile=file_, suspectMethod=method, notes=notes)

    # Pattern 2: wrong arithmetic operator
    if suspect and "AssertionFailedError" in exc:
        op = _wrong_operator(req, suspect)
        if op:
            current, better = op
            return DiagnosisResponse(
                source="rules", category="WRONG_OPERATOR",
                rootCause=(f"{where} uses {_NAMES.get(current, current)} ('{current}') "
                           f"but the test expects a different result ({msg})."),
                explanation=(f"The test expected one value and got another. The method returns "
                             f"an expression using '{current}'. That operator is a likely cause, "
                             f"so '{better}' is worth trying."),
                suggestedFix=f"Try replacing '{current}' with '{better}' in {where}, then re-run the tests.",
                confidence="MEDIUM",
                suspectFile=file_, suspectMethod=method,
                notes=notes + ["This is a pattern match. It is verified only if the tests pass after the fix."])

    # Pattern 3: other assertion failure
    if "AssertionFailedError" in exc:
        return DiagnosisResponse(
            source="rules", category="ASSERTION_MISMATCH",
            rootCause=f"The result of {where} does not match what the test expects ({msg}).",
            explanation=("The test compared an expected value with the real one and they "
                         "differ. The logic in the suspect method is the first place to look."),
            suggestedFix="Compare the method's logic with what the test expects and correct the difference.",
            confidence="LOW",
            suspectFile=file_, suspectMethod=method, notes=notes)

    # Fallback
    return DiagnosisResponse(
        source="rules", category="UNKNOWN",
        rootCause="The offline analyzer could not identify the cause.",
        explanation=f"Exception: {exc or 'unknown'}. {msg}".strip(),
        suggestedFix="Read the stack trace and the suspect code manually, or enable LLM mode.",
        confidence="LOW", suspectFile=file_, suspectMethod=method, notes=notes)