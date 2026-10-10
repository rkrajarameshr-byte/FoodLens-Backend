import io
import json

import httpx
import pytest
from fastapi.testclient import TestClient
from PIL import Image

from app.main import app, analyze_with_openai


def jpeg_bytes(color="orange"):
    buffer = io.BytesIO()
    Image.new("RGB", (40, 40), color).save(buffer, format="JPEG")
    return buffer.getvalue()


def test_demo_requires_real_image(monkeypatch):
    monkeypatch.setenv("FOOD_VISION_MODE", "demo")
    client = TestClient(app)
    bad = client.post("/analyze", files={"file": ("fake.jpg", b"not an image", "image/jpeg")})
    assert bad.status_code == 400
    good = client.post("/analyze", files={"file": ("test.jpg", jpeg_bytes(), "image/jpeg")})
    assert good.status_code == 200
    assert good.json()["provider"] == "offline-demo"
    assert "DEMO ONLY" in good.json()["note"]


def test_missing_file_and_large_file():
    client = TestClient(app)
    assert client.post("/analyze").status_code == 422
    assert client.post("/analyze", files={"file": ("big.jpg", b"x" * (10 * 1024 * 1024 + 1), "image/jpeg")}).status_code == 413


def test_openai_without_key_does_not_fall_back(monkeypatch):
    monkeypatch.setenv("FOOD_VISION_MODE", "openai")
    monkeypatch.delenv("OPENAI_API_KEY", raising=False)
    client = TestClient(app)
    response = client.post("/analyze", files={"file": ("test.jpg", jpeg_bytes(), "image/jpeg")})
    assert response.status_code == 503
    assert "API_KEY" in response.json()["detail"]


@pytest.mark.asyncio
async def test_real_provider_sends_actual_image(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-not-a-real-key")
    blob = jpeg_bytes()
    seen = {}

    def responder(request):
        body = json.loads(request.content)
        seen["image_url"] = body["input"][0]["content"][1]["image_url"]
        return httpx.Response(200, json={"output": [{"content": [{"type": "output_text", "text": json.dumps({
            "foods": [{"name": "Banana", "confidence": .87, "estimatedGrams": 110}],
            "note": "One banana visible."
        })}]}]})

    async with httpx.AsyncClient(transport=httpx.MockTransport(responder)) as client:
        result = await analyze_with_openai(blob, "image/jpeg", client=client)
    assert seen["image_url"].startswith("data:image/jpeg;base64,")
    assert result.foods[0].name == "Banana"
    assert result.provider.startswith("openai:")


@pytest.mark.asyncio
async def test_real_provider_errors_not_demo(monkeypatch):
    monkeypatch.setenv("OPENAI_API_KEY", "test-not-a-real-key")
    async with httpx.AsyncClient(transport=httpx.MockTransport(lambda r: httpx.Response(429))) as client:
        with pytest.raises(Exception) as e:
            await analyze_with_openai(jpeg_bytes(), "image/jpeg", client=client)
    assert e.value.status_code == 503


def test_health_reports_provider(monkeypatch):
    monkeypatch.setenv('FOOD_VISION_MODE', 'openai')
    monkeypatch.delenv('OPENAI_API_KEY', raising=False)
    response = TestClient(app).get('/health')
    assert response.json()['mode'] == 'openai'
    assert response.json()['configured'] is False


@pytest.mark.asyncio
async def test_nonfood_response_can_be_empty(monkeypatch):
    monkeypatch.setenv('OPENAI_API_KEY', 'test-not-a-real-key')
    def responder(request):
        return httpx.Response(200, json={'output': [{'content': [{'type': 'output_text', 'text': '{"foods":[],"note":"No food is visible."}'}]}]})
    async with httpx.AsyncClient(transport=httpx.MockTransport(responder)) as client:
        result = await analyze_with_openai(jpeg_bytes(), 'image/jpeg', client=client)
    assert result.foods == []
    assert 'No food' in result.note


def test_unsupported_format():
    buf = io.BytesIO()
    Image.new('RGB', (10, 10), 'red').save(buf, format='GIF')
    result = TestClient(app).post('/analyze', files={'file': ('a.gif', buf.getvalue(), 'image/gif')})
    assert result.status_code == 415
