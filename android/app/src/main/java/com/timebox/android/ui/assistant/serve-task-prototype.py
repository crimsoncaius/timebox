"""Optional loopback preview. Only this generated prototype is exposed."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

PROTOTYPE = Path(__file__).with_name("TaskAccess.prototype.html")
PORT = 12082  # Reserved for this worktree's assistant-task-304-prototype.


class Preview(BaseHTTPRequestHandler):
    def do_GET(self):
        if urlsplit(self.path).path not in ("/", "/TaskAccess.prototype.html"):
            self.send_error(404)
            return
        body = PROTOTYPE.read_bytes()
        self.send_response(200)
        self.send_header("Content-Type", "text/html; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; font-src data:; connect-src 'none'; frame-ancestors 'none'")
        self.end_headers()
        self.wfile.write(body)


if __name__ == "__main__":
    print(f"Prototype: http://127.0.0.1:{PORT}", flush=True)
    ThreadingHTTPServer(("127.0.0.1", PORT), Preview).serve_forever()
