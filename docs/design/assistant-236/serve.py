"""THROWAWAY local review server; run python docs/design/assistant-236/serve.py."""
from http.server import ThreadingHTTPServer, SimpleHTTPRequestHandler
from functools import partial
from pathlib import Path

if __name__ == '__main__':
    handler = partial(SimpleHTTPRequestHandler, directory=str(Path(__file__).parent))
    print('Assistant review: http://127.0.0.1:12044/?variant=A', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 12044), handler).serve_forever()
