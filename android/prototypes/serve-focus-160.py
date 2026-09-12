# THROWAWAY. Run: python android/prototypes/serve-focus-160.py
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from pathlib import Path

root = Path(__file__).resolve().parents[1]
print('http://127.0.0.1:12008/prototypes/focus-160.html?variant=A', flush=True)
ThreadingHTTPServer(('127.0.0.1', 12008), partial(SimpleHTTPRequestHandler, directory=str(root))).serve_forever()
