from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)

BUGGY_ADD = {
    "testClass": "demo.CalculatorTest",
    "testMethod": "addsTwoNumbers",
    "exceptionType": "org.opentest4j.AssertionFailedError",
    "exceptionMessage": "expected: <5> but was: <-1>",
    "stackTrace": ["org.opentest4j.AssertionFailedError: expected: <5> but was: <-1>"],
    "suspects": [{
        "filePath": "src/main/java/demo/Calculator.java",
        "className": "demo.Calculator",
        "methodName": "add",
        "startLine": 5,
        "endLine": 7,
        "score": 0.65,
        "confidence": "MEDIUM",
        "reasons": ["Called by the failing test: Calculator.add() (test line 13)"],
        "sourceSnippet": "    public int add(int a, int b) {\n        return a - b;\n    }\n",
    }],
}


def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "UP"


def test_wrong_operator_is_diagnosed():
    r = client.post("/diagnose", json=BUGGY_ADD)
    assert r.status_code == 200
    body = r.json()
    assert body["source"] == "rules"
    assert body["category"] == "WRONG_OPERATOR"
    assert body["suspectMethod"] == "add"
    assert "'+'" in body["suggestedFix"]


def test_null_pointer_is_diagnosed():
    req = dict(BUGGY_ADD, exceptionType="java.lang.NullPointerException", exceptionMessage="")
    r = client.post("/diagnose", json=req)
    assert r.json()["category"] == "NULL_POINTER"


def test_unknown_exception_falls_back():
    req = dict(BUGGY_ADD, exceptionType="java.lang.OutOfMemoryError", exceptionMessage="heap")
    r = client.post("/diagnose", json=req)
    assert r.json()["category"] == "UNKNOWN"
    assert r.json()["confidence"] == "LOW"


def test_missing_suspects_does_not_crash():
    req = dict(BUGGY_ADD, suspects=[])
    r = client.post("/diagnose", json=req)
    assert r.status_code == 200
    assert r.json()["suspectFile"] is None


def test_invalid_request_is_rejected():
    r = client.post("/diagnose", json={"testMethod": "x"})
    assert r.status_code == 422