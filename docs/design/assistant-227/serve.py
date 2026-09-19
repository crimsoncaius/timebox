"""Throwaway Assistant design review: python docs/design/assistant-227/serve.py"""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from pathlib import Path

print('Assistant prototype: http://127.0.0.1:12033/?variant=A', flush=True)
ThreadingHTTPServer(('127.0.0.1', 12033), partial(SimpleHTTPRequestHandler, directory=str(Path(__file__).parent))).serve_forever()
