"""List the Gemini models this key can use for generateContent. The key is never printed."""
import base64, json, re, urllib.request

src = open("android/app/src/main/java/com/forge/app/util/Gemini.kt").read()
key = base64.b64decode(re.search(r'DEFAULT_KEY_ENCODED = "([^"]+)"', src).group(1)).decode().strip()
req = urllib.request.Request("https://generativelanguage.googleapis.com/v1beta/models?pageSize=200",
                             headers={"x-goog-api-key": key})
try:
    with urllib.request.urlopen(req, timeout=40) as r:
        models = json.loads(r.read().decode()).get("models", [])
    for m in models:
        if "generateContent" in m.get("supportedGenerationMethods", []):
            print("MODEL", m["name"].replace("models/", ""))
except Exception as e:
    print("PROBE failed:", type(e).__name__, str(e)[:200].replace(key, "<key>"))
