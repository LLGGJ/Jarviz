#!/usr/bin/env python3
"""
Jarvis PC Agent: deixa o Jarvis (no celular) usar este computador pela sua rede.
Só usa a biblioteca padrão do Python. Teclado e mouse são opcionais: pip install pyautogui

Uso:   python jarvis_pc_agent.py            (escuta na rede local, porta 8765)
       python jarvis_pc_agent.py --host 127.0.0.1 --port 9000

No Jarvis: Configurações > Casa e PC > URL do agente (http://IP-DO-PC:8765) e o token mostrado aqui.
SEGURANÇA: quem tem o token controla este PC. Use só na sua rede (ou por VPN, como Tailscale), nunca exponha a porta na internet.
Cada comando fica registrado em ~/.jarvis_pc_agent.log.
"""
import argparse, hmac, json, os, platform, secrets, socket, subprocess, sys, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HOME = os.path.expanduser("~")
TOKEN_FILE = os.path.join(HOME, ".jarvis_pc_token")
LOG_FILE = os.path.join(HOME, ".jarvis_pc_agent.log")


def load_token():
    t = os.environ.get("JARVIS_TOKEN")
    if t:
        return t
    if os.path.exists(TOKEN_FILE):
        return open(TOKEN_FILE).read().strip()
    t = secrets.token_urlsafe(24)
    with open(TOKEN_FILE, "w") as f:
        f.write(t)
    try:
        os.chmod(TOKEN_FILE, 0o600)
    except OSError:
        pass
    return t


TOKEN = load_token()


def log(line):
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(time.strftime("%Y-%m-%d %H:%M:%S ") + line + "\n")


def gui():
    try:
        import pyautogui
        pyautogui.FAILSAFE = True
        return pyautogui
    except Exception:
        return None


def do_info(_):
    return f"{socket.gethostname()} | {platform.system()} {platform.release()} | Python {platform.python_version()} | usuário {os.environ.get('USERNAME') or os.environ.get('USER')}"


def do_run(b):
    cmd = b.get("cmd", "")
    timeout = int(b.get("timeout_s", 60))
    log("RUN " + cmd)
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    out = (r.stdout or "") + (("\n[stderr]\n" + r.stderr) if r.stderr else "")
    return out[-8000:] + f"\n[saída {r.returncode}]"


def do_open(b):
    target = b.get("target", "")
    log("OPEN " + target)
    if sys.platform.startswith("win"):
        os.startfile(target)
    elif sys.platform == "darwin":
        subprocess.Popen(["open", target])
    else:
        subprocess.Popen(["xdg-open", target])
    return "Aberto: " + target


def need_gui():
    g = gui()
    if g is None:
        raise RuntimeError("Instale pyautogui no PC: pip install pyautogui")
    return g


def do_type(b):
    log("TYPE")
    need_gui().write(b.get("text", ""), interval=0.01)
    return "Digitado."


def do_key(b):
    keys = [k.strip() for k in b.get("keys", "").lower().replace(" ", "").split("+") if k.strip()]
    log("KEY " + "+".join(keys))
    g = need_gui()
    g.hotkey(*keys) if len(keys) > 1 else g.press(keys[0])
    return "Tecla: " + "+".join(keys)


def do_click(b):
    x, y = int(b.get("x", 0)), int(b.get("y", 0))
    log(f"CLICK {x},{y}")
    need_gui().click(x, y)
    return f"Cliquei em {x},{y}."


ROUTES = {"/info": do_info, "/run": do_run, "/open": do_open, "/type": do_type, "/key": do_key, "/click": do_click}


class Handler(BaseHTTPRequestHandler):
    def _send(self, code, obj):
        data = json.dumps(obj).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_POST(self):
        if not hmac.compare_digest(self.headers.get("X-Token", ""), TOKEN):
            return self._send(401, {"ok": False, "error": "token inválido"})
        fn = ROUTES.get(self.path)
        if fn is None:
            return self._send(404, {"ok": False, "error": "rota desconhecida"})
        try:
            n = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(n) or b"{}")
            self._send(200, {"ok": True, "out": fn(body)})
        except subprocess.TimeoutExpired:
            self._send(200, {"ok": False, "error": "o comando passou do tempo limite"})
        except Exception as e:
            self._send(200, {"ok": False, "error": str(e)})

    def log_message(self, *a):
        pass


def local_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int, default=8765)
    a = ap.parse_args()
    print(f"Jarvis PC Agent rodando.\n  URL no Jarvis:   http://{local_ip()}:{a.port}\n  Token no Jarvis: {TOKEN}\n  Registro:        {LOG_FILE}\n(Ctrl+C para parar)")
    ThreadingHTTPServer((a.host, a.port), Handler).serve_forever()
